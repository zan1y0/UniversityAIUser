ALTER TABLE app_users ADD COLUMN email VARCHAR(254);
ALTER TABLE app_users ADD COLUMN email_verified_at TIMESTAMP NULL;
ALTER TABLE app_users ADD COLUMN account_status VARCHAR(24) NOT NULL DEFAULT 'PENDING_ACTIVATION';
ALTER TABLE app_users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN terms_accepted_at TIMESTAMP NULL;
ALTER TABLE app_users ADD CONSTRAINT uk_app_users_email UNIQUE (email);
ALTER TABLE app_users ADD CONSTRAINT ck_app_users_status CHECK (account_status IN ('PENDING_ACTIVATION','ENABLED','DISABLED'));
CREATE INDEX ix_auth_tokens_user ON auth_tokens(user_id);

CREATE TABLE identity_locks (lock_id INTEGER PRIMARY KEY);
INSERT INTO identity_locks(lock_id) VALUES (1),(2);
CREATE TABLE admin_bootstrap (slot INTEGER PRIMARY KEY, completed_at TIMESTAMP NULL, user_id VARCHAR(36), FOREIGN KEY(user_id) REFERENCES app_users(id));
INSERT INTO admin_bootstrap(slot) VALUES (1);

CREATE TABLE email_challenges (
 purpose VARCHAR(24) NOT NULL, email VARCHAR(254) NOT NULL, scope_key VARCHAR(128) NOT NULL,
 digest VARCHAR(64) NOT NULL, expires_at TIMESTAMP NOT NULL, failures INTEGER NOT NULL DEFAULT 0,
 PRIMARY KEY(purpose,email,scope_key)
);
CREATE INDEX ix_email_challenges_expiry ON email_challenges(expires_at);
CREATE TABLE email_send_quota (
 subject_key VARCHAR(80) NOT NULL, period_seconds INTEGER NOT NULL, window_start BIGINT NOT NULL,
 send_count INTEGER NOT NULL, PRIMARY KEY(subject_key,period_seconds,window_start)
);
CREATE TABLE account_invitations (
 user_id VARCHAR(36) PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, token_digest VARCHAR(64) NOT NULL UNIQUE,
 purpose VARCHAR(24) NOT NULL, expires_at TIMESTAMP NOT NULL, delivery_status VARCHAR(16) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES app_users(id)
);
CREATE TABLE security_audit (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36), action_code VARCHAR(64) NOT NULL,
 target_id VARCHAR(128) NOT NULL, before_value TEXT, after_value TEXT,
 reason VARCHAR(250) NOT NULL, trace_id VARCHAR(64) NOT NULL, occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_security_audit_time ON security_audit(occurred_at);
CREATE TABLE managed_settings (setting_key VARCHAR(64) PRIMARY KEY, setting_value VARCHAR(500) NOT NULL);
INSERT INTO managed_settings VALUES ('platform.announcement','');
INSERT INTO sys_roles VALUES ('ADMIN','管理员');
INSERT INTO sys_permissions VALUES
 ('user:list','查看用户'),('user:update','管理账号资料与历史绑定'),('user:disable','停用账号'),('user:enable','恢复账号'),
 ('teacher:create','发放老师账号'),('teacher:invite','重发老师邀请'),('teacher:manage','取消老师邀请'),
 ('role:list','查看角色'),('role:update','编辑角色'),('role:assign','分配用户角色'),
 ('permission:list','查看权限'),('permission:assign','编辑角色权限'),('system:config:manage','管理平台配置'),
 ('system:log:read','查看审计日志');
INSERT INTO sys_role_permissions(role_code,permission_code)
 SELECT 'ADMIN',permission_code FROM sys_permissions WHERE permission_code IN
 ('user:list','user:update','user:disable','user:enable','teacher:create','teacher:invite','teacher:manage',
  'role:list','role:update','role:assign','permission:list','permission:assign','system:config:manage','system:log:read');
