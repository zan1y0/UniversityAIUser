package com.kaoyan.ai.service.task;

import com.kaoyan.ai.persistence.repository.RecordRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ReminderService {
  private final RecordRepository db;
  private final StudySupervisionService supervision;
  private final TransactionTemplate tx;

  public ReminderService(
      RecordRepository db, StudySupervisionService supervision, PlatformTransactionManager manager) {
    this.db = db;
    this.supervision = supervision;
    this.tx = new TransactionTemplate(manager);
  }

  @Scheduled(fixedDelay = 60000)
  public void remind() {
    for (var row : db.scan("plans", "user", "plan")) {
      var plan = row.path("plan");
      if (!plan.path("status").asText().equals("active")) continue;
      String user = row.path("user").asText();
      String planId = plan.path("id").asText();
      tx.executeWithoutResult(status -> {
        db.lockOwner(user);
        supervision.scan(user, db.get("plans", planId, user), Instant.now());
      });
    }
  }
}
