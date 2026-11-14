package com.kaoyan.ai.service.task;

import com.kaoyan.ai.common.ApiException;
import java.time.Instant;

enum StudyMode {
  SCHOLAR(5, "START_RECOMMENDED", 0),
  GENTLE(10, "START_FIVE", 5),
  STRICT(0, "START_TIMER", 0);

  final int delay;
  final String action;
  final int minutes;

  StudyMode(int delay, String action, int minutes) {
    this.delay = delay;
    this.action = action;
    this.minutes = minutes;
  }

  static StudyMode parse(String value) {
    try {
      return valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ApiException(422, "VALIDATION_FAILED", "请选择学霸、温柔或严管模式");
    }
  }

  int round(Instant scheduled, Instant now, Instant first, boolean secondSent) {
    if (first == null) return now.isBefore(scheduled.plusSeconds(delay * 60L)) ? 0 : 1;
    Instant followup = scheduled.plusSeconds(300);
    return this == STRICT && !secondSent && first.isBefore(followup) && !now.isBefore(followup)
        ? 2 : 0;
  }

  String reminder(String title, String localTime, String step) {
    return switch (this) {
      case SCHOLAR -> "接下来学「" + title + "」。先确定一个可验收的小目标。";
      case GENTLE -> "「" + title + "」可以从一小步开始：" + step + "。先试 5 分钟。";
      case STRICT -> "「" + title + "」计划 " + localTime + " 开始，当前尚未开始。请立即计时或调整时间。";
    };
  }
}
