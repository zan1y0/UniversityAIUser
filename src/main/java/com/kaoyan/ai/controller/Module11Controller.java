package com.kaoyan.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.contract.ApiExecutor;
import com.kaoyan.ai.jobs.EventService;
import com.kaoyan.ai.storage.FileService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class Module11Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module11Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op075：分页查询政策和院校通知 */
  @GetMapping("/api/v1/news/notices")
  public ResponseEntity<?> op075(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op075", paths, body, request);
  }

  /** op076：查看政策版本差异 */
  @GetMapping("/api/v1/news/notices/{id}/changes")
  public ResponseEntity<?> op076(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op076", paths, body, request);
  }

  /** op077：获取本人关注范围 */
  @GetMapping("/api/v1/news/follows")
  public ResponseEntity<?> op077(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op077", paths, body, request);
  }

  /** op078：更新院校政策关注范围 */
  @PutMapping("/api/v1/news/follows")
  public ResponseEntity<?> op078(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op078", paths, body, request);
  }

  /** op079：获取报名确认准考证初试日期 */
  @GetMapping("/api/v1/news/milestones")
  public ResponseEntity<?> op079(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op079", paths, body, request);
  }

  /** op080：分页获取本人通知与补取游标 */
  @GetMapping("/api/v1/news/notifications")
  public ResponseEntity<?> op080(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op080", paths, body, request);
  }

  /** op081：标记通知已读 */
  @PostMapping("/api/v1/news/notifications/{id}/read")
  public ResponseEntity<?> op081(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op081", paths, body, request);
  }
}
