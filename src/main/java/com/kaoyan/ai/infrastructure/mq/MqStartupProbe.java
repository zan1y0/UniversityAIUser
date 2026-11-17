package com.kaoyan.ai.infrastructure.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 启动时探测 RabbitMQ：连得上才启动消费者；连不上只记一条告警，之后每分钟重试一次。
 *
 * <p>消费容器配置成 auto-startup=false，由本类统一启动。这样“开了开关但 broker 没起”的机器
 * 不会每 5 秒刷一屏连接失败堆栈；broker 稍后启动也能自动接上，不用重启应用。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class MqStartupProbe {

  private static final Logger log = LoggerFactory.getLogger(MqStartupProbe.class);

  private final ConnectionFactory connectionFactory;
  private final RabbitListenerEndpointRegistry registry;
  private final long retryMillis;
  private volatile boolean started;

  public MqStartupProbe(
      ConnectionFactory connectionFactory,
      RabbitListenerEndpointRegistry registry,
      @Value("${app.mq.probe-interval-millis:60000}") long retryMillis) {
    this.connectionFactory = connectionFactory;
    this.registry = registry;
    this.retryMillis = retryMillis;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void probeOnStartup() {
    String error = tryStart();
    if (error != null) {
      log.warn(
          "RabbitMQ 暂不可用（{}），本次不启动 MQ 消费者：任务仍由数据库兜底扫描，事件仍由 1 秒轮询推送；"
              + "broker 起来后每 {} 毫秒自动重试",
          error,
          retryMillis);
    }
  }

  /** broker 晚于应用启动时，靠这个定时器自动接上。 */
  @Scheduled(fixedDelayString = "${app.mq.probe-interval-millis:60000}")
  public void retry() {
    if (!started) tryStart();
  }

  private String tryStart() {
    if (started) return null;
    try (Connection ignored = connectionFactory.createConnection()) {
      registry.start();
      started = true;
      log.info("RabbitMQ 已连接，任务投递与事件广播消费者已启动");
      return null;
    } catch (Exception e) {
      return e.getMessage();
    }
  }
}