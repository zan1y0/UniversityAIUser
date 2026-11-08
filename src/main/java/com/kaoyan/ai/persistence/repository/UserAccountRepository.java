package com.kaoyan.ai.persistence.repository;

import com.kaoyan.ai.persistence.entity.UserAccount;
import com.kaoyan.ai.persistence.mapper.UserAccountMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 账户持久化适配层，SQL 由 MyBatis Mapper 执行。 */
@Repository
public class UserAccountRepository {
  private final UserAccountMapper mapper;

  public UserAccountRepository(UserAccountMapper mapper) {
    this.mapper = mapper;
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

  public void create(UserAccount account) {
    mapper.insert(account);
  }
}
