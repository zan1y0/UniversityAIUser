package com.kaoyan.ai.api.controller;

import static com.kaoyan.ai.common.Jsons.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.api.contract.ContractRegistry;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.service.auth.SessionService;
import com.kaoyan.ai.service.teaching.TeachingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TeachingController {
  private final TeachingService teaching;
  private final SessionService sessions;
  private final ContractRegistry contract;
  public TeachingController(TeachingService teaching,SessionService sessions,ContractRegistry contract) {
    this.teaching=teaching;this.sessions=sessions;this.contract=contract;
  }
  private String validate(String op,JsonNode body,Map<String,String> path,HttpServletRequest r) {
    contract.request(op,body==null?com.fasterxml.jackson.databind.node.NullNode.instance:body,path,r);
    contract.query(op,r);return sessions.user(r);
  }
  private ResponseEntity<?> result(String op,JsonNode data,HttpServletRequest r) {
    var result=obj("code","OK","message","操作成功","data",data,"traceId",r.getAttribute("traceId"));
    contract.response(op,result);return ResponseEntity.status(contract.status(op)).body(result);
  }
  @GetMapping("/api/v1/teacher/students")
  public ResponseEntity<?> students(HttpServletRequest r) {
    String user=validate("op087",null,Map.of(),r);return result("op087",teaching.students(user),r);
  }
  @GetMapping("/api/v1/teacher/students/{id}/summary")
  public ResponseEntity<?> summary(@PathVariable String id,HttpServletRequest r) {
    String user=validate("op088",null,Map.of("id",id),r);return result("op088",teaching.summary(user,id),r);
  }
  @GetMapping("/api/v1/teacher/tasks")
  public ResponseEntity<?> teacherTasks(HttpServletRequest r) {
    String user=validate("op089",null,Map.of(),r);return result("op089",teaching.teacherTasks(user),r);
  }
  @PostMapping("/api/v1/teacher/tasks")
  public ResponseEntity<?> create(@RequestBody JsonNode body,HttpServletRequest r) {
    String user=validate("op090",body,Map.of(),r);return result("op090",teaching.create(user,body),r);
  }
  @PatchMapping("/api/v1/teacher/tasks/{id}/cancel")
  public ResponseEntity<?> cancel(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest r) {
    String user=validate("op091",body,Map.of("id",id),r);return result("op091",teaching.cancel(user,id,body.path("version").asInt()),r);
  }
  @GetMapping("/api/v1/teaching/teachers")
  public ResponseEntity<?> teachers(HttpServletRequest r) {
    String user=validate("op092",null,Map.of(),r);return result("op092",teaching.teachers(user),r);
  }
  @PostMapping("/api/v1/teaching/teachers/{id}")
  public ResponseEntity<?> connect(@PathVariable String id,HttpServletRequest r) {
    String user=validate("op093",null,Map.of("id",id),r);return result("op093",teaching.connect(user,id,true),r);
  }
  @DeleteMapping("/api/v1/teaching/teachers/{id}")
  public ResponseEntity<?> disconnect(@PathVariable String id,HttpServletRequest r) {
    String user=validate("op094",null,Map.of("id",id),r);return result("op094",teaching.connect(user,id,false),r);
  }
  @GetMapping("/api/v1/teaching/tasks")
  public ResponseEntity<?> studentTasks(HttpServletRequest r) {
    String user=validate("op095",null,Map.of(),r);return result("op095",teaching.studentTasks(user),r);
  }
  @PatchMapping("/api/v1/teaching/tasks/{id}/complete")
  public ResponseEntity<?> complete(@PathVariable String id,@RequestBody JsonNode body,HttpServletRequest r) {
    String user=validate("op096",body,Map.of("id",id),r);return result("op096",teaching.complete(user,id,body.path("version").asInt()),r);
  }

  @GetMapping("/api/v1/teacher/classes")
  public ResponseEntity<?> teacherClasses(HttpServletRequest r) {
    String user = validate("op101", null, Map.of(), r);
    return result("op101", teaching.teacherClasses(user), r);
  }

  @PostMapping("/api/v1/teacher/classes")
  public ResponseEntity<?> createClass(@RequestBody JsonNode body, HttpServletRequest r) {
    String user = validate("op102", body, Map.of(), r);
    return result("op102", teaching.createClass(user, body), r);
  }

  @GetMapping("/api/v1/teaching/classes")
  public ResponseEntity<?> studentClasses(HttpServletRequest r) {
    String user = validate("op103", null, Map.of(), r);
    return result("op103", teaching.studentClasses(user), r);
  }

  @PostMapping("/api/v1/teaching/classes/join")
  public ResponseEntity<?> joinClass(@RequestBody JsonNode body, HttpServletRequest r) {
    String user = validate("op104", body, Map.of(), r);
    return result("op104", teaching.joinClass(user, body.path("code").asText()), r);
  }

  @PostMapping("/api/v1/teaching/tasks/{id}/submission")
  public ResponseEntity<?> submit(@PathVariable String id, @RequestBody JsonNode body, HttpServletRequest r) {
    String user = validate("op105", body, Map.of("id", id), r);
    return result("op105", teaching.submit(user, id, body), r);
  }

  @PatchMapping("/api/v1/teacher/tasks/{id}/grade")
  public ResponseEntity<?> grade(@PathVariable String id, @RequestBody JsonNode body, HttpServletRequest r) {
    String user = validate("op106", body, Map.of("id", id), r);
    return result("op106", teaching.grade(user, id, body), r);
  }

  @DeleteMapping("/api/v1/teaching/classes/{id}")
  public ResponseEntity<?> leaveClass(@PathVariable String id, HttpServletRequest r) {
    String user = validate("op107", null, Map.of("id", id), r);
    return result("op107", teaching.leaveClass(user, id), r);
  }

  @PostMapping("/api/v1/teacher/classes/{id}/tasks")
  public ResponseEntity<?> publishToClass(@PathVariable String id, @RequestBody JsonNode body, HttpServletRequest r) {
    String user = validate("op108", body, Map.of("id", id), r);
    return result("op108", teaching.publishToClass(user, id, body), r);
  }

  @GetMapping("/api/v1/teacher/classes/{id}/students")
  public ResponseEntity<?> classRoster(@PathVariable String id, HttpServletRequest r) {
    String user = validate("op109", null, Map.of("id", id), r);
    return result("op109", teaching.classRoster(user, id), r);
  }

  @PostMapping("/api/v1/teacher/tasks/publish")
  public ResponseEntity<?> publishBatch(@RequestBody JsonNode body, HttpServletRequest r) {
    String user = validate("op110", body, Map.of(), r);
    return result("op110", teaching.publishBatch(user, body), r);
  }

  @PostMapping("/api/v1/teaching/tasks/{id}/submission/attachment")
  public ResponseEntity<?> submitAttachment(@PathVariable String id, @RequestParam(defaultValue = "") String version,
      @RequestParam(defaultValue = "") String content, @RequestParam(required = false) MultipartFile file, HttpServletRequest r)
      throws IOException {
    contract.request("op111", com.fasterxml.jackson.databind.node.NullNode.instance, Map.of("id", id), r);
    int taskVersion;
    try {
      taskVersion = Integer.parseInt(version);
    } catch (NumberFormatException e) {
      throw new ApiException(400, "INVALID_REQUEST", "作业版本无效");
    }
    if (file == null) throw new ApiException(400, "INVALID_REQUEST", "请选择作业附件");
    return result("op111", teaching.submitAttachment(sessions.user(r), id, taskVersion, content, file), r);
  }

  @GetMapping("/api/v1/teaching/tasks/{id}/submission/attachment")
  public ResponseEntity<byte[]> studentAttachment(@PathVariable String id, HttpServletRequest r) {
    return attachment("op112", id, false, r);
  }

  @GetMapping("/api/v1/teacher/tasks/{id}/submission/attachment")
  public ResponseEntity<byte[]> teacherAttachment(@PathVariable String id, HttpServletRequest r) {
    return attachment("op113", id, true, r);
  }

  private ResponseEntity<byte[]> attachment(String operation, String id, boolean teacher, HttpServletRequest r) {
    String user = validate(operation, null, Map.of("id", id), r);
    var file = teaching.downloadAttachment(user, id, teacher);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
        .body(file.bytes());
  }
}
