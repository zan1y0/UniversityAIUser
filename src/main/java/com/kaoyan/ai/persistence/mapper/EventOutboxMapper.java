package com.kaoyan.ai.persistence.mapper;

import com.kaoyan.ai.persistence.entity.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** SQL 定义见 resources/mapper/EventOutboxMapper.xml。 */
public interface EventOutboxMapper {

  int insert(OutboxRecord record);
  int complete(@Param("id") long id, @Param("owner") String owner, @Param("payload") String payload);
  long latest(@Param("owner") String owner);
  List<OutboxRecord> after(@Param("owner") String owner, @Param("cursor") long cursor);
}
