package com.kaoyan.ai.infrastructure.config;

import com.kaoyan.ai.service.auth.AdminAccounts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {
  private final AdminAccounts accounts;
  private final String email;
  private final String password;
  private final String name;

  AdminInitializer(AdminAccounts accounts, @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
      @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password, @Value("${BOOTSTRAP_ADMIN_NAME:管理员}") String name) {
    this.accounts = accounts;
    this.email = email;
    this.password = password;
    this.name = name;
  }

  @Override public void run(ApplicationArguments args) {
    if (email.isBlank() && password.isBlank()) return;
    if (email.isBlank() || password.isBlank()) throw new IllegalStateException("管理员初始化配置不完整");
    accounts.bootstrap(email, password, name);
  }
}
