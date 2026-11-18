package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.infrastructure.ai.MasteryPredictor;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeTracingService {
  private static final Logger LOG = LoggerFactory.getLogger(KnowledgeTracingService.class);
  private static final int CACHE_LIMIT = 512;
  private static final int MODEL_HISTORY = 250;
  private final CatalogService catalog;
  private final TrainingSequenceService training;
  private final MasteryPredictor predictor;
  private final RuleMasteryPredictor rules;
  private final Map<CacheKey, Result> cache = new LinkedHashMap<>(16, 0.75f, true);

  public KnowledgeTracingService(CatalogService catalog, TrainingSequenceService training,
      MasteryPredictor predictor, RuleMasteryPredictor rules) {
    this.catalog = catalog;
    this.training = training;
    this.predictor = predictor;
    this.rules = rules;
  }

  public JsonNode execute(String op, DomainContext context) {
    long start = System.nanoTime();
    if ("op141".equals(op)) {
      ObjectNode batch = training.export(context);
      LOG.info("knowledgeTracing operation={} latencyMs={} source=export schemaVersion={} pageSize={}",
          op, (System.nanoTime() - start) / 1_000_000, batch.path("schemaVersion").asText(),
          batch.path("items").size());
      return batch;
    }
    String subject = context.q("subjectId");
    CacheKey key = new CacheKey(context.user(), subject, predictor.version());
    Result result = cached(key);
    boolean hit = result != null;
    if (!hit) {
      result = calculate(context.user(), subject);
      synchronized (cache) {
        cache.put(key, result);
        if (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
      }
    }
    boolean recommendations = "op140".equals(op);
    LOG.info("knowledgeTracing operation={} latencyMs={} source={} fallbackReason={} modelVersion={} predictorVersion={} cacheHit={}",
        op, (System.nanoTime() - start) / 1_000_000,
        recommendations ? result.recommendationSource() : result.masterySource(),
        recommendations ? result.recommendationFallback() : result.masteryFallback(),
        recommendations ? result.recommendationVersion() : result.masteryVersion(), predictor.version(), hit);
    return obj("subjectId", subject, "asOf", result.asOf().toString(), "items",
        recommendations ? result.recommendations() : result.mastery());
  }

  private Result cached(CacheKey key) {
    synchronized (cache) {
      Result result = cache.get(key);
      if (result != null && !result.expiresAt().isAfter(Instant.now())) {
        cache.remove(key);
        return null;
      }
      return result;
    }
  }

  private Result calculate(String user, String subject) {
    var nodes = catalog.learningNodes(catalog.subject(subject));
    List<ObjectNode> questions = catalog.questions(subject, null, null);
    Instant asOf = Instant.now();
    var sequence = training.sequence(user, subject, asOf).stream()
        .filter(row -> row.path("correct").isBoolean()).toList();
    var evidence = rules.summarize(sequence);
    List<ObjectNode> candidates = questions.stream()
        .map(q -> obj("questionVersionId", q.get("versionId"), "knowledgeId", q.get("knowledgeId"),
            "knowledgeVersion", q.get("knowledgeVersion"), "difficultyGrade", q.get("difficultyGrade")))
        .toList();
    var prediction = predictor.predict(obj("subjectId", subject, "modelVersion", predictor.version(),
        "asOf", asOf.toString(), "nodes", nodes, "candidates", candidates,
        "sequence", sequence.subList(Math.max(0, sequence.size() - MODEL_HISTORY), sequence.size())));
    JsonNode output = prediction.orElseGet(() -> obj());
    Map<String, JsonNode> modelItems = new HashMap<>();
    for (JsonNode item : output.path("items")) {
      if (unit(item.path("mastery")) != null)
        modelItems.put(item.path("knowledgeId").asText(), item);
    }
    List<ObjectNode> mastery = new ArrayList<>();
    Map<String, ObjectNode> byKnowledge = new HashMap<>();
    for (JsonNode node : nodes) {
      String id = node.path("id").asText();
      var observed = evidence.getOrDefault(id, new RuleMasteryPredictor.Evidence());
      JsonNode item = modelItems.get(id);
      ObjectNode estimate = obj("knowledgeId", id, "sampleCount", observed.count(),
          "mastery", item == null ? observed.mastery() : unit(item.path("mastery")),
          "confidence", item == null ? null : unit(item.path("confidence")),
          "stddev", item == null ? null : unit(item.path("stddev")),
          "weight", item == null ? null : unit(item.path("weight")),
          "source", item == null ? "rule" : "model",
          "modelVersion", item == null ? RuleMasteryPredictor.VERSION : predictor.version());
      mastery.add(estimate);
      byKnowledge.put(id, estimate);
    }
    long modelCount = mastery.stream().filter(item -> "model".equals(item.path("source").asText())).count();
    String masterySource = modelCount == 0 ? "rule" : modelCount == mastery.size() ? "model" : "mixed";
    var ranking = rank(questions, output.path("recommendations"), evidence, byKnowledge);
    List<ObjectNode> recommendations = new ArrayList<>();
    Map<String, JsonNode> nodeIndex = new HashMap<>();
    for (JsonNode node : nodes) nodeIndex.put(node.path("id").asText(), node);
    for (ObjectNode question : ranking.questions().stream().limit(10).toList()) {
      String id = question.path("knowledgeId").asText();
      var estimate = byKnowledge.getOrDefault(id, obj());
      recommendations.add(obj("questionVersionId", question.get("versionId"), "knowledgeId", id,
          "confidence", estimate.get("confidence"), "stddev", estimate.get("stddev"),
          "weight", estimate.get("weight"), "source", ranking.source(), "modelVersion", ranking.version(),
          "reason", reason(id, nodeIndex.getOrDefault(id, obj()), evidence, byKnowledge, ranking.source())));
    }
    String unavailable = predictor.enabled() ? "model_unavailable_or_invalid" : "model_disabled";
    return new Result(asOf, Instant.now().plusSeconds(60), mastery, recommendations, masterySource,
        ranking.source(), modelCount == 0 ? RuleMasteryPredictor.VERSION : predictor.version(),
        ranking.version(), modelCount == 0 ? unavailable : modelCount < mastery.size() ? "partial_model_output" : "none",
        "model".equals(ranking.source()) ? "none" : prediction.isPresent() ? "no_valid_model_recommendations" : unavailable);
  }

  private Ranking rank(List<ObjectNode> questions, JsonNode recommended,
      Map<String, RuleMasteryPredictor.Evidence> evidence, Map<String, ObjectNode> estimates) {
    Map<String, ObjectNode> byVersion = new LinkedHashMap<>();
    for (ObjectNode question : questions) byVersion.put(question.path("versionId").asText(), question);
    List<ObjectNode> remote = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JsonNode version : recommended) {
      String id = version.asText();
      if (byVersion.containsKey(id) && seen.add(id)) remote.add(byVersion.get(id));
    }
    if (!remote.isEmpty()) return new Ranking(remote, "model", predictor.version());

    Comparator<ObjectNode> stable = Comparator.comparing(q -> q.path("versionId").asText());
    long total = evidence.values().stream().mapToLong(RuleMasteryPredictor.Evidence::count).sum();
    var eligible = questions.stream().filter(q -> evidence.containsKey(knowledgeId(q))).toList();
    if (!eligible.isEmpty()) {
      // 用作答错误率作为复习收益，置信上界让样本较少的知识点仍有机会被选中。
      var ranked = eligible.stream().sorted(Comparator.<ObjectNode>comparingDouble(q -> {
        var observed = evidence.get(knowledgeId(q));
        return observed.mastery() - Math.sqrt(2 * Math.log1p(total) / observed.count());
      }).thenComparing(stable)).toList();
      return new Ranking(ranked, "bandit", "review-ucb-v1");
    }
    var predicted = questions.stream().filter(q -> estimates.containsKey(knowledgeId(q))
        && estimates.get(knowledgeId(q)).hasNonNull("mastery")).toList();
    if (!predicted.isEmpty()) {
      return new Ranking(predicted.stream().sorted(Comparator.<ObjectNode>comparingDouble(
          q -> estimates.get(knowledgeId(q)).path("mastery").asDouble()).thenComparing(stable)).toList(),
          "greedy", "mastery-greedy-v1");
    }
    return new Ranking(questions.stream().sorted(stable).toList(), "rule", RuleMasteryPredictor.VERSION);
  }

  private ObjectNode reason(String id, JsonNode node, Map<String, RuleMasteryPredictor.Evidence> evidence,
      Map<String, ObjectNode> estimates, String source) {
    for (JsonNode prerequisite : node.path("prerequisiteIds")) {
      String prerequisiteId = prerequisite.asText();
      JsonNode estimate = estimates.getOrDefault(prerequisiteId, obj()).path("mastery");
      if (estimate.isNumber() && estimate.asDouble() < 0.6)
        return obj("code", "PREREQUISITE_WEAK", "message", "该题的先修知识点掌握度低于 60%",
            "prerequisiteId", prerequisiteId, "mastery", estimate);
    }
    var observed = evidence.get(id);
    if (observed != null && observed.recentCorrect() < observed.recentCount())
      return obj("code", "RECENT_ERRORS", "message", "该知识点近 " + observed.recentCount()
          + " 次答对 " + observed.recentCorrect() + " 次", "sampleCount", observed.recentCount(),
          "correctCount", observed.recentCorrect());
    if ("model".equals(source) || "greedy".equals(source))
      return obj("code", "MODEL_RANK", "message", "根据模型估计安排该知识点练习",
          "mastery", estimates.getOrDefault(id, obj()).get("mastery"));
    if (observed != null)
      return obj("code", "REVIEW", "message", "复习已练习的知识点", "sampleCount", observed.count());
    return obj("code", "COLD_START", "message", "该知识点暂无有效作答，推荐练习以建立基线");
  }

  private String knowledgeId(JsonNode question) {
    return question.path("knowledgeId").asText();
  }

  private Double unit(JsonNode node) {
    double value = node.asDouble(Double.NaN);
    return node.isNumber() && value >= 0 && value <= 1 ? value : null;
  }

  private record CacheKey(String user, String subject, String modelVersion) {}
  private record Ranking(List<ObjectNode> questions, String source, String version) {}
  private record Result(Instant asOf, Instant expiresAt, List<ObjectNode> mastery,
      List<ObjectNode> recommendations, String masterySource, String recommendationSource,
      String masteryVersion, String recommendationVersion, String masteryFallback,
      String recommendationFallback) {}
}
