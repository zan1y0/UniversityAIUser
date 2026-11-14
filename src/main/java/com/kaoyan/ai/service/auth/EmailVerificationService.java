package com.kaoyan.ai.service.auth;

import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.mapper.EmailChallengeMapper;
import com.kaoyan.ai.persistence.mapper.IdentityMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmailVerificationService {
  public enum Purpose { REGISTER, RESET, ACTIVATE, BIND, CHANGE }
  public static final class InvalidCode extends ApiException {
    InvalidCode() { super(422, "INVALID_CODE", "验证码无效、已过期或尝试次数过多"); }
  }

  private final IdentityMapper identities;
  private final EmailChallengeMapper challenges;
  private final AccountMail mail;
  private final TransactionTemplate transaction;
  private final SecureRandom random = new SecureRandom();
  private final byte[] secret;

  EmailVerificationService(IdentityMapper identities, EmailChallengeMapper challenges, AccountMail mail,
      PlatformTransactionManager manager, Environment environment,
      @Value("${app.account-mail.code-secret:}") String configuredSecret) {
    this.identities = identities;
    this.challenges = challenges;
    this.mail = mail;
    this.transaction = new TransactionTemplate(manager);
    if (configuredSecret.isBlank()) {
      if (environment.matchesProfiles("prod")) throw new IllegalStateException("生产环境需配置邮箱验证密钥");
      secret = new byte[32];
      random.nextBytes(secret);
    } else {
      secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
      if (secret.length < 32) throw new IllegalStateException("邮箱验证密钥至少32字节");
    }
  }

  public void reserve(String email, String source) {
    long now = Instant.now().getEpochSecond();
    transaction.executeWithoutResult(status -> {
      identities.lock(2);
      String recipient = "email:" + digest(email);
      String ip = "ip:" + digest(source);
      bucket(recipient, 60, 1, now);
      // The second window enforces a rolling 60-second gap across minute boundaries.
      Integer last = challenges.count(recipient, 1, 0);
      int current = (int) (now - 1_700_000_000);
      if (last != null && current - last < 60) throw limited();
      if (last == null) challenges.createBucket(recipient, 1, 0);
      challenges.setCount(recipient, 1, 0, current);
      bucket(recipient, 3600, 5, now);
      bucket(recipient, 86400, 10, now);
      bucket(ip, 3600, 30, now);
      bucket(ip, 86400, 100, now);
    });
  }

  private void bucket(String key, int period, int limit, long now) {
    long start = now - now % period;
    Integer count = challenges.count(key, period, start);
    if (count != null && count >= limit) throw limited();
    if (count == null) challenges.createBucket(key, period, start);
    challenges.increment(key, period, start);
  }

  private ApiException limited() {
    return new ApiException(429, "EMAIL_RATE_LIMITED", "邮件请求过于频繁，请稍后再试");
  }

  public void send(Purpose purpose, String email, String scope) {
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    transaction.executeWithoutResult(status -> {
      identities.lock(2);
      challenges.remove(purpose.name(), email, scope);
      challenges.add(purpose.name(), email, scope, codeDigest(purpose, email, scope, code),
          Timestamp.from(Instant.now().plusSeconds(300)));
    });
    try {
      mail.send(email, "研途 AI 邮箱验证码", "验证码为 " + code + "，5 分钟内有效，请勿转发。");
    } catch (ApiException e) {
      transaction.executeWithoutResult(status -> {
        identities.lock(2);
        var challenge = challenges.find(purpose.name(), email, scope);
        if (challenge != null && challenge.digest().equals(codeDigest(purpose, email, scope, code)))
          challenges.remove(purpose.name(), email, scope);
      });
      throw e;
    }
  }

  /** Caller runs in the transaction that applies the verified operation. */
  public void reject(Purpose purpose, String email, String scope) {
    identities.lock(2);
    var challenge = challenges.find(purpose.name(), email, scope);
    if (challenge != null && challenge.failures() < 5) challenges.fail(purpose.name(), email, scope);
    throw new InvalidCode();
  }

  public void consume(Purpose purpose, String email, String scope, String code) {
    identities.lock(2);
    var challenge = challenges.find(purpose.name(), email, scope);
    if (challenge == null || challenge.failures() >= 5 || !challenge.expires().toInstant().isAfter(Instant.now()))
      throw new InvalidCode();
    if (!MessageDigest.isEqual(challenge.digest().getBytes(StandardCharsets.US_ASCII),
        codeDigest(purpose, email, scope, code).getBytes(StandardCharsets.US_ASCII))) {
      challenges.fail(purpose.name(), email, scope);
      throw new InvalidCode();
    }
    challenges.remove(purpose.name(), email, scope);
  }

  @Scheduled(fixedDelay = 3600000)
  public void cleanup() {
    Instant now = Instant.now();
    transaction.executeWithoutResult(status -> {
      identities.lock(2);
      challenges.cleanQuota(now.minusSeconds(172800).getEpochSecond());
      challenges.cleanChallenges(Timestamp.from(now));
    });
  }

  private String codeDigest(Purpose purpose, String email, String scope, String code) {
    return digest(purpose.name() + "\n" + email + "\n" + scope + "\n" + code);
  }

  private String digest(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
