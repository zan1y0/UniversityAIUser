package com.kaoyan.ai.persistence.migration;

import static com.kaoyan.ai.common.Jsons.id;
import static com.kaoyan.ai.common.Jsons.obj;

import com.kaoyan.ai.service.teaching.TeachingNotice;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class BackfillTeachingNotices {
  private BackfillTeachingNotices() {}

  public static void migrate(Connection connection) throws SQLException {
    String missingNotices = """
        SELECT t.id,t.student_id,t.title,t.deadline,t.created_at,c.class_name,c.class_code
        FROM teaching_tasks t LEFT JOIN teaching_classes c ON c.id=t.class_id
        JOIN sys_user_roles student ON student.user_id=t.student_id AND student.role_code='STUDENT'
        JOIN sys_user_roles teacher ON teacher.user_id=t.teacher_id AND teacher.role_code='TEACHER'
        WHERE t.status <> 'cancelled'
          AND ((t.class_id IS NULL AND EXISTS (
            SELECT 1 FROM teacher_students ts
            WHERE ts.teacher_id=t.teacher_id AND ts.student_id=t.student_id AND ts.status='active'
          )) OR EXISTS (
            SELECT 1 FROM teaching_class_members m JOIN teaching_classes cls ON cls.id=m.class_id
            WHERE cls.teacher_id=t.teacher_id AND m.student_id=t.student_id
              AND (t.class_id IS NULL OR t.class_id=m.class_id)
          ))
          AND NOT EXISTS (
            SELECT 1 FROM notifications n WHERE n.owner_id=t.student_id
              AND n.payload LIKE '%"type":"teaching.task.assigned"%'
              AND n.payload LIKE CONCAT('%"resourceId":"',t.id,'"%')
          )
        ORDER BY t.created_at,t.id
        """;
    try (var query = connection.prepareStatement(missingNotices);
         var rows = query.executeQuery();
         var reserve = connection.prepareStatement(
             "INSERT INTO event_outbox(owner_id,event_type,payload) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS);
         var event = connection.prepareStatement("UPDATE event_outbox SET payload=? WHERE id=?");
         var notice = connection.prepareStatement("INSERT INTO notifications(id,owner_id,version,payload) VALUES (?,?,1,?)")) {
      while (rows.next()) {
        String task = rows.getString("id");
        String student = rows.getString("student_id");
        String createdAt = rows.getString("created_at");
        reserve.setString(1, student);
        reserve.setString(2, TeachingNotice.TYPE);
        reserve.setString(3, "{}");
        reserve.executeUpdate();
        long sequence;
        try (var keys = reserve.getGeneratedKeys()) {
          if (!keys.next()) throw new SQLException("Could not reserve teaching notification event");
          sequence = keys.getLong(1);
        }
        String eventId = Long.toString(sequence);
        event.setString(1, obj("eventId", eventId, "type", TeachingNotice.TYPE, "resourceId", task,
            "version", 1, "occurredAt", createdAt, "data", obj("taskId", task)).toString());
        event.setLong(2, sequence);
        event.executeUpdate();
        String noticeId = id();
        notice.setString(1, noticeId);
        notice.setString(2, student);
        notice.setString(3, obj("id", noticeId, "eventId", eventId, "type", TeachingNotice.TYPE,
            "title", TeachingNotice.title(rows.getString("title")),
            "content", TeachingNotice.content(rows.getString("class_name"), rows.getString("class_code"),
                rows.getString("deadline")), "resourceId", task, "read", false, "readAt", null,
            "createdAt", createdAt, "version", 1).toString());
        notice.executeUpdate();
      }
    }
  }
}
