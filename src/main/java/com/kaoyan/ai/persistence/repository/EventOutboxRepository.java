package com.kaoyan.ai.persistence.repository;

import static com.kaoyan.ai.common.Jsons.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.persistence.entity.OutboxRecord;
import com.kaoyan.ai.persistence.mapper.EventOutboxMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class EventOutboxRepository {
  public record Event(long id, String type, JsonNode payload) {}

  private final EventOutboxMapper mapper;

  public EventOutboxRepository(EventOutboxMapper mapper) {
    this.mapper = mapper;
  }

  public long reserve(String user, String type) {
    var record = new OutboxRecord();
    record.setOwnerId(user);
    record.setEventType(type);
    record.setPayload("{}");
    mapper.insert(record);
    if (record.getId() == null) throw new IllegalStateException("事件主键未返回");
    return record.getId();
  }

  public void complete(long id, String owner, JsonNode payload) {
    if (mapper.complete(id, owner, payload.toString()) != 1)
      throw new IllegalStateException("事件记录不存在或归属不匹配");
  }

  public long latest(String user) {
    return mapper.latest(user);
  }

  public List<Event> after(String user, long cursor) {
    return mapper.after(user, cursor).stream()
        .map(row -> new Event(row.getId(), row.getEventType(), parse(row.getPayload()))).toList();
  }
}
