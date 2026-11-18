package com.kaoyan.ai.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

public interface MasteryPredictor {
  Optional<JsonNode> predict(JsonNode input);
  String version();
  boolean enabled();
}
