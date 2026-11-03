CREATE TABLE auth_tokens (
  token_id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE INDEX ix_auth_tokens_expiry ON auth_tokens(expires_at);
