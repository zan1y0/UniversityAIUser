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
public class Module04Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module04Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op031：获取专业课学科配置 */
  @GetMapping("/api/v1/professional-courses/subjects")
  public ResponseEntity<?> op031(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op031", paths, body, request);
  }

  /** op032：专业课知识体系 */
  @GetMapping("/api/v1/professional-courses/knowledge")
  public ResponseEntity<?> op032(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op032", paths, body, request);
  }

  /** op033：参考书章节讲解 */
  @PostMapping("/api/v1/professional-courses/book-explanations")
  public ResponseEntity<?> op033(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op033", paths, body, request);
  }

  /** op034：专业课历年真题 */
  @GetMapping("/api/v1/professional-courses/past-questions")
  public ResponseEntity<?> op034(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op034", paths, body, request);
  }

  /** op035：专业课真题分层讲解 */
  @PostMapping("/api/v1/professional-courses/explanations")
  public ResponseEntity<?> op035(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op035", paths, body, request);
  }

  /** op036：专业课考点预测 */
  @PostMapping("/api/v1/professional-courses/predictions")
  public ResponseEntity<?> op036(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op036", paths, body, request);
  }
}
