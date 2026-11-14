CREATE TABLE teacher_accounts (
 user_id VARCHAR(36) PRIMARY KEY, login_name VARCHAR(64) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES app_users(id)
);
INSERT INTO teacher_accounts(user_id,login_name,password_hash)
 SELECT u.id,u.login_name,u.password_hash FROM app_users u
 JOIN sys_user_roles r ON r.user_id=u.id AND r.role_code='TEACHER';
UPDATE app_users SET login_name=CONCAT('teacher:',id), password_hash='!TEACHER_LOGIN_ONLY'
 WHERE id IN (SELECT user_id FROM teacher_accounts)
 AND id NOT IN (SELECT user_id FROM sys_user_roles WHERE role_code='STUDENT');

CREATE TABLE teaching_classes (
 id VARCHAR(36) PRIMARY KEY, teacher_id VARCHAR(36) NOT NULL,
 class_code VARCHAR(16) NOT NULL UNIQUE, class_name VARCHAR(80) NOT NULL,
 FOREIGN KEY(teacher_id) REFERENCES app_users(id)
);
CREATE INDEX ix_teaching_classes_teacher ON teaching_classes(teacher_id);
CREATE TABLE teaching_class_members (
 class_id VARCHAR(36) NOT NULL, student_id VARCHAR(36) NOT NULL,
 PRIMARY KEY(class_id,student_id),
 FOREIGN KEY(class_id) REFERENCES teaching_classes(id),
 FOREIGN KEY(student_id) REFERENCES app_users(id)
);
CREATE INDEX ix_teaching_class_members_student ON teaching_class_members(student_id);
ALTER TABLE teaching_tasks ADD COLUMN class_id VARCHAR(36);
ALTER TABLE teaching_tasks ADD FOREIGN KEY (class_id) REFERENCES teaching_classes(id);
ALTER TABLE teaching_tasks ADD COLUMN requires_submission BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE teaching_submissions (
 task_id VARCHAR(36) PRIMARY KEY, content VARCHAR(5000) NOT NULL,
 submitted_at VARCHAR(35) NOT NULL, score INT, feedback VARCHAR(2000), graded_at VARCHAR(35),
 FOREIGN KEY(task_id) REFERENCES teaching_tasks(id),
 CHECK (score BETWEEN 0 AND 100)
);

INSERT INTO sys_permissions VALUES
 ('teacher:class:manage','创建和查看自己的班级'),
 ('student:class:manage','加入和退出自己的班级'),
 ('student:task:submit','提交自己的作业'),
 ('teacher:task:grade','批改自己布置的作业');
INSERT INTO sys_role_permissions VALUES
 ('TEACHER','teacher:class:manage'),
 ('STUDENT','student:class:manage'),
 ('STUDENT','student:task:submit'),
 ('TEACHER','teacher:task:grade');
