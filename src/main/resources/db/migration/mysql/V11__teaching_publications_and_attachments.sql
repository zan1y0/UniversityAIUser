CREATE TABLE teaching_publications (
 id VARCHAR(36) PRIMARY KEY,
 teacher_id VARCHAR(36) NOT NULL,
 created_at VARCHAR(35) NOT NULL,
 FOREIGN KEY (teacher_id) REFERENCES app_users(id)
);
ALTER TABLE teaching_tasks ADD COLUMN publication_id VARCHAR(36);
INSERT INTO teaching_publications(id,teacher_id,created_at)
 SELECT id,teacher_id,created_at FROM teaching_tasks;
UPDATE teaching_tasks SET publication_id=id;
ALTER TABLE teaching_tasks ADD FOREIGN KEY (publication_id) REFERENCES teaching_publications(id);
CREATE INDEX ix_teaching_tasks_publication ON teaching_tasks(publication_id);

CREATE TABLE teaching_submission_attachments (
 task_id VARCHAR(36) PRIMARY KEY,
 file_name VARCHAR(255) NOT NULL,
 size_bytes INT NOT NULL,
 file_content LONGBLOB NOT NULL,
 FOREIGN KEY (task_id) REFERENCES teaching_submissions(task_id)
);
ALTER TABLE teaching_tasks MODIFY COLUMN publication_id VARCHAR(36) NOT NULL;
