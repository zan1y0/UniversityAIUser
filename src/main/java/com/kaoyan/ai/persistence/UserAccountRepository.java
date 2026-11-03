package com.kaoyan.ai.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** app_users 的参数化 JDBC 映射；口令哈希仅供认证服务使用。 */
@Repository
public class UserAccountRepository {
  public record Account(String id, String loginName, String displayName, String passwordHash) {
    @Override
    public String toString() {
      return "Account[id=" + id + ", loginName=" + loginName + ", passwordHash=REDACTED]";
    }
  }

  private final JdbcTemplate jdbc;

  public UserAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Account> findByLoginName(String loginName) {
    return jdbc
        .query(
            "SELECT id,login_name,display_name,password_hash FROM app_users WHERE login_name=?",
            (rs, n) ->
                new Account(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
            loginName)
        .stream()
        .findFirst();
  }

  public boolean existsById(String id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE id=?", Integer.class, id) > 0;
  }

  public Optional<Account> findById(String id) {
    return jdbc.query(
        "SELECT id,login_name,display_name,password_hash FROM app_users WHERE id=?",
        (rs, n) -> new Account(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
        id).stream().findFirst();
  }

  public void create(Account account) {
    jdbc.update(
        "INSERT INTO app_users(id,login_name,display_name,password_hash) VALUES (?,?,?,?)",
        account.id(),
        account.loginName(),
        account.displayName(),
        account.passwordHash());
  }
}
