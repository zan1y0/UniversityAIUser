CREATE TABLE sys_roles (role_code VARCHAR(20) PRIMARY KEY, role_name VARCHAR(50) NOT NULL);
CREATE TABLE sys_permissions (permission_code VARCHAR(80) PRIMARY KEY, permission_name VARCHAR(80) NOT NULL);
CREATE TABLE sys_user_roles (
 user_id VARCHAR(36) NOT NULL, role_code VARCHAR(20) NOT NULL,
 PRIMARY KEY(user_id,role_code), FOREIGN KEY(user_id) REFERENCES app_users(id),
 FOREIGN KEY(role_code) REFERENCES sys_roles(role_code)
);
CREATE TABLE sys_role_permissions (
 role_code VARCHAR(20) NOT NULL, permission_code VARCHAR(80) NOT NULL,
 PRIMARY KEY(role_code,permission_code), FOREIGN KEY(role_code) REFERENCES sys_roles(role_code),
 FOREIGN KEY(permission_code) REFERENCES sys_permissions(permission_code)
);
CREATE TABLE teacher_students (
 teacher_id VARCHAR(36) NOT NULL, student_id VARCHAR(36) NOT NULL, status VARCHAR(20) NOT NULL,
 PRIMARY KEY(teacher_id,student_id), FOREIGN KEY(teacher_id) REFERENCES app_users(id),
 FOREIGN KEY(student_id) REFERENCES app_users(id)
);
CREATE TABLE teaching_tasks (
 id VARCHAR(36) PRIMARY KEY, teacher_id VARCHAR(36) NOT NULL, student_id VARCHAR(36) NOT NULL,
 title VARCHAR(100) NOT NULL, description VARCHAR(2000) NOT NULL, deadline VARCHAR(35) NOT NULL,
 status VARCHAR(20) NOT NULL, version INT NOT NULL DEFAULT 1,
 created_at VARCHAR(35) NOT NULL, completed_at VARCHAR(35),
 FOREIGN KEY(teacher_id,student_id) REFERENCES teacher_students(teacher_id,student_id)
);
CREATE INDEX ix_teaching_tasks_student ON teaching_tasks(student_id,created_at);
CREATE INDEX ix_teaching_tasks_teacher ON teaching_tasks(teacher_id,created_at);
INSERT INTO sys_roles VALUES ('STUDENT','学生'),('TEACHER','老师');
INSERT INTO sys_permissions VALUES
 ('student:study:use','使用个人学习功能'),('student:analytics:read','读取个人统计'),
 ('student:teacher:manage','管理自己的指导关系'),('student:task:read','读取个人教学任务'),
 ('student:task:complete','完成个人教学任务'),
 ('teacher:student:list','读取指导学生列表'),('teacher:analytics:read','读取指导学生统计'),
 ('teacher:task:create','布置教学任务'),('teacher:task:read','读取自己布置的任务'),
 ('teacher:task:cancel','取消自己布置的任务');
INSERT INTO sys_role_permissions(role_code,permission_code)
 SELECT 'STUDENT',permission_code FROM sys_permissions WHERE permission_code LIKE 'student:%';
INSERT INTO sys_role_permissions(role_code,permission_code)
 SELECT 'TEACHER',permission_code FROM sys_permissions WHERE permission_code LIKE 'teacher:%';
INSERT INTO sys_user_roles(user_id,role_code) SELECT id,'STUDENT' FROM app_users;
