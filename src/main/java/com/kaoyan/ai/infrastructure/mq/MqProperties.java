package com.kaoyan.ai.infrastructure.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 消息队列配置（前缀 app.mq）。
 *
 * <p>默认 {@code enabled=false}：没装 RabbitMQ 的机器完全不建立连接，后台任务仍然由数据库轮询执行，
 * 行为与引入 MQ 之前完全一致。开启后 jobs 表依旧是任务状态的唯一事实来源，MQ 只负责“立即唤醒”和
 * “跨实例广播”，所以 broker 挂掉只会退化为轮询，不会丢任务。
 */
@Component
@ConfigurationProperties(prefix = "app.mq")
public class MqProperties {

  /** 总开关；true 时才会声明队列、启动消费者并投递消息。 */
  private boolean enabled = false;

  /** 任务投递交换机（direct）。 */
  private String jobExchange = "kaoyan.job.exchange";

  /** 任务队列；消费失败重试耗尽后进入死信队列。 */
  private String jobQueue = "kaoyan.job.queue";

  /** 任务投递路由键。 */
  private String jobRoutingKey = "job.queued";

  /** 死信交换机。 */
  private String deadLetterExchange = "kaoyan.dlx";

  /** 死信队列，保存重试后仍失败的消息，便于人工排查。 */
  private String deadLetterQueue = "kaoyan.job.dlq";

  /** 死信路由键。 */
  private String deadLetterRoutingKey = "job.dead";

  /** 事件广播交换机（topic）。 */
  private String eventExchange = "kaoyan.event.exchange";

  /** 事件广播队列；每个实例各有一条自己的队列绑定，实现多实例 SSE 扇出。 */
  private String eventQueue = "kaoyan.event.queue";

  /** 事件广播路由键。 */
  private String eventRoutingKey = "event.broadcast";

  /** MQ 开启时数据库兜底扫描的间隔（毫秒）。 */
  private long safetyScanMillis = 30_000L;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getJobExchange() {
    return jobExchange;
  }

  public void setJobExchange(String jobExchange) {
    this.jobExchange = jobExchange;
  }

  public String getJobQueue() {
    return jobQueue;
  }

  public void setJobQueue(String jobQueue) {
    this.jobQueue = jobQueue;
  }

  public String getJobRoutingKey() {
    return jobRoutingKey;
  }

  public void setJobRoutingKey(String jobRoutingKey) {
    this.jobRoutingKey = jobRoutingKey;
  }

  public String getDeadLetterExchange() {
    return deadLetterExchange;
  }

  public void setDeadLetterExchange(String deadLetterExchange) {
    this.deadLetterExchange = deadLetterExchange;
  }

  public String getDeadLetterQueue() {
    return deadLetterQueue;
  }

  public void setDeadLetterQueue(String deadLetterQueue) {
    this.deadLetterQueue = deadLetterQueue;
  }

  public String getDeadLetterRoutingKey() {
    return deadLetterRoutingKey;
  }

  public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
    this.deadLetterRoutingKey = deadLetterRoutingKey;
  }

  public String getEventExchange() {
    return eventExchange;
  }

  public void setEventExchange(String eventExchange) {
    this.eventExchange = eventExchange;
  }

  public String getEventQueue() {
    return eventQueue;
  }

  public void setEventQueue(String eventQueue) {
    this.eventQueue = eventQueue;
  }

  public String getEventRoutingKey() {
    return eventRoutingKey;
  }

  public void setEventRoutingKey(String eventRoutingKey) {
    this.eventRoutingKey = eventRoutingKey;
  }

  public long getSafetyScanMillis() {
    return safetyScanMillis;
  }

  public void setSafetyScanMillis(long safetyScanMillis) {
    this.safetyScanMillis = safetyScanMillis;
  }
}