package com.kaoyan.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class KaoyanAiApplication {
  public static void main(String[] args) {
    SpringApplication.run(KaoyanAiApplication.class, args);
  }
}
