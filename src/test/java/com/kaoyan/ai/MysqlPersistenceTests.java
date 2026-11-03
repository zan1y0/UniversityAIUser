package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;

import com.kaoyan.ai.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 显式指定临时库才执行，默认构建绝不向用户业务库写测试记录。 */
@EnabledIfSystemProperty(named = "mysql.verify", matches = "true")
@ActiveProfiles("mysql")
@SpringBootTest(
    properties = {
      "spring.datasource.url=${mysql.verify.url}",
      "spring.flyway.baseline-on-migrate=true",
      "spring.flyway.baseline-version=1",
      "app.seed-local=false"
    })
class MysqlPersistenceTests {
  @Autowired UserAccountRepository accounts;
  @Autowired RecordRepository records;
  @Autowired IdempotencyRepository idempotency;
  @Autowired EventOutboxRepository outbox;

  @Test
  @Transactional
  void generatedSchemaSupportsActualMysqlRepositoryRoundTrip() {
    assertEquals("student", accounts.findByLoginName("student").orElseThrow().loginName());
    String user = id();
    accounts.create(
        new UserAccountRepository.Account(
            user, "verify_" + user, "MySQL往返验证", "not-a-login-secret"));
    String resource = id();
    var row = obj("id", resource, "version", 1, "content", "数据库初值");
    records.create("plans", resource, user, row);
    assertEquals("数据库初值", records.get("plans", resource, user).path("content").asText());
    row.put("version", 2);
    row.put("content", "持久化更新");
    records.update("plans", resource, user, row, 1);
    assertEquals("持久化更新", records.get("plans", resource, user).path("content").asText());
    String key = id();
    idempotency.save(user, "op015", key, "c".repeat(64), 201, "{\"result\":\"original\"}");
    assertEquals(201, idempotency.find(user, "op015", key).orElseThrow().statusCode());
    long event = outbox.reserve(user, "job.failed");
    outbox.complete(event, user, obj("eventId", Long.toString(event)));
    assertEquals(event, outbox.after(user, 0).get(0).id());
    assertTrue(outbox.after(LocalDataInitializer.USER, 0).stream().noneMatch(e -> e.id() == event));
    records.delete("plans", resource, user);
    assertTrue(records.find("plans", resource, user).isEmpty());
  }
}
