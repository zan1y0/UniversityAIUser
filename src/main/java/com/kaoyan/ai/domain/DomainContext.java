package com.kaoyan.ai.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record DomainContext(String user, Map<String, String> paths, JsonNode query, JsonNode body) {
  public String path(String name) {
    return paths.get(name);
  }

  public String q(String name) {
    return query.path(name).asText();
  }

  public String b(String name) {
    return body.path(name).asText();
  }

  public int integer(String name) {
    return body.path(name).asInt();
  }
}
