package com.kaoyan.ai.persistence.entity;

/** 自增键由 MyBatis 回填，数据库负载以原始 JSON 字符串存取。 */
public class OutboxRecord {
  private Long id;
  private String ownerId;
  private String eventType;
  private String payload;
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getOwnerId() { return ownerId; }
  public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
}
