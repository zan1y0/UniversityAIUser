package com.kaoyan.ai.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RuleMasteryPredictor {
  public static final String VERSION = "empirical-correct-rate-v1";

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
