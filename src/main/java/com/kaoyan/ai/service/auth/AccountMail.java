package com.kaoyan.ai.service.auth;

public interface AccountMail {
  void send(String address, String subject, String body);
}
