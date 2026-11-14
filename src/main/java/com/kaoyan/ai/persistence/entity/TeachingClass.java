package com.kaoyan.ai.persistence.entity;

public record TeachingClass(String id, String teacherId, String code, String name,
    String teacherName, long memberCount) {}
