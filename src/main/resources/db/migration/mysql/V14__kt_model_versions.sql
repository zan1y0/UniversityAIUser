CREATE TABLE kt_model_versions (
  algorithm_version VARCHAR(128) PRIMARY KEY,
  registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
