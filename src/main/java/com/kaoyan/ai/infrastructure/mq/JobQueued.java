package com.kaoyan.ai.infrastructure.mq;

/**
 * 任务投递消息：任务行已经写进 jobs 表，这里只告诉消费方“有一个新任务可以跑了”。
 *
 * <p>消息里只带标识，不带业务数据：消费方仍以数据库为准，重复投递也只是多一次抢占失败，
 * 因此可以安全地重试、重放。
 *
 * @param userId 任务所属用户
 * @param jobId jobs 表主键
 */
public record JobQueued(String userId, String jobId) {}