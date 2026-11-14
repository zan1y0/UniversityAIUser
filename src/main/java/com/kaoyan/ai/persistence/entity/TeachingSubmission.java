package com.kaoyan.ai.persistence.entity;

public record TeachingSubmission(String taskId, String content, String submittedAt,
    Integer score, String feedback, String gradedAt) {}
