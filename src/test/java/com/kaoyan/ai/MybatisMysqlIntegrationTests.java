package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;

import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.entity.*;
import com.kaoyan.ai.persistence.mapper.*;
import com.kaoyan.ai.persistence.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用既有 MySQL 连接的显式验证。只加载持久化层，不执行 Flyway、种子或后台任务；
 * 所有测试写入均回滚，不能因验证触发原有任务恢复或业务数据变更。
 */
@EnabledIfSystemProperty(named = "mysql.integration", matches = "true")
@ActiveProfiles({"mysql", "mybatis-persistence-verification"})
@SpringBootTest(
    classes = MybatisMysqlIntegrationTests.PersistenceConfiguration.class,
    properties = "spring.flyway.enabled=false")
class MybatisMysqlIntegrationTests {
  @Configuration(proxyBeanMethods = false)
  @Profile("mybatis-persistence-verification")
  @EnableAutoConfiguration
  @MapperScan("com.kaoyan.ai.persistence.mapper")
  @Import({UserAccountRepository.class, RecordRepository.class,
      IdempotencyRepository.class, EventOutboxRepository.class})
  static class PersistenceConfiguration {}

  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate verificationJdbc;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired UserAccountRepository accounts;
  @Autowired RecordRepository records;
  @Autowired IdempotencyRepository idempotency;
  @Autowired EventOutboxRepository outbox;
  @Autowired AggregateMapper aggregates;
  @Autowired AuthTokenMapper tokens;

  @Test
  void actualMysqlReadsExistingRowsWithoutChangingTheirContents() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
      System.out.println("MyBatis MySQL connection verified: database=" + connection.getCatalog()
          + ", version=" + connection.getMetaData().getDatabaseProductVersion());
    }
    for (String table : new TreeSet<>(AggregateTable.NAMES)) {
      // 独立 JDBC 读取作为比对基准，避免用同一 Mapper 实现验证自身。
      var existing = verificationJdbc.query(
          "SELECT id,owner_id,version,payload FROM " + new AggregateTable(table).name() + " ORDER BY id",
          (rs, n) -> new AggregateRecord(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)));
      var mapped = new ArrayList<>(aggregates.scan(new AggregateTable(table)));
      mapped.sort(Comparator.comparing(AggregateRecord::id));
      assertEquals(digest(existing), digest(mapped), "历史数据映射不一致：" + table);
    }
    for (String id : verificationJdbc.queryForList("SELECT id FROM app_users", String.class)) {
      var account = accounts.findById(id).orElseThrow();
      assertEquals(id, accounts.findByLoginName(account.loginName()).orElseThrow().id());
    }
  }

  @Test
  void actualMysqlCrudOwnershipOptimisticLockGeneratedKeysAndRollback() {
    String user = id(), other = id(), resource = id(), token = id(), key = id();
    var tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(status -> {
      accounts.create(new UserAccount(user, "mybatis_" + user, "MyBatis验证", "test-only-hash"));
      accounts.create(new UserAccount(other, "mybatis_" + other, "隔离验证", "test-only-hash"));
      records.lockOwner(user);
      assertEquals("MyBatis验证", accounts.findById(user).orElseThrow().displayName());
      var row = obj("id", resource, "version", 1, "content", "MySQL中文🙂", "nullable", null);
      records.create("plans", resource, user, row);
      assertEquals(row, records.get("plans", resource, user));
      assertTrue(records.find("plans", resource, other).isEmpty());
      assertThrows(ApiException.class, () -> records.get("plans", resource, other));
      row.put("version", 2);
      row.put("content", "更新后");
      records.update("plans", resource, user, row, 1);
      assertEquals(row, records.get("plans", resource, user));
      assertEquals(2, records.version("plans", resource, user));
      assertThrows(ApiException.class, () -> records.update("plans", resource, user, row, 1));
      assertThrows(IllegalArgumentException.class, () -> records.list("plans;DROP TABLE app_users", user));

      idempotency.save(user, "op015", key, "a".repeat(64), 201, "{\"result\":\"中文\"}");
      var entry = idempotency.find(user, "op015", key).orElseThrow();
      assertEquals(201, entry.statusCode());
      assertNotNull(entry.createdAt());
      assertTrue(idempotency.find(other, "op015", key).isEmpty());
      idempotency.delete(user, "op015", key);
      assertTrue(idempotency.find(user, "op015", key).isEmpty());

      long event = outbox.reserve(user, "job.failed");
      outbox.complete(event, user, obj("message", "事件中文"));
      assertEquals(event, outbox.latest(user));
      assertEquals("事件中文", outbox.after(user, 0).get(0).payload().path("message").asText());
      assertTrue(outbox.after(other, 0).isEmpty());

      var now = Timestamp.from(Instant.now());
      tokens.insert(token, user, Timestamp.from(Instant.now().plusSeconds(600)));
      assertEquals(1, tokens.countActive(token, user, now));
      assertEquals(0, tokens.countActive(token, other, now));
      tokens.delete(token, user);
      assertEquals(0, tokens.countActive(token, user, now));
      records.delete("plans", resource, user);
      assertTrue(records.find("plans", resource, user).isEmpty());
      status.setRollbackOnly();
    });
    assertTrue(accounts.findById(user).isEmpty());
    assertTrue(accounts.findById(other).isEmpty());
    assertTrue(records.find("plans", resource, user).isEmpty());
    assertTrue(outbox.after(user, 0).isEmpty());
  }

  private String digest(List<AggregateRecord> rows) throws Exception {
    var digest = MessageDigest.getInstance("SHA-256");
    for (var row : rows) digest.update(row.toString().getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest.digest());
  }
}
