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
public class Module01Controller {
  private final ApiExecutor api;
  private final FileService files;
  private final EventService events;

  public Module01Controller(ApiExecutor api, FileService files, EventService events) {
    this.api = api;
    this.files = files;
    this.events = events;
  }

  /** op011：获取数字人形象与动作素材 */
  @GetMapping("/api/v1/avatar/assets")
  public ResponseEntity<?> op011(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op011", paths, body, request);
  }

  /** op012：获取数字人偏好 */
  @GetMapping("/api/v1/avatar/preferences")
  public ResponseEntity<?> op012(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op012", paths, body, request);
  }

  /** op013：更新形象模式声音与提醒时间 */
  @PutMapping("/api/v1/avatar/preferences")
  public ResponseEntity<?> op013(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op013", paths, body, request);
  }

  /** op014：文字或语音互动 */
  @PostMapping("/api/v1/avatar/interactions")
  public ResponseEntity<?> op014(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op014", paths, body, request);
  }

  /** op015：每日打卡 */
  @PostMapping("/api/v1/avatar/checkins")
  public ResponseEntity<?> op015(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op015", paths, body, request);
  }

  /** op016：获取督学状态与鼓励 */
  @GetMapping("/api/v1/avatar/supervision")
  public ResponseEntity<?> op016(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op016", paths, body, request);
  }

  /** op017：获取督学提醒设置 */
  @GetMapping("/api/v1/avatar/reminders")
  public ResponseEntity<?> op017(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op017", paths, body, request);
  }

  /** op018：更新督学提醒设置 */
  @PutMapping("/api/v1/avatar/reminders")
  public ResponseEntity<?> op018(
      @PathVariable Map<String, String> paths,
      @RequestBody(required = false) JsonNode body,
      HttpServletRequest request) {
    return api.execute("op018", paths, body, request);
  }
}
