package com.kaoyan.ai.infrastructure.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 发布器在不装 RabbitMQ 的机器上必须完全静默：既不能建连接，也不能把异常抛给业务事务。
 */
class MqPublisherTests {

  @Test
  void 关闭时不投递任何消息() {
    MqProperties props = new MqProperties();
    RabbitTemplate template = mock(RabbitTemplate.class);
    MqPublisher publisher = new MqPublisher(props, providerOf(template));

    publisher.onJobQueued(new JobQueued("u1", "j1"));
    publisher.broadcastEvent("u1", "1");

    verifyNoInteractions(template);
  }

  @Test
  void 开启时投递任务与广播消息() {
    MqProperties props = new MqProperties();
    props.setEnabled(true);
    RabbitTemplate template = mock(RabbitTemplate.class);
    MqPublisher publisher = new MqPublisher(props, providerOf(template));

    publisher.onJobQueued(new JobQueued("u1", "j1"));
    publisher.broadcastEvent("u1", "9");

    verify(template)
        .convertAndSend(eq("kaoyan.job.exchange"), eq("job.queued"), eq(new JobQueued("u1", "j1")));
    verify(template)
        .convertAndSend(
            eq("kaoyan.event.exchange"), eq("event.broadcast"), eq(new EventBroadcast("u1", "9")));
  }

  @Test
  void broker不可用时只记告警不影响业务() {
    MqProperties props = new MqProperties();
    props.setEnabled(true);
    RabbitTemplate template = mock(RabbitTemplate.class);
    doThrow(new IllegalStateException("connection refused"))
        .when(template)
        .convertAndSend(anyString(), anyString(), any(Object.class));
    MqPublisher publisher = new MqPublisher(props, providerOf(template));

    publisher.onJobQueued(new JobQueued("u1", "j1"));
    publisher.broadcastEvent("u1", "9");

    verify(template, times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<RabbitTemplate> providerOf(RabbitTemplate template) {
    ObjectProvider<RabbitTemplate> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(template);
    return provider;
  }

  /** 消息体是 record，必须能序列化再反序列化回来，否则真实 broker 上会消费失败。 */
  @Test
  void 消息可往返序列化() {
    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
    JobQueued job = new JobQueued("u1", "j1");
    EventBroadcast event = new EventBroadcast("u1", "9");

    assertEquals(job, converter.fromMessage(converter.toMessage(job, new MessageProperties())));
    assertEquals(event, converter.fromMessage(converter.toMessage(event, new MessageProperties())));
  }
}