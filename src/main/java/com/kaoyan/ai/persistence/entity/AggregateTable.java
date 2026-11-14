package com.kaoyan.ai.persistence.entity;

import java.util.Set;

/** 表名不可使用预编译参数，通过不可变白名单类型保证 Mapper 动态表名安全。 */
public record AggregateTable(String name) {
  public static final Set<String> NAMES = Set.of(
          "profiles",
          "avatar_preferences",
          "reminder_preferences",
          "study_supervision",
          "checkins",
          "programs",
          "knowledge_trees",
          "subjects",
          "books",
          "questions",
          "papers",
          "evaluations",
          "conversations",
          "qa_messages",
          "recognitions",
          "practice_sessions",
          "attempts",
          "wrong_items",
          "plans",
          "exam_sessions",
          "exam_submissions",
          "exam_results",
          "score_snapshots",
          "interviews",
          "interview_turns",
          "interview_reports",
          "study_events",
          "notices",
          "notice_revisions",
          "news_follows",
          "milestones",
          "notifications",
          "jobs",
          "files",
          "job_commands");

  public AggregateTable {
    if (!NAMES.contains(name)) throw new IllegalArgumentException("未知聚合表");
  }
}
