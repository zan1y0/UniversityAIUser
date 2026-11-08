package com.kaoyan.ai.persistence.mapper;

import com.kaoyan.ai.persistence.entity.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** SQL 定义见 resources/mapper/AggregateMapper.xml。 */
public interface AggregateMapper {

  int insert(@Param("table") AggregateTable table, @Param("row") AggregateRecord row);
  String findPayload(@Param("table") AggregateTable table, @Param("id") String id, @Param("owner") String owner);
  Integer findVersion(@Param("table") AggregateTable table, @Param("id") String id, @Param("owner") String owner);
  List<String> findByOwner(@Param("table") AggregateTable table, @Param("owner") String owner);
  int update(@Param("table") AggregateTable table, @Param("row") AggregateRecord row, @Param("expected") int expected);
  int delete(@Param("table") AggregateTable table, @Param("id") String id, @Param("owner") String owner);
  List<AggregateRecord> scan(@Param("table") AggregateTable table);
  List<AggregateRecord> findQueuedJobs();
}
