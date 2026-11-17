package com.kaoyan.ai.infrastructure.mq;

/**
 * 事件广播消息：某个用户的 outbox 里新增了一条事件，各实例收到后立刻推给自己本地的 SSE 连接。
 *
 * <p>事件正文仍从 event_outbox 读取（带 Last-Event-ID 游标），消息丢了也不影响：1 秒轮询会补推。
 *
 * @param userId 事件所属用户
 * @param eventId event_outbox 主键
 */
public record EventBroadcast(String userId, String eventId) {}