package com.kaoyan.ai.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpMasteryPredictor implements MasteryPredictor, ApplicationRunner {
  private static final Logger LOG = LoggerFactory.getLogger(HttpMasteryPredictor.class);
  private final JdbcTemplate jdbc;
  private final RestClient client;
  private final String version;
  private final String previousVersion;
  private final boolean enabled;

  @Autowired
  public HttpMasteryPredictor(JdbcTemplate jdbc,
      @Value("${ai.kt.enabled:false}") boolean enabled,
      @Value("${ai.kt.base-url:}") String baseUrl,
      @Value("${ai.kt.timeout-ms:800}") int timeout,
      @Value("${ai.kt.model-version:kt-gkt-v1}") String version,
      @Value("${ai.kt.previous-model-version:}") String previousVersion) {
    this(jdbc, enabled, version, previousVersion, enabled ? connect(baseUrl, timeout) : null);
  }

  HttpMasteryPredictor(JdbcTemplate jdbc, boolean enabled, String version, String previousVersion,
      RestClient client) {
    this.jdbc = jdbc;
    this.version = version;
    this.previousVersion = previousVersion;
    this.enabled = enabled;
    this.client = client;
  }

  private static RestClient connect(String baseUrl, int timeout) {
    try {
      URI url = URI.create(baseUrl);
      if (url.getHost() != null && ("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, timeout));
        factory.setReadTimeout(Math.max(1, timeout));
        return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
      }
    } catch (IllegalArgumentException ignored) {
      // 配置错误按外部服务不可用处理。
    }
    LOG.warn("知识追踪模型地址无效，启用本地降级");
    return null;
  }

  @Override
  public Optional<JsonNode> predict(JsonNode input) {
    if (client == null) return Optional.empty();
    try {
      JsonNode output = client.post().uri("/predict").contentType(MediaType.APPLICATION_JSON)
          .body(input).retrieve().body(JsonNode.class);
      if (output != null && output.isObject() && version.equals(output.path("modelVersion").asText())
          && output.path("items").isArray()) return Optional.of(output);
    } catch (RestClientException e) {
      LOG.debug("知识追踪模型调用失败 modelVersion={} error={}", version, e.getClass().getSimpleName());
    }
    return Optional.empty();
  }

  @Override
  public String version() {
    return version;
  }

  @Override
  public boolean enabled() {
    return enabled;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    register(previousVersion);
    register(version);
  }

  private void register(String algorithmVersion) {
    if (algorithmVersion.isBlank()) return;
    try {
      jdbc.update("INSERT INTO kt_model_versions (algorithm_version) VALUES (?)", algorithmVersion);
    } catch (DuplicateKeyException ignored) {
      // 多实例同时启动时复用已有登记。
    }
  }
}
