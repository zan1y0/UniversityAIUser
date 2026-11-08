package com.kaoyan.ai.persistence.entity;

/** 数据库账户实体；口令哈希不得输出到日志或接口。 */
public record UserAccount(String id, String loginName, String displayName, String passwordHash) {
  @Override
  public String toString() {
    return "UserAccount[id=" + id + ", loginName=" + loginName + ", passwordHash=REDACTED]";
  }
}
