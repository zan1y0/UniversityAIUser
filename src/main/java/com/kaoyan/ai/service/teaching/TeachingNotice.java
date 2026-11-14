package com.kaoyan.ai.service.teaching;

import static com.kaoyan.ai.common.Jsons.ZONE;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public final class TeachingNotice {
  public static final String TYPE = "teaching.task.assigned";
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE);

  private TeachingNotice() {}

  public static String title(String title) {
    return "新教学任务：" + title;
  }

  public static String content(String className, String classCode, String deadline) {
    String recipient = className == null ? "分配对象：个人" : "班级：" + className + "（" + classCode + "）";
    return recipient + "\n截止时间：" + TIME.format(Instant.parse(deadline)) + "（北京时间）";
  }
}
