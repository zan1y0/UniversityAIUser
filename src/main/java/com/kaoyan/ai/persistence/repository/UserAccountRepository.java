package com.kaoyan.ai.persistence.repository;

import com.kaoyan.ai.persistence.entity.UserAccount;
import com.kaoyan.ai.persistence.mapper.UserAccountMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 账户持久化适配层，SQL 由 MyBatis Mapper 执行。 */
@Repository
public class UserAccountRepository {
  private final UserAccountMapper mapper;
  private final com.kaoyan.ai.persistence.mapper.RbacMapper roles;

  public UserAccountRepository(UserAccountMapper mapper, com.kaoyan.ai.persistence.mapper.RbacMapper roles) {
    this.mapper = mapper;
    this.roles = roles;
  }

  public Optional<UserAccount> findByLoginName(String loginName) {
    return Optional.ofNullable(mapper.findByLoginName(loginName));
  }

  public boolean existsById(String id) {
    return mapper.countById(id) > 0;
  }

  public Optional<UserAccount> findById(String id) {
    return Optional.ofNullable(mapper.findById(id));
  }

  public Optional<UserAccount> findTeacherByLoginName(String loginName) {
    return Optional.ofNullable(mapper.findTeacherByLoginName(loginName));
  }

  public Optional<UserAccount> findTeacherById(String id) {
    return Optional.ofNullable(mapper.findTeacherById(id));
  }

  @org.springframework.transaction.annotation.Transactional
  public void createTeacher(UserAccount account) {
    // The common row is the identity referenced by sessions, profiles and business records.
    mapper.insert(new UserAccount(account.id(), "teacher:" + account.id(), account.displayName(), "!TEACHER_LOGIN_ONLY"));
    mapper.insertTeacherCredentials(account);
    roles.grant(account.id(), "TEACHER");
  }

  @org.springframework.transaction.annotation.Transactional
  public void create(UserAccount account) {
    mapper.insert(account);
    roles.grant(account.id(), "STUDENT");
  }
}
