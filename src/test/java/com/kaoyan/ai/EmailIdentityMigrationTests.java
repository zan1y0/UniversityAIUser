package com.kaoyan.ai;

import static com.kaoyan.ai.common.Jsons.id;
import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EmailIdentityMigrationTests {
  @Test void migratesExistingV12AccountsWithoutChangingRelationshipsOrPrivateData() {
    String url = "jdbc:h2:mem:identity-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new DriverManagerDataSource(url,"sa","");
    Flyway.configure().dataSource(ds).locations("classpath:db/migration/h2").target("12").load().migrate();
    var db = new JdbcTemplate(ds);
    String teacher = id(), student = id(), classroom = id(), task = id(), conversation = id();
    String stamp = "2026-11-12T12:00:00Z";
    db.update("INSERT INTO app_users(id,login_name,display_name,password_hash) VALUES(?,?,?,?)",
        teacher,"legacy-teacher","老师","!TEACHER_LOGIN_ONLY");
    db.update("INSERT INTO teacher_accounts VALUES(?,?,?)",teacher,"legacy-teacher","legacy-teacher-hash");
    db.update("INSERT INTO app_users(id,login_name,display_name,password_hash) VALUES(?,?,?,?)",
        student,"legacy-student","学生","legacy-student-hash");
    db.update("INSERT INTO sys_user_roles VALUES(?,'STUDENT'),(?,'TEACHER')",student,teacher);
    db.update("INSERT INTO teacher_students VALUES(?,?,'active')",teacher,student);
    db.update("INSERT INTO teaching_classes VALUES(?,?,?,?)",classroom,teacher,"TESTCLASS","原班级");
    db.update("INSERT INTO teaching_class_members VALUES(?,?)",classroom,student);
    db.update("INSERT INTO teaching_publications(id,teacher_id,created_at) VALUES(?,?,?)",task,teacher,stamp);
    db.update("""
        INSERT INTO teaching_tasks(id,teacher_id,student_id,title,description,deadline,status,created_at,class_id,publication_id)
        VALUES(?,?,?,'旧教学任务','测试',?,'pending',?,?,?)
        """,task,teacher,student,stamp,stamp,classroom,task);
    db.update("INSERT INTO teaching_submissions(task_id,content,submitted_at) VALUES(?, '旧作业',?)",task,stamp);
    db.update("INSERT INTO teaching_submission_attachments VALUES(?,'original.txt',1,?)",task,new byte[]{42});
    db.update("INSERT INTO conversations(id,owner_id,version,payload) VALUES(?,?,1,'{}')",conversation,student);

    Flyway.configure().dataSource(ds).locations("classpath:db/migration/h2").load().migrate();

    assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM app_users WHERE email IS NULL AND account_status='PENDING_ACTIVATION'",Integer.class));
    assertEquals("legacy-teacher-hash",db.queryForObject("SELECT password_hash FROM teacher_accounts WHERE user_id=?",String.class,teacher));
    assertEquals("TEACHER",db.queryForObject("SELECT role_code FROM sys_user_roles WHERE user_id=?",String.class,teacher));
    assertEquals(classroom,db.queryForObject("SELECT class_id FROM teaching_class_members WHERE student_id=?",String.class,student));
    assertEquals(teacher,db.queryForObject("SELECT teacher_id FROM teacher_students WHERE student_id=?",String.class,student));
    assertEquals("旧作业",db.queryForObject("SELECT content FROM teaching_submissions WHERE task_id=?",String.class,task));
    assertArrayEquals(new byte[]{42},db.queryForObject("SELECT file_content FROM teaching_submission_attachments WHERE task_id=?",byte[].class,task));
    assertEquals(student,db.queryForObject("SELECT owner_id FROM conversations WHERE id=?",String.class,conversation));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_roles WHERE role_code='ADMIN'",Integer.class));
  }
}
