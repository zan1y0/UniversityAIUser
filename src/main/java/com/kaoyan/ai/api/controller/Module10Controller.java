package com.kaoyan.ai.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.api.contract.ApiExecutor;
import com.kaoyan.ai.service.task.EventService;
import com.kaoyan.ai.infrastructure.storage.FileService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class Module10Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module10Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op071：记录学习事实 */
  @PostMapping("/api/v1/analytics/study-events")
  public ResponseEntity<?> op071(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op071", paths, body, request);
  }

  /** op072：学习数据总览与曲线 */
  @GetMapping("/api/v1/analytics/dashboard")
  public ResponseEntity<?> op072(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op072", paths, body, request);
  }

  /** op073：薄弱知识点雷达 */
  @GetMapping("/api/v1/analytics/weakness-radar")
  public ResponseEntity<?> op073(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op073", paths, body, request);
  }

  /** op074：本人学习周报 */
  @GetMapping("/api/v1/analytics/weekly-reports")
  public ResponseEntity<?> op074(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op074", paths, body, request);
  }

  @GetMapping("/api/v1/analytics/mastery-estimates")
  public ResponseEntity<?> masteryEstimates(HttpServletRequest request) {
    return api.execute("op139", Map.of(), null, request);
  }

  @GetMapping("/api/v1/analytics/recommendations")
  public ResponseEntity<?> recommendations(HttpServletRequest request) {
    return api.execute("op140", Map.of(), null, request);
  }

  @GetMapping("/api/v1/analytics/training-sequence")
  public ResponseEntity<?> trainingSequence(HttpServletRequest request) {
    return api.execute("op141", Map.of(), null, request);
  }
}
