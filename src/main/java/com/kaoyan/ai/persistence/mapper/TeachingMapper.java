package com.kaoyan.ai.persistence.mapper;
import com.kaoyan.ai.persistence.entity.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;
public interface TeachingMapper {
  List<UserAccount> teachers();
  List<UserAccount> students(@Param("teacher") String teacher);
  List<String> connectedTeachers(@Param("student") String student);
  int related(@Param("teacher") String teacher, @Param("student") String student);
  int activate(@Param("teacher") String teacher, @Param("student") String student);
  int connect(@Param("teacher") String teacher, @Param("student") String student);
  int disconnect(@Param("teacher") String teacher, @Param("student") String student);
  boolean hasRelationRow(@Param("teacher") String teacher, @Param("student") String student);
  void insertClass(TeachingClass teachingClass);
  TeachingClass lockClassByCode(@Param("code") String code);
  TeachingClass lockClassById(@Param("id") String id);
  List<TeachingClass> teacherClasses(@Param("user") String user);
  List<TeachingClass> studentClasses(@Param("user") String user);
  List<UserAccount> classStudents(@Param("id") String id);
  boolean ownsClass(@Param("id") String id, @Param("teacher") String teacher);
  boolean classMember(@Param("id") String id, @Param("student") String student);
  void joinClass(@Param("id") String id, @Param("student") String student);
  int leaveClass(@Param("id") String id, @Param("student") String student);
  void insertRelationAnchor(@Param("teacher") String teacher, @Param("student") String student);
  void insertPublication(@Param("id") String id, @Param("teacher") String teacher, @Param("createdAt") String createdAt);
  int insertTask(TeachingTask task);
  TeachingTask task(@Param("id") String id);
  List<TeachingTask> teacherTasks(@Param("user") String user);
  List<TeachingTask> studentTasks(@Param("user") String user);
  TeachingSubmission submission(@Param("id") String id);
  void insertSubmission(TeachingSubmission submission);
  TeachingAttachment attachment(@Param("id") String id);
  TeachingAttachmentContent attachmentContent(@Param("id") String id);
  void insertAttachment(@Param("id") String id, @Param("fileName") String fileName,
      @Param("size") int size, @Param("content") byte[] content);
  int gradeSubmission(TeachingSubmission submission);
  int transition(@Param("id") String id, @Param("teacher") String teacher, @Param("student") String student,
      @Param("version") int version, @Param("from") String from, @Param("status") String status,
      @Param("completedAt") String completedAt);
}
