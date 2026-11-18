package com.kaoyan.ai.infrastructure.mq;

import com.kaoyan.ai.service.task.EventService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 事件广播消费者：任意实例写入 outbox 后，本实例立刻把该用户的待推送事件发给自己的 SSE 连接。
 *
 * <p>此前事件只能靠本进程 1 秒轮询推送，多实例部署时用户连在 A 实例上、任务在 B 实例执行，
 * 事件就推不到；广播消费者补上了这条路。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class EventBroadcastListener {

  private final EventService events;

  public EventBroadcastListener(EventService events) {
    this.events = events;
  }

  @RabbitListener(queues = "${app.mq.event-queue:kaoyan.event.queue}")
  public void onEventBroadcast(EventBroadcast message) {
    events.deliverToUser(message.userId());
  }
}