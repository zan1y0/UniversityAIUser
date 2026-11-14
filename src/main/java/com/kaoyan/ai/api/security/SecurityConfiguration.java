package com.kaoyan.ai.api.security;
import com.kaoyan.ai.service.auth.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean org.springframework.security.core.userdetails.UserDetailsService userDetails(
      com.kaoyan.ai.persistence.mapper.IdentityMapper accounts) {
    return login -> {
      var account = accounts.byEmail(login);
      if (account == null || !account.enabled())
        throw new org.springframework.security.core.userdetails.UsernameNotFoundException("邮箱或密码错误");
      return org.springframework.security.core.userdetails.User.withUsername(account.id())
          .password(account.passwordHash()).authorities("ACCOUNT").build();
    };
  }
  @Bean org.springframework.security.authentication.AuthenticationManager authenticationManager(
      org.springframework.security.core.userdetails.UserDetailsService userDetails) {
    var provider=new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetails);
    provider.setPasswordEncoder(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12));
    return new org.springframework.security.authentication.ProviderManager(provider);
  }
  @Bean SecurityFilterChain security(HttpSecurity http,JwtService jwt,PermissionService permissions,
      @Value("${app.cors.allowed-origins}") String origins) throws Exception {
    var cors=new CorsConfiguration();cors.setAllowedOrigins(List.of(origins.split(",")));
    cors.setAllowedMethods(List.of("GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type","Authorization","X-CSRF-Token","Idempotency-Key","Last-Event-ID"));
    cors.setAllowCredentials(true);
    var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",cors);
    http.cors(c->c.configurationSource(source))
      .csrf(c->c.csrfTokenRepository(new SessionCsrfTokenRepository())
          .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
          .requireCsrfProtectionMatcher(r->SessionFilter.path(r).startsWith("/api/v1/") && !List.of("GET","HEAD","OPTIONS").contains(r.getMethod())))
      .securityContext(c->c.securityContextRepository(new org.springframework.security.web.context.RequestAttributeSecurityContextRepository()))
      // JWT每次请求重新校验；旧SESSION仅由现有登录服务维护，避免重复认证策略清除CSRF。
      .sessionManagement(c->c.disable())
      .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
      .exceptionHandling(c->c.authenticationEntryPoint((r,w,e)->SecurityErrors.write(r,w,401,"UNAUTHENTICATED","请先登录"))
        .accessDeniedHandler((r,w,e)->SecurityErrors.write(r,w,403,e instanceof CsrfException?"CSRF_REJECTED":"FORBIDDEN",e instanceof CsrfException?"CSRF校验失败，请重新获取令牌":"当前角色没有此操作权限")))
      .authorizeHttpRequests(c->c.requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
        .requestMatchers(SessionFilter.PUBLIC.toArray(String[]::new)).permitAll()
        .requestMatchers("/api/v1/auth/me","/api/v1/auth/logout","/api/v1/auth/workspace",
          "/api/v1/auth/email/change","/api/v1/auth/email/change-code","/api/v1/session/logout").authenticated()
        .requestMatchers("/api/v1/profile").hasRole("STUDENT")
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/v1/teacher/**").hasRole("TEACHER")
        .requestMatchers("/api/v1/teaching/**").hasRole("STUDENT")
        .requestMatchers(HttpMethod.GET,"/api/v1/analytics/**").hasAuthority("student:analytics:read")
        .requestMatchers(HttpMethod.HEAD,"/api/v1/analytics/**").hasAuthority("student:analytics:read")
        .requestMatchers("/api/v1/avatar/**","/api/v1/schools/**","/api/v1/public-courses/**",
          "/api/v1/professional-courses/**","/api/v1/qa/**","/api/v1/practice/**","/api/v1/plans/**",
          "/api/v1/mock-exams/**","/api/v1/interviews/**","/api/v1/analytics/study-events","/api/v1/news/**",
          "/api/v1/study-mode","/api/v1/study-supervision/**","/api/v1/files/**","/api/v1/jobs/**",
          "/api/v1/events").access((authentication, context) ->
            new org.springframework.security.authorization.AuthorizationDecision(
                authentication.get().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"))
                && authentication.get().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("student:study:use"))))
        .requestMatchers("/api/v1/**").denyAll()
        .anyRequest().permitAll())
      .addFilterBefore(new SessionFilter(jwt,permissions),CsrfFilter.class);
    return http.build();
  }
}
