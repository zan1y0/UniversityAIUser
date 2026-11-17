package com.kaoyan.ai.infrastructure.cache;

/** 公开聚合数据发生变化；在事务提交后触发缓存失效。 */
public record AggregateChanged(String table, String id, String owner) {}