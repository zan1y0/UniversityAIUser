package com.kaoyan.ai.service.teaching;

import static com.kaoyan.ai.common.Jsons.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.entity.TeachingClass;
import com.kaoyan.ai.persistence.entity.TeachingSubmission;
import com.kaoyan.ai.persistence.entity.TeachingTask;
import com.kaoyan.ai.persistence.entity.UserAccount;
import com.kaoyan.ai.persistence.mapper.TeachingMapper;
import com.kaoyan.ai.persistence.repository.RecordRepository;
import com.kaoyan.ai.service.*;
import com.kaoyan.ai.service.auth.PermissionService;
import com.kaoyan.ai.service.task.EventService;
import java.time.*;
import java.util.*;
import java.io.IOException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TeachingService {
  public record SubmissionFile(String fileName, byte[] bytes) {}
  private record Publication(String id, String title, String description, String deadline, String createdAt,
      boolean requiresSubmission) {}
  private final TeachingMapper mapper;
  private final RecordRepository records;
  private final AnalyticsService analytics;
  private final PermissionService permissions;
  private final EventService events;

  public TeachingService(TeachingMapper mapper, RecordRepository records,
      AnalyticsService analytics, PermissionService permissions, EventService events) {
    this.mapper=mapper; this.records=records; this.analytics=analytics; this.permissions=permissions;
    this.events = events;
  }

  @PreAuthorize("hasAuthority('student:teacher:manage')")
  public JsonNode teachers(String student) {
    var connected=mapper.connectedTeachers(student);
    return obj("items",mapper.teachers().stream().map(t->obj("id",t.id(),"displayName",t.displayName(),
        "connected",connected.contains(t.id()))).toList());
  }

  @Transactional
  @PreAuthorize("hasAuthority('student:teacher:manage')")
  public JsonNode connect(String student,String teacher,boolean active) {
    require(!student.equals(teacher),"不能将自己设为指导老师");
    records.lockOwner(student);
    if(active) {
      permissions.requireRole(teacher,"TEACHER");
      if (mapper.hasRelationRow(teacher, student)) mapper.activate(teacher, student);
      else mapper.connect(teacher, student);
    } else mapper.disconnect(teacher,student);
    return obj("teacherId",teacher,"connected",active);
  }

  @PreAuthorize("hasAuthority('teacher:student:list')")
  public JsonNode students(String teacher) {
    return studentList(mapper.students(teacher));
  }

  @PreAuthorize("hasAuthority('teacher:student:list')")
  public JsonNode classRoster(String teacher, String classId) {
    if (!mapper.ownsClass(classId, teacher)) throw ApiException.missing();
    return studentList(mapper.classStudents(classId));
  }

  private JsonNode studentList(List<UserAccount> students) {
    return obj("items", students.stream().map(s -> obj("id", s.id(), "displayName", s.displayName(),
        "loginName", s.loginName())).toList());
  }

  @PreAuthorize("hasAuthority('teacher:class:manage')")
  public JsonNode teacherClasses(String teacher) {
    return classes(mapper.teacherClasses(teacher));
  }

  @PreAuthorize("hasAuthority('student:class:manage')")
  public JsonNode studentClasses(String student) {
    return classes(mapper.studentClasses(student));
  }

  private JsonNode classes(List<TeachingClass> classes) {
    return obj("items", classes.stream().map(c -> obj("id", c.id(), "name", c.name(),
        "code", c.code(), "teacherName", c.teacherName(), "memberCount", c.memberCount())).toList());
  }

  @Transactional
  @PreAuthorize("hasAuthority('teacher:class:manage')")
  public JsonNode createClass(String teacher, JsonNode body) {
    String name = body.path("name").asText().trim();
    require(!name.isEmpty(), "请输入班级名称");
    String code = body.path("code").asText().toUpperCase(Locale.ROOT);
    var classroom = new TeachingClass(id(), teacher, code, name, null, 0);
    try {
      mapper.insertClass(classroom);
    } catch (DuplicateKeyException e) {
      throw new ApiException(409, "CLASS_CODE_EXISTS", "该班级编号已被使用");
    }
    return obj("id", classroom.id());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('student:class:manage')")
  public JsonNode joinClass(String student, String code) {
    var classroom = mapper.lockClassByCode(code.toUpperCase(Locale.ROOT));
    if (classroom == null) throw ApiException.missing();
    String teacher = classroom.teacherId();
    require(!student.equals(teacher), "不能加入自己创建的班级");
    permissions.requireRole(teacher, "TEACHER");
    records.lockOwner(student);
    if (!mapper.hasRelationRow(teacher, student)) mapper.insertRelationAnchor(teacher, student);
    if (!mapper.classMember(classroom.id(), student)) mapper.joinClass(classroom.id(), student);
    return obj("id", classroom.id());
  }

  @Transactional
  @PreAuthorize("hasAuthority('student:class:manage')")
  public JsonNode leaveClass(String student, String id) {
    if (mapper.lockClassById(id) == null) throw ApiException.missing();
    records.lockOwner(student);
    if (mapper.leaveClass(id, student) == 0) throw ApiException.missing();
    return obj("id", id);
  }

  @PreAuthorize("hasAuthority('teacher:analytics:read')")
  @Transactional(readOnly=true)
  public JsonNode summary(String teacher,String student) {
    related(teacher,student);
    var to=today(); var from=to.minusDays(6);
    JsonNode statistics=analytics.execute("op072",new DomainContext(student,Map.of(),
        obj("from",from.toString(),"to",to.toString(),"timezone","Asia/Shanghai"),obj()));
    int total=0,completed=0;
    for(var plan:records.list("plans",student))for(var task:plan.path("tasks")) {
      total++;if(task.path("status").asText().equals("completed"))completed++;
    }
    var scores=records.list("exam_results",student).stream()
        .filter(e->e.path("scoringStatus").asText().equals("completed"))
        .map(e->obj("id",e.path("id"),"totalScore",e.path("totalScore"))).toList();
    return obj("studentId",student,"statistics",statistics,"planTaskCount",total,
        "completedPlanTaskCount",completed,"examScores",scores);
  }

  @PreAuthorize("hasAuthority('teacher:task:read')")
  public JsonNode teacherTasks(String teacher) {
    return obj("items",mapper.teacherTasks(teacher).stream()
        .filter(t->mapper.related(t.teacherId(),t.studentId())==1).map(this::view).toList());
  }

  @PreAuthorize("hasAuthority('student:task:read')")
  public JsonNode studentTasks(String student) {
    return obj("items",mapper.studentTasks(student).stream()
        .filter(t->mapper.related(t.teacherId(),t.studentId())==1).map(this::view).toList());
  }

  @Transactional
  @PreAuthorize("hasAuthority('teacher:task:create')")
  public JsonNode create(String teacher,JsonNode body) {
    String student=body.path("studentId").asText();
    records.lockOwner(student); related(teacher,student);
    return view(createTask(teacher, student, null, publish(teacher, body)));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('teacher:task:create')")
  public JsonNode publishToClass(String teacher, String id, JsonNode body) {
    var classroom = ownedClass(teacher, id);
    var students = mapper.classStudents(id);
    if (students.isEmpty()) throw ApiException.state("班级暂无学生，无法发布任务");
    var publication = publish(teacher, body);
    for (var student : students) createTask(teacher, student.id(), classroom, publication);
    return obj("assignedCount", students.size());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('teacher:task:create')")
  public JsonNode publishBatch(String teacher, JsonNode body) {
    var classes = new TreeMap<String, JsonNode>();
    for (var target : body.path("classes")) {
      if (classes.put(target.path("classId").asText(), target) != null)
        throw new ApiException(422, "VALIDATION_FAILED", "同一个班级只能选择一次");
    }
    var publication = publish(teacher, body);
    var assignedToClass = new HashSet<String>();
    int count = 0;
    for (var target : classes.entrySet()) {
      var classroom = ownedClass(teacher, target.getKey());
      var roster = mapper.classStudents(classroom.id()).stream().map(UserAccount::id).toList();
      var selected = new ArrayList<String>();
      if (target.getValue().has("studentIds")) {
        target.getValue().path("studentIds").forEach(student -> selected.add(student.asText()));
        if (!new HashSet<>(roster).containsAll(selected)) throw ApiException.state("班级成员已变化，请刷新后重新选择");
      } else selected.addAll(roster);
      if (selected.isEmpty()) throw ApiException.state(classroom.name() + "暂无可分配的学生");
      for (String student : selected) {
        createTask(teacher, student, classroom, publication);
        assignedToClass.add(student);
        count++;
      }
    }
    var individual = new TreeSet<String>();
    body.path("studentIds").forEach(student -> individual.add(student.asText()));
    individual.removeAll(assignedToClass);
    for (String student : individual) {
      records.lockOwner(student);
      related(teacher, student);
      createTask(teacher, student, null, publication);
      count++;
    }
    require(count > 0, "请选择发布对象");
    return obj("assignedCount", count);
  }

  private TeachingClass ownedClass(String teacher, String id) {
    var classroom = mapper.lockClassById(id);
    if (classroom == null || !classroom.teacherId().equals(teacher)) throw ApiException.missing();
    return classroom;
  }

  private Publication publish(String teacher, JsonNode body) {
    String title = body.path("title").asText().trim();
    require(!title.isBlank(), "请输入任务标题");
    Instant deadline;
    try { deadline = Instant.parse(body.path("deadline").asText()); }
    catch (DateTimeException e) { throw new ApiException(422,"VALIDATION_FAILED","截止时间格式不正确"); }
    require(deadline.isAfter(Instant.now()), "截止时间必须晚于当前时间");
    var publication = new Publication(id(), title, body.path("description").asText().trim(), deadline.toString(),
        now(), body.path("requiresSubmission").asBoolean());
    mapper.insertPublication(publication.id(), teacher, publication.createdAt());
    return publication;
  }

  private TeachingTask createTask(String teacher, String student, TeachingClass classroom, Publication publication) {
    var task = new TeachingTask(id(), teacher, student, publication.title(), publication.description(),
        publication.deadline(), "pending", 1, publication.createdAt(), null, classroom == null ? null : classroom.id(),
        publication.requiresSubmission(), publication.id());
    mapper.insertTask(task);
    String eventId = events.publish(student, TeachingNotice.TYPE, task.id(), task.version(), obj("taskId", task.id()));
    events.notify(student, TeachingNotice.TYPE, task.id(), TeachingNotice.title(task.title()),
        TeachingNotice.content(classroom == null ? null : classroom.name(),
            classroom == null ? null : classroom.code(), task.deadline()), eventId);
    return task;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('student:task:complete')")
  public JsonNode complete(String student,String id,int version) {
    var task=owned(id,student,false);
    if (task.requiresSubmission()) throw ApiException.state("请提交作业");
    transition(task, version, "pending", "completed", now());
    return view(mapper.task(id));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('teacher:task:cancel')")
  public JsonNode cancel(String teacher,String id,int version) {
    var task=owned(id,teacher,true);
    transition(task, version, "pending", "cancelled", null);
    return view(mapper.task(id));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('student:task:submit')")
  public JsonNode submit(String student, String id, JsonNode body) {
    var task = owned(id, student, false);
    String content = body.path("content").asText().trim();
    require(!content.isEmpty(), "请输入作业内容");
    saveSubmission(task, body.path("version").asInt(), content);
    return view(mapper.task(id));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('student:task:submit')")
  public JsonNode submitAttachment(String student, String id, int version, String content, MultipartFile file) throws IOException {
    var task = owned(id, student, false);
    require(version > 0 && content.length() <= 5000, "作业版本或正文长度不正确");
    require(!file.isEmpty() && file.getSize() <= 10 * 1024 * 1024, "附件不能为空且不能超过 10 MiB");
    String name = Objects.toString(file.getOriginalFilename(), "").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
    require(!name.isBlank() && name.length() <= 255 && !name.equals(".") && !name.equals(".."), "附件名称无效");
    byte[] bytes = file.getBytes();
    saveSubmission(task, version, content.trim());
    mapper.insertAttachment(id, name, bytes.length, bytes);
    return view(mapper.task(id));
  }

  private void saveSubmission(TeachingTask task, int version, String content) {
    if (!task.requiresSubmission()) throw ApiException.state("该任务无需提交作业");
    transition(task, version, "pending", "submitted", null);
    mapper.insertSubmission(new TeachingSubmission(task.id(), content, now(), null, null, null));
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyAuthority('teacher:task:read', 'student:task:read')")
  public SubmissionFile downloadAttachment(String user, String id, boolean teacher) {
    var task = taskForOwner(id, user, teacher);
    requireAccess(task);
    var metadata = mapper.attachment(id);
    if (metadata == null) throw ApiException.missing();
    return new SubmissionFile(metadata.fileName(), mapper.attachmentContent(id).bytes());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasAuthority('teacher:task:grade')")
  public JsonNode grade(String teacher, String id, JsonNode body) {
    var task = owned(id, teacher, true);
    String gradedAt = now();
    transition(task, body.path("version").asInt(), "submitted", "graded", gradedAt);
    if (mapper.gradeSubmission(new TeachingSubmission(id, null, null, body.path("score").asInt(),
        body.path("feedback").asText().trim(), gradedAt)) != 1)
      throw ApiException.state("该作业无法评分");
    return view(mapper.task(id));
  }

  private TeachingTask owned(String id,String user,boolean teacher) {
    var task = taskForOwner(id, user, teacher);
    records.lockOwner(task.studentId());
    requireAccess(task);
    return task;
  }
  private TeachingTask taskForOwner(String id, String user, boolean teacher) {
    var task=mapper.task(id);
    if(task==null || !(teacher?task.teacherId():task.studentId()).equals(user))throw ApiException.missing();
    return task;
  }
  private void requireAccess(TeachingTask task) {
    related(task.teacherId(),task.studentId());
    if (task.classId() != null && !mapper.classMember(task.classId(), task.studentId()))
      throw new ApiException(403, "FORBIDDEN", "该学生已退出此任务所属班级");
  }
  private void transition(TeachingTask task, int version, String from, String status, String completedAt) {
    if(mapper.transition(task.id(),task.teacherId(),task.studentId(),version,from,status,completedAt)!=1)
      throw new ApiException(409,"VERSION_CONFLICT","任务状态或版本已变化，请刷新");
  }
  private void related(String teacher,String student) {
    if(mapper.related(teacher,student)!=1)
      throw new ApiException(403,"FORBIDDEN","该学生尚未授权你作为指导老师");
  }
  private ObjectNode view(TeachingTask t) {
    TeachingSubmission submission = Set.of("submitted", "graded").contains(t.status()) ? mapper.submission(t.id()) : null;
    JsonNode submissionView = submission == null ? null : MAPPER.valueToTree(submission);
    if (submissionView instanceof ObjectNode node) node.set("attachment", MAPPER.valueToTree(mapper.attachment(t.id())));
    return obj("id",t.id(),"teacherId",t.teacherId(),"studentId",t.studentId(),"title",t.title(),
        "description",t.description(),"deadline",t.deadline(),"status",t.status(),"version",t.version(),
        "createdAt",t.createdAt(),"completedAt",t.completedAt(),"classId",t.classId(),
        "requiresSubmission",t.requiresSubmission(),"submission",submissionView,"publicationId",t.publicationId());
  }
}
