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
public class Module03Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module03Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op025：公共课知识体系 */
  @GetMapping("/api/v1/public-courses/knowledge")
  public ResponseEntity<?> op025(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op025", paths, body, request);
  }

  /** op026：分层讲解知识点 */
  @PostMapping("/api/v1/public-courses/explanations")
  public ResponseEntity<?> op026(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op026", paths, body, request);
  }

  /** op027：高频易错及背诵考点 */
  @GetMapping("/api/v1/public-courses/exam-points")
  public ResponseEntity<?> op027(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op027", paths, body, request);
  }

  /** op028：英语词汇长难句作文辅导 */
  @PostMapping("/api/v1/public-courses/english/tasks")
  public ResponseEntity<?> op028(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op028", paths, body, request);
  }

  /** op029：政治大纲预测背诵时政辅导 */
  @PostMapping("/api/v1/public-courses/politics/tasks")
  public ResponseEntity<?> op029(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op029", paths, body, request);
  }

  /** op030：数学推导题型与解题指导 */
  @PostMapping("/api/v1/public-courses/math/tasks")
  public ResponseEntity<?> op030(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op030", paths, body, request);
  }
}
