package com.kaoyan.ai.infrastructure.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明，仅在 {@code app.mq.enabled=true} 时生效。
 *
 * <pre>
 * job.queued  ──▶ kaoyan.job.exchange ──▶ kaoyan.job.queue ──(消费失败/重试耗尽)──▶ kaoyan.dlx ──▶ kaoyan.job.dlq
 * event.broadcast ──▶ kaoyan.event.exchange ──▶ kaoyan.event.queue（每个实例一条，实现多实例 SSE 扇出）
 * </pre>
 *
 * <p>队列与交换机都是 durable 的，应用重启不会丢积压消息；死信队列用于保存重试后仍失败的消息，便于排查。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class RabbitConfig {

  private final MqProperties props;

  public RabbitConfig(MqProperties props) {
    this.props = props;
  }

  @Bean
  public Jackson2JsonMessageConverter mqMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public DirectExchange mqJobExchange() {
    return new DirectExchange(props.getJobExchange(), true, false);
  }

  @Bean
  public Queue mqJobQueue() {
    return QueueBuilder.durable(props.getJobQueue())
        .deadLetterExchange(props.getDeadLetterExchange())
        .deadLetterRoutingKey(props.getDeadLetterRoutingKey())
        .build();
  }

  @Bean
  public Binding mqJobBinding(Queue mqJobQueue, DirectExchange mqJobExchange) {
    return BindingBuilder.bind(mqJobQueue).to(mqJobExchange).with(props.getJobRoutingKey());
  }

  @Bean
  public DirectExchange mqDeadLetterExchange() {
    return new DirectExchange(props.getDeadLetterExchange(), true, false);
  }

  @Bean
  public Queue mqDeadLetterQueue() {
    return QueueBuilder.durable(props.getDeadLetterQueue()).build();
  }

  @Bean
  public Binding mqDeadLetterBinding(Queue mqDeadLetterQueue, DirectExchange mqDeadLetterExchange) {
    return BindingBuilder.bind(mqDeadLetterQueue)
        .to(mqDeadLetterExchange)
        .with(props.getDeadLetterRoutingKey());
  }

  @Bean
  public TopicExchange mqEventExchange() {
    return new TopicExchange(props.getEventExchange(), true, false);
  }

  @Bean
  public Queue mqEventQueue() {
    return QueueBuilder.durable(props.getEventQueue()).build();
  }

  @Bean
  public Binding mqEventBinding(Queue mqEventQueue, TopicExchange mqEventExchange) {
    return BindingBuilder.bind(mqEventQueue).to(mqEventExchange).with(props.getEventRoutingKey());
  }

  @Bean
  public RabbitTemplate mqRabbitTemplate(
      ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    return template;
  }
}