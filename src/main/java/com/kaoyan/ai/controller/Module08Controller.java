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
public class Module08Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module08Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op057：分页查询模考试卷 */
  @GetMapping("/api/v1/mock-exams/papers")
  public ResponseEntity<?> op057(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op057", paths, body, request);
  }

  /** op058：开始模考 */
  @PostMapping("/api/v1/mock-exams/sessions")
  public ResponseEntity<?> op058(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op058", paths, body, request);
  }

  /** op059：恢复本人模考会话与已存答案 */
  @GetMapping("/api/v1/mock-exams/sessions/{id}")
  public ResponseEntity<?> op059(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op059", paths, body, request);
  }

  /** op060：自动保存单题答案 */
  @PutMapping("/api/v1/mock-exams/sessions/{id}/answers/{qid}")
  public ResponseEntity<?> op060(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op060", paths, body, request);
  }

  /** op061：交卷并异步评分 */
  @PostMapping("/api/v1/mock-exams/sessions/{id}/submit")
  public ResponseEntity<?> op061(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op061", paths, body, request);
  }

  /** op062：查询模考成绩与评分状态 */
  @GetMapping("/api/v1/mock-exams/sessions/{id}/result")
  public ResponseEntity<?> op062(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op062", paths, body, request);
  }

  /** op063：查询同试卷版本匿名排名 */
  @GetMapping("/api/v1/mock-exams/papers/{id}/ranking")
  public ResponseEntity<?> op063(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op063", paths, body, request);
  }

  /** op064：考前估分与冲刺策略 */
  @PostMapping("/api/v1/mock-exams/estimates")
  public ResponseEntity<?> op064(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op064", paths, body, request);
  }
}
