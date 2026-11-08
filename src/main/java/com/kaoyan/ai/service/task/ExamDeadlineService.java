package com.kaoyan.ai.service.task;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.service.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 截止时间由服务端执行，关闭浏览器也不会延长考试。 */
@Component
public class ExamDeadlineService {
  private final RecordRepository db;
  private final DomainDispatcher domain;
  private final TransactionTemplate tx;

  public ExamDeadlineService(
      RecordRepository db, DomainDispatcher domain, PlatformTransactionManager manager) {
    this.db = db;
    this.domain = domain;
    this.tx = new TransactionTemplate(manager);
  }

  @Scheduled(fixedDelay = 5000)
  public void closeExpired() {
    var rows =
        db.scan("exam_sessions", "user", "session");
    for (var row : rows) {
      var s = row.path("session");
      if (!s.path("status").asText().equals("in_progress")
          || Instant.parse(s.path("deadlineAt").asText()).isAfter(Instant.now())) continue;
      tx.executeWithoutResult(
          status -> {
            String user = row.path("user").asText(), sid = s.path("id").asText();
            db.lockOwner(user);
            ObjectNode current = db.get("exam_sessions", sid, user);
            if (current.path("status").asText().equals("in_progress"))
              domain.execute(
                  "op061",
                  new DomainContext(
                      user, Map.of("id", sid), obj(), obj("version", current.get("version"))));
          });
    }
  }
}
