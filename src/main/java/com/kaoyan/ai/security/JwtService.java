package com.kaoyan.ai.security;

import com.kaoyan.ai.common.ApiException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/** JWT 只经 HttpOnly Cookie 交给浏览器；数据库保存 jti，支持立即撤销。 */
@Service
public class JwtService {
  public static final String COOKIE = "AUTH_TOKEN";
  private static final String ISSUER = "university-ai-user";
  private final NimbusJwtEncoder encoder;
  private final NimbusJwtDecoder decoder;
  private final JdbcTemplate jdbc;
  private final boolean secure;
  private final long ttl;

  public JwtService(JdbcTemplate jdbc, Environment environment,
      @Value("${app.jwt.secret:}") String secret,
      @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
      @Value("${app.jwt.ttl-seconds:1800}") long ttl) {
    this.jdbc = jdbc;
    this.secure = secure;
    this.ttl = ttl;
    if (ttl < 60 || ttl > 86400) throw new IllegalStateException("JWT 有效期应为 60～86400 秒");
    byte[] key;
    if (secret.isBlank()) {
      if (environment.matchesProfiles("prod", "dev", "mysql"))
        throw new IllegalStateException("MySQL/生产环境必须设置 APP_JWT_SECRET（至少32字节的Base64随机密钥）");
      key = new byte[32];
      new SecureRandom().nextBytes(key);
    } else {
      try { key = Base64.getDecoder().decode(secret); }
      catch (IllegalArgumentException e) { throw new IllegalStateException("APP_JWT_SECRET 必须为 Base64", e); }
      if (key.length < 32) throw new IllegalStateException("JWT 密钥不得少于32字节");
    }
    var signingKey = new SecretKeySpec(key, "HmacSHA256");
    encoder = new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
    decoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
  }

  public Optional<String> token(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null) {
      if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) throw invalid();
      return Optional.of(authorization.substring(7));
    }
    if (request.getCookies() != null)
      for (Cookie cookie : request.getCookies())
        if (COOKIE.equals(cookie.getName())) return Optional.of(cookie.getValue());
    return Optional.empty();
  }

  public Jwt verify(String token) {
    try {
      Jwt jwt = decoder.decode(token);
      Instant now = Instant.now();
      if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(now)
          || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now.plusSeconds(30))
          || jwt.getId() == null || jwt.getSubject() == null
          || !jwt.getAudience().contains("university-ai-web")) throw invalid();
      Integer count = jdbc.queryForObject(
          "SELECT COUNT(*) FROM auth_tokens WHERE token_id=? AND user_id=? AND expires_at>?",
          Integer.class, jwt.getId(), jwt.getSubject(), Timestamp.from(now));
      if (count == null || count != 1) throw invalid();
      return jwt;
    } catch (JwtException | IllegalArgumentException e) { throw invalid(); }
  }

  public void issue(String user, HttpServletResponse response) {
    Instant now = Instant.now(), expires = now.plusSeconds(ttl);
    String jti = UUID.randomUUID().toString();
    JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject(user)
        .audience(List.of("university-ai-web")).issuedAt(now).expiresAt(expires).id(jti).build();
    String token = encoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    jdbc.update("DELETE FROM auth_tokens WHERE expires_at<=?", Timestamp.from(now));
    jdbc.update("INSERT INTO auth_tokens(token_id,user_id,expires_at) VALUES (?,?,?)",
        jti, user, Timestamp.from(expires));
    response.addHeader("Set-Cookie", cookie(token, ttl));
  }

  public void revoke(HttpServletRequest request, HttpServletResponse response) {
    revokeActive(request);
    response.addHeader("Set-Cookie", cookie("", 0));
  }

  public void revokeActive(HttpServletRequest request) {
    token(request).ifPresent(value -> {
      Jwt jwt = verify(value);
      jdbc.update("DELETE FROM auth_tokens WHERE token_id=? AND user_id=?", jwt.getId(), jwt.getSubject());
    });
  }

  private String cookie(String value, long age) {
    return ResponseCookie.from(COOKIE, value).path("/api/v1").httpOnly(true)
        .secure(secure).sameSite("Lax").maxAge(Duration.ofSeconds(age)).build().toString();
  }

  private ApiException invalid() { return new ApiException(401, "UNAUTHENTICATED", "登录已过期或凭证无效，请重新登录"); }
}
