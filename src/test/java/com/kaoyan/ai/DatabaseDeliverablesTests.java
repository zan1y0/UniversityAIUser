package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;

import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:database_deliverables;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.local-student-password=LocalStudy2026!",
      "app.seed-local=true"
    })
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseDeliverablesTests {
  @Autowired UserAccountRepository accounts;
  @Autowired IdempotencyRepository idempotency;
  @Autowired EventOutboxRepository outbox;
  @Autowired RecordRepository records;
  static final String OWNER = LocalDataInitializer.USER;
  static final String OTHER = LocalDataInitializer.SECOND;

  @Test
  @Transactional
  void usersUseBoundParametersAndUniqueLogin() {
    var account =
        new UserAccountRepository.Account(id(), "' OR 1=1 --", "参数绑定验证", "hashed-test-value");
    accounts.create(account);
    assertEquals(account.id(), accounts.findByLoginName(account.loginName()).orElseThrow().id());
    assertTrue(accounts.findByLoginName("not-a-user").isEmpty());
    assertFalse(account.toString().contains("hashed-test-value"));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            accounts.create(
                new UserAccountRepository.Account(
                    id(), account.loginName(), "重复", "another-hash")));
  }

  @Test
  @Transactional
  void idempotencyKeyIsIsolatedByUserAndOperation() {
    String key = id();
    String original = "{\"code\":\"OK\",\"traceId\":\"original\"}";
    idempotency.save(OWNER, "op015", key, "a".repeat(64), 201, original);
    assertEquals(original, idempotency.find(OWNER, "op015", key).orElseThrow().responseBody());
    assertTrue(idempotency.find(OTHER, "op015", key).isEmpty());
    assertTrue(idempotency.find(OWNER, "op016", key).isEmpty());
    idempotency.delete(OTHER, "op015", key);
    assertTrue(idempotency.find(OWNER, "op015", key).isPresent());
    idempotency.delete(OWNER, "op015", key);
    assertTrue(idempotency.find(OWNER, "op015", key).isEmpty());
  }

  @Test
  @Transactional
  void outboxUsesGeneratedIdAndOwnedCursor() {
    long before = outbox.latest(OWNER);
    long event = outbox.reserve(OWNER, "job.failed");
    outbox.complete(event, OWNER, obj("marker", "数据库往返"));
    assertTrue(event > before);
    assertEquals("数据库往返", outbox.after(OWNER, before).get(0).payload().path("marker").asText());
    assertTrue(outbox.after(OTHER, event - 1).stream().noneMatch(e -> e.id() == event));
    assertThrows(IllegalStateException.class, () -> outbox.complete(event, OTHER, obj()));
  }

  @Test
  @Transactional
  void aggregateCrudPreservesOwnerAndVersion() {
    String resource = id();
    var value = obj("id", resource, "version", 1, "content", "初始");
    records.create("plans", resource, OWNER, value);
    assertThrows(ApiException.class, () -> records.get("plans", resource, OTHER));
    value.put("version", 2);
    value.put("content", "更新");
    records.update("plans", resource, OWNER, value, 1);
    assertEquals("更新", records.get("plans", resource, OWNER).path("content").asText());
    assertThrows(ApiException.class, () -> records.update("plans", resource, OWNER, value, 1));
    assertThrows(
        IllegalArgumentException.class, () -> records.list("plans; DROP TABLE app_users", OWNER));
    records.delete("plans", resource, OWNER);
    assertTrue(records.find("plans", resource, OWNER).isEmpty());
  }

  @AfterAll
  void exportOnlyTestDatabaseSeedForSqlDeliverable() throws Exception {
    var snapshot = obj();
    snapshot.set("app_users", arr());
    for (var user :
        records
            .jdbc()
            .queryForList(
                "SELECT id,login_name,display_name,password_hash FROM app_users ORDER BY id"))
      snapshot
          .withArray("app_users")
          .add(
              obj(
                  "id",
                  user.get("id"),
                  "login_name",
                  user.get("login_name"),
                  "display_name",
                  user.get("display_name"),
                  "password_hash",
                  user.get("password_hash")));
    for (String table : new TreeSet<>(RecordRepository.TABLES)) {
      var rows = arr();
      for (var row :
          records
              .jdbc()
              .queryForList("SELECT id,owner_id,version,payload FROM " + table + " ORDER BY id"))
        rows.add(
            obj(
                "id",
                row.get("id"),
                "owner_id",
                row.get("owner_id"),
                "version",
                row.get("version"),
                "payload",
                parse(row.get("payload").toString())));
      if (!rows.isEmpty()) snapshot.set(table, rows);
    }
    Files.createDirectories(Path.of("target/verification"));
    Files.writeString(
        Path.of("target/verification/database-test-seed.json"), snapshot.toPrettyString());
  }
}
