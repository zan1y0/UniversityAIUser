package com.kaoyan.ai.persistence.entity;

import java.time.Instant;

public record EmailAccount(String id, String email, String displayName, String passwordHash,
    String status, Instant verifiedAt, long authVersion) {
  public boolean enabled() { return "ENABLED".equals(status) && verifiedAt != null; }

  @Override public String toString() { return "EmailAccount[id=" + id + ", status=" + status + "]"; }
}
