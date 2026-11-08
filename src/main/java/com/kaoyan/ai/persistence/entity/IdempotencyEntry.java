package com.kaoyan.ai.persistence.entity;

import java.time.Instant;

public record IdempotencyEntry(String fingerprint, int statusCode, String responseBody, Instant createdAt) {}
