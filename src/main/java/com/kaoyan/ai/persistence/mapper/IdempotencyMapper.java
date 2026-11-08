package com.kaoyan.ai.persistence.mapper;

import com.kaoyan.ai.persistence.entity.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** SQL 定义见 resources/mapper/IdempotencyMapper.xml。 */
public interface IdempotencyMapper {

  IdempotencyEntry find(@Param("user") String user, @Param("operation") String operation, @Param("key") String key);
  int insert(@Param("user") String user, @Param("operation") String operation, @Param("key") String key,
      @Param("fingerprint") String fingerprint, @Param("status") int status, @Param("response") String response);
  int delete(@Param("user") String user, @Param("operation") String operation, @Param("key") String key);
}
