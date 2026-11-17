package com.kaoyan.ai.infrastructure.mq;

import com.kaoyan.ai.service.task.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 任务队列消费者：收到 JobQueued 后交给 JobWorker 执行。
 *
 * <p>消费与轮询共用 {@link JobWorker#dispatch}，同实例内按 jobId 去重，跨实例靠数据库的
 * 行锁 + 版本号抢占（只有一个实例能把 queued 改成 running），因此重复投递是安全的。
 */
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true")
public class JobQueueListener {

  private static final Logger log = LoggerFactory.getLogger(JobQueueListener.class);

  private final JobWorker worker;

  public JobQueueListener(JobWorker worker) {
    this.worker = worker;
  }

  @RabbitListener(queues = "${app.mq.job-queue:kaoyan.job.queue}")
  public void onJobQueued(JobQueued message) {
    log.info("收到任务投递消息：job={} user={}", message.jobId(), message.userId());
    worker.dispatch(message.userId(), message.jobId());
  }
}