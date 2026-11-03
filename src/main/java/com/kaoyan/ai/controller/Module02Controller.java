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
public class Module02Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module02Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op019：分页查询院校专业 */
  @GetMapping("/api/v1/schools/programs")
  public ResponseEntity<?> op019(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op019", paths, body, request);
  }

  /** op020：推荐院校专业 */
  @PostMapping("/api/v1/schools/recommendations")
  public ResponseEntity<?> op020(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op020", paths, body, request);
  }

  /** op021：查询院校历史指标 */
  @GetMapping("/api/v1/schools/programs/{id}/facts")
  public ResponseEntity<?> op021(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op021", paths, body, request);
  }

  /** op022：对比二至四个院校专业 */
  @PostMapping("/api/v1/schools/comparisons")
  public ResponseEntity<?> op022(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op022", paths, body, request);
  }

  /** op023：评估录取概率与风险 */
  @PostMapping("/api/v1/schools/evaluations")
  public ResponseEntity<?> op023(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op023", paths, body, request);
  }

  /** op024：获取本人择校评估 */
  @GetMapping("/api/v1/schools/evaluations/{id}")
  public ResponseEntity<?> op024(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op024", paths, body, request);
  }
}
