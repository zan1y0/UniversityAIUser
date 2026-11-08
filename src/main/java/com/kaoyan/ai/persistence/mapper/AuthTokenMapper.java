package com.kaoyan.ai.persistence.mapper;

import com.kaoyan.ai.persistence.entity.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** SQL 定义见 resources/mapper/AuthTokenMapper.xml。 */
public interface AuthTokenMapper {

  int countActive(@Param("id") String id, @Param("user") String user, @Param("now") java.sql.Timestamp now);
  int deleteExpired(@Param("now") java.sql.Timestamp now);
  int insert(@Param("id") String id, @Param("user") String user, @Param("expires") java.sql.Timestamp expires);
  int delete(@Param("id") String id, @Param("user") String user);
}
