package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.infrastructure.ai.MasteryPredictor;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RuleMasteryPredictor implements MasteryPredictor {
  public static final String VERSION = "empirical-correct-rate-v1";

  @Override
  public Optional<JsonNode> predict(JsonNode input) {
    List<ObjectNode> sequence = new ArrayList<>();
    for (JsonNode row : input.path("history")) {
      JsonNode correct = row.path("correct");
      if (!correct.isIntegralNumber() || (correct.asInt() != 0 && correct.asInt() != 1)) continue;
      sequence.add(obj("knowledgeId", row.path("knowledgeId").asText("__unknown__"),
          "correct", correct.asInt() == 1));
    }
    var evidence = summarize(sequence);
    Set<String> ids = new TreeSet<>(evidence.keySet());
    input.path("candidates").forEach(id -> ids.add(id.asText()));
    var items = arr();
    for (String id : ids) {
      var observed = evidence.getOrDefault(id, new Evidence());
      items.add(obj("knowledgeId", id, "mastery", observed.mastery(), "sampleCount", observed.count(),
          "stddev", null, "weight", null, "source", "rule"));
    }
    return Optional.of(obj("modelVersion", VERSION, "knowledgeMastery", items,
        "recommendedQuestions", arr()));
  }

  @Override public String version() { return VERSION; }
  @Override public boolean enabled() { return true; }

  public Map<String, Evidence> summarize(List<ObjectNode> sequence) {
    Map<String, Evidence> result = new HashMap<>();
    for (ObjectNode row : sequence) {
      if (!row.path("correct").isBoolean()) continue;
      result.computeIfAbsent(row.path("knowledgeId").asText(), key -> new Evidence())
          .record(row.path("correct").asBoolean());
    }
    return result;
  }

  public static final class Evidence {
    private int count;
    private int correct;
    private final Deque<Boolean> recent = new ArrayDeque<>(5);

    private void record(boolean answer) {
      count++;
      if (answer) correct++;
      if (recent.size() == 5) recent.removeFirst();
      recent.addLast(answer);
    }

    public int count() { return count; }
    public Double mastery() { return count == 0 ? null : (double) correct / count; }
    public int recentCount() { return recent.size(); }
    public long recentCorrect() { return recent.stream().filter(Boolean::booleanValue).count(); }
  }
}
