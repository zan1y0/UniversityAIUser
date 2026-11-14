CREATE TABLE study_supervision (
 id VARCHAR(100) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL,
 version INT NOT NULL DEFAULT 1, payload CLOB NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_study_supervision_owner ON study_supervision(owner_id,created_at);
