package com.kaoyan.ai.infrastructure.config;

import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.service.auth.AccountMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class SmtpAccountMail implements AccountMail {
  private static final Logger log = LoggerFactory.getLogger(SmtpAccountMail.class);

  private final ObjectProvider<JavaMailSender> senders;
  private final String from;

  @Bean("resetMailExecutor")
  static ThreadPoolTaskExecutor resetMailExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("password-reset-mail-");
    return executor;
  }

  SmtpAccountMail(ObjectProvider<JavaMailSender> senders, @Value("${app.account-mail.from:}") String from,
      @Value("${spring.mail.host:}") String host, @Value("${spring.mail.port:587}") int port,
      @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") boolean ssl,
      @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") boolean starttls) {
    this.senders = senders;
    this.from = from;
    // 启动时就把「有没有配好邮件通道」讲清楚：否则你只能看到一个 503 和一句通用文案。
    if (senders.getIfAvailable() == null || from.isBlank()) {
      log.warn("邮件通道未配置：spring.mail.host={}，app.account-mail.from={}。"
          + "注册、找回密码、邀请等发信接口会统一返回 503 MAIL_UNAVAILABLE；"
          + "请在 .local-data/mail-local.properties（或环境变量）补齐 APP_SMTP_* 与 APP_MAIL_FROM 后重启后端。",
          host.isBlank() ? "(空)" : host, from.isBlank() ? "(空)" : from);
    } else {
      log.info("邮件通道已就绪：{}:{}，发件人 {}，加密方式 {}", host, port, from,
          ssl ? "隐式 TLS(465)" : (starttls ? "STARTTLS(587)" : "无加密"));
      if (ssl && starttls) {
        log.error("spring.mail 配置冲突：mail.smtp.ssl.enable 与 mail.smtp.starttls.enable 同时为 true。"
            + "用 465 端口时应设 APP_SMTP_SSL=true、APP_SMTP_STARTTLS=false，否则发信必然失败。");
      }
    }
  }

  @Override
  public void send(String address, String subject, String body) {
    JavaMailSender sender = senders.getIfAvailable();
    if (sender == null || from.isBlank()) throw unavailable();
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(address);
    message.setSubject(subject);
    message.setText(body);
    try {
      sender.send(message);
    } catch (MailException e) {
      // 必须留痕：不打日志的话，认证失败、超时、DNS 错误在日志里长得一模一样，排查只能靠猜。
      log.warn("发送邮件失败：from={} to={} subject={}，原因：{}", from, address, subject, e.getMessage(), e);
      throw unavailable();
    }
  }

  private ApiException unavailable() {
    return new ApiException(503, "MAIL_UNAVAILABLE", "邮件暂时无法发送，请稍后重试");
  }
}
