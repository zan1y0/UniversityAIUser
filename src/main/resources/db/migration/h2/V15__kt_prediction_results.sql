CREATE TABLE kt_prediction_results (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  operation_id VARCHAR(16) NOT NULL,
  algorithm_version VARCHAR(64) NOT NULL,
  latency_ms BIGINT NOT NULL,
  cache_hit BOOLEAN NOT NULL,
  result_json CLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_kt_results_user_subject_time ON kt_prediction_results(user_id, subject_id, created_at);
