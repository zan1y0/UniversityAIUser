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
public class Module09Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module09Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op065：创建专业英语压力复试 */
  @PostMapping("/api/v1/interviews")
  public ResponseEntity<?> op065(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op065", paths, body, request);
  }

  /** op066：提交复试回答并生成追问 */
  @PostMapping("/api/v1/interviews/{id}/turns")
  public ResponseEntity<?> op066(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op066", paths, body, request);
  }

  /** op067：结束复试并生成总结 */
  @PostMapping("/api/v1/interviews/{id}/finish")
  public ResponseEntity<?> op067(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op067", paths, body, request);
  }

  /** op068：查询复试总结 */
  @GetMapping("/api/v1/interviews/{id}/result")
  public ResponseEntity<?> op068(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op068", paths, body, request);
  }

  /** op069：自我介绍批改 */
  @PostMapping("/api/v1/interviews/self-introduction-reviews")
  public ResponseEntity<?> op069(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op069", paths, body, request);
  }

  /** op070：查询复试礼仪与流程 */
  @GetMapping("/api/v1/interviews/guidance")
  public ResponseEntity<?> op070(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op070", paths, body, request);
  }
}
