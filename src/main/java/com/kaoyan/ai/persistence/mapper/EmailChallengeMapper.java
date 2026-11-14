package com.kaoyan.ai.persistence.mapper;

import java.sql.Timestamp;
import org.apache.ibatis.annotations.Param;

public interface EmailChallengeMapper {
  record Challenge(String digest, Timestamp expires, int failures) {}
  Challenge find(@Param("purpose") String purpose, @Param("email") String email, @Param("scope") String scope);
  void remove(@Param("purpose") String purpose, @Param("email") String email, @Param("scope") String scope);
  void add(@Param("purpose") String purpose, @Param("email") String email, @Param("scope") String scope,
      @Param("digest") String digest, @Param("expires") Timestamp expires);
  void fail(@Param("purpose") String purpose, @Param("email") String email, @Param("scope") String scope);
  Integer count(@Param("key") String key, @Param("period") int period, @Param("start") long start);
  void createBucket(@Param("key") String key, @Param("period") int period, @Param("start") long start);
  void increment(@Param("key") String key, @Param("period") int period, @Param("start") long start);
  void setCount(@Param("key") String key, @Param("period") int period, @Param("start") long start,
      @Param("value") int value);
  void cleanQuota(long cutoff);
  void cleanChallenges(Timestamp cutoff);
}
