-- UniversityAIUser MySQL 8.0 physical schema. Generated 2026-10-18.
-- Existing data is retained. IF NOT EXISTS does not repair an incompatible existing table.
-- 37 application tables; Flyway history is owned by Flyway, not by this script.
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS `kaoyan_ai` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `kaoyan_ai`;

-- 用户账号 | 认证
CREATE TABLE IF NOT EXISTS `app_users` (
  `id` varchar(36) NOT NULL,
  `login_name` varchar(64) NOT NULL,
  `display_name` varchar(64) NOT NULL,
  `password_hash` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `login_name` (`login_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 作答与批改 | M06
CREATE TABLE IF NOT EXISTS `attempts` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_attempts_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 数字人偏好 | M01
CREATE TABLE IF NOT EXISTS `avatar_preferences` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_avatar_preferences_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 参考书与章节 | M04
CREATE TABLE IF NOT EXISTS `books` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_books_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 每日打卡 | M01
CREATE TABLE IF NOT EXISTS `checkins` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_checkins_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 答疑会话 | M05
CREATE TABLE IF NOT EXISTS `conversations` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_conversations_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 择校评估 | M02
CREATE TABLE IF NOT EXISTS `evaluations` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_evaluations_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 事件发件箱 | 支撑
CREATE TABLE IF NOT EXISTS `event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` varchar(36) NOT NULL,
  `event_type` varchar(40) NOT NULL,
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_outbox_owner` (`owner_id`,`id`),
  CONSTRAINT `event_outbox_ibfk_1` FOREIGN KEY (`owner_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 模考成绩 | M08
CREATE TABLE IF NOT EXISTS `exam_results` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_exam_results_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 限时模考会话 | M08
CREATE TABLE IF NOT EXISTS `exam_sessions` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_exam_sessions_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 模考交卷 | M08
CREATE TABLE IF NOT EXISTS `exam_submissions` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_exam_submissions_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 私有文件元数据 | 支撑
CREATE TABLE IF NOT EXISTS `files` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_files_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 幂等响应 | 支撑
CREATE TABLE IF NOT EXISTS `idempotency_records` (
  `user_id` varchar(36) NOT NULL,
  `operation_id` varchar(10) NOT NULL,
  `idem_key` varchar(36) NOT NULL,
  `fingerprint` varchar(64) NOT NULL,
  `status_code` int NOT NULL,
  `response_body` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`operation_id`,`idem_key`),
  CONSTRAINT `idempotency_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 复试评估报告 | M09
CREATE TABLE IF NOT EXISTS `interview_reports` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_interview_reports_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 复试问答轮次 | M09
CREATE TABLE IF NOT EXISTS `interview_turns` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_interview_turns_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 复试训练会话 | M09
CREATE TABLE IF NOT EXISTS `interviews` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_interviews_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 异步任务内部命令 | 支撑
CREATE TABLE IF NOT EXISTS `job_commands` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_job_commands_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 异步任务状态 | 支撑
CREATE TABLE IF NOT EXISTS `jobs` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_jobs_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 学科知识树 | M03 M04
CREATE TABLE IF NOT EXISTS `knowledge_trees` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_knowledge_trees_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 考试时间节点 | M11
CREATE TABLE IF NOT EXISTS `milestones` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_milestones_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 资讯关注设置 | M11
CREATE TABLE IF NOT EXISTS `news_follows` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_news_follows_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 通知历史版本 | M11
CREATE TABLE IF NOT EXISTS `notice_revisions` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_notice_revisions_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 公开政策通知 | M11
CREATE TABLE IF NOT EXISTS `notices` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_notices_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户通知 | M11
CREATE TABLE IF NOT EXISTS `notifications` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_notifications_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 模考试卷 | M08
CREATE TABLE IF NOT EXISTS `papers` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_papers_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 学习计划与任务 | M07
CREATE TABLE IF NOT EXISTS `plans` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_plans_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 练习会话 | M06
CREATE TABLE IF NOT EXISTS `practice_sessions` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_practice_sessions_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 用户备考资料 | 认证
CREATE TABLE IF NOT EXISTS `profiles` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_profiles_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 院校专业目录 | M02
CREATE TABLE IF NOT EXISTS `programs` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_programs_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 答疑消息 | M05
CREATE TABLE IF NOT EXISTS `qa_messages` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_qa_messages_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 题目与评分依据 | M03 M04 M06
CREATE TABLE IF NOT EXISTS `questions` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_questions_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 图片识别结果 | M05
CREATE TABLE IF NOT EXISTS `recognitions` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_recognitions_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 学习提醒偏好 | M01
CREATE TABLE IF NOT EXISTS `reminder_preferences` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_reminder_preferences_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 估分成绩快照 | M08
CREATE TABLE IF NOT EXISTS `score_snapshots` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_score_snapshots_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 学习行为记录 | M10
CREATE TABLE IF NOT EXISTS `study_events` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_study_events_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 专业课科目 | M04
CREATE TABLE IF NOT EXISTS `subjects` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_subjects_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 错题归档 | M06
CREATE TABLE IF NOT EXISTS `wrong_items` (
  `id` varchar(100) NOT NULL,
  `owner_id` varchar(36) NOT NULL,
  `version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_wrong_items_owner` (`owner_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
