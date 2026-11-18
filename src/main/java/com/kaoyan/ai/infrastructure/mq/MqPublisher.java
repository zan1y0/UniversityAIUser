package com.kaoyan.ai.infrastructure.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 消息发布器。
 *
 * <p>两种消息：
 *
 * <ul>
 *   <li>任务投递：JobService 把任务写进 jobs 表后发出 {@link JobQueued}，本类在事务提交之后才真正投递，
 *       避免消费方读到尚未提交的任务行。
 *   <li>事件广播：EventService 把事件写进 event_outbox 后调用 {@link #broadcastEvent}，让其他实例
 *       立即把事件推给自己本地的 SSE 连接。
 * </ul>
 *
 * <p>broker 不可用时只记一条告警：任务仍会被数据库轮询捡起来，事件仍会被 1 秒轮询补推，业务不受影响。
 */
@Component
public class MqPublisher {

  private static final Logger log = LoggerFactory.getLogger(MqPublisher.class);

  private final MqProperties props;
  private final ObjectProvider<RabbitTemplate> template;

  public MqPublisher(MqProperties props, ObjectProvider<RabbitTemplate> template) {
    this.props = props;
    this.template = template;
  }

  /** 任务创建事务提交后再投递，保证消费方一定能读到这条 jobs 记录。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onJobQueued(JobQueued message) {
    send(props.getJobExchange(), props.getJobRoutingKey(), message, "任务投递 " + message.jobId());
  }

  /** 事件写入 outbox 后广播给所有实例；本地实例也会收到自己发的这条消息，重复推送由游标去重。 */
  public void broadcastEvent(String user, String eventId) {
    send(
        props.getEventExchange(),
        props.getEventRoutingKey(),
        new EventBroadcast(user, eventId),
        "事件广播 " + eventId);
  }

  private void send(String exchange, String routingKey, Object payload, String what) {
    if (!props.isEnabled()) return;
    RabbitTemplate rabbit = template.getIfAvailable();
    if (rabbit == null) return;
    try {
      rabbit.convertAndSend(exchange, routingKey, payload);
    } catch (Exception e) {
      log.warn("消息队列不可用，{} 改由数据库轮询兜底：{}", what, e.getMessage());
    }
  }
}