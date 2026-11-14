package com.kaoyan.ai.api.controller;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.kaoyan.ai.api.contract.ContractRegistry;
import com.kaoyan.ai.service.auth.SessionService;
import com.kaoyan.ai.service.task.StudySupervisionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StudyModeController {
  private final SessionService sessions;
  private final StudySupervisionService study;
  private final ContractRegistry contract;

  public StudyModeController(SessionService sessions, StudySupervisionService study, ContractRegistry contract) {
    this.sessions = sessions;
    this.study = study;
    this.contract = contract;
  }

  private void validate(String op, HttpServletRequest request, JsonNode body, Map<String, String> path) {
    contract.request(op, body, path, request);
    contract.query(op, request);
  }

  private JsonNode result(String op, HttpServletRequest request, JsonNode data) {
    var value = obj("code", "OK", "message", "操作成功", "traceId", request.getAttribute("traceId"), "data", data);
    contract.response(op, value);
    return value;
  }

  @GetMapping("/study-mode")
  public JsonNode mode(HttpServletRequest request) {
    validate("op097", request, NullNode.instance, Map.of());
    return result("op097", request, study.settings(sessions.user(request)));
  }

  @PutMapping("/study-mode")
  public JsonNode save(@RequestBody JsonNode body, HttpServletRequest request) {
    validate("op098", request, body, Map.of());
    return result("op098", request, study.saveMode(sessions.user(request), body.path("mode").asText()));
  }

  @GetMapping("/study-supervision")
  public JsonNode state(HttpServletRequest request) {
    String date = contract.query("op099", request).path("date").asText("");
    return result("op099", request, study.state(sessions.user(request), date.isEmpty() ? today() : LocalDate.parse(date)));
  }

  @PutMapping("/study-supervision/tasks/{taskId}")
  public JsonNode act(@PathVariable String taskId, @RequestBody JsonNode body, HttpServletRequest request) {
    validate("op100", request, body, Map.of("taskId", taskId));
    return result("op100", request, study.act(sessions.user(request), taskId, body));
  }
}
