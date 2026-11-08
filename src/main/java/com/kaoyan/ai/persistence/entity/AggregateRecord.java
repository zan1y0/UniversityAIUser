package com.kaoyan.ai.persistence.entity;

/** 与既有聚合表对应，不改写历史 JSON 数据。 */
public record AggregateRecord(String id, String ownerId, Integer version, String payload) {}
