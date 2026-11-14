package com.kaoyan.ai.persistence.entity;
public record TeachingTask(String id, String teacherId, String studentId, String title,
    String description, String deadline, String status, int version, String createdAt, String completedAt,
    String classId, boolean requiresSubmission, String publicationId) {}
