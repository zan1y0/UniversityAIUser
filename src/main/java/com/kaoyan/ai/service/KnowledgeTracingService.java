package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.infrastructure.ai.MasteryPredictor;
import com.kaoyan.ai.persistence.repository.PredictionResultRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeTracingService {
  private static final Logger LOG = LoggerFactory.getLogger(KnowledgeTracingService.class);
  private static final int CACHE_LIMIT = 512;
  private static final int MODEL_HISTORY = 100;
  private final CatalogService catalog;
  private final TrainingSequenceService training;
  private final MasteryPredictor predictor;
  private final RuleMasteryPredictor rules;
  private final PredictionResultRepository results;
  private final Map<CacheKey, Result> cache = new LinkedHashMap<>(16, 0.75f, true);

  public KnowledgeTracingService(CatalogService catalog, TrainingSequenceService training,
      MasteryPredictor predictor, RuleMasteryPredictor rules, PredictionResultRepository results) {
    this.catalog = catalog;
    this.training = training;
    this.predictor = predictor;
    this.rules = rules;
    this.results = results;
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
    Input input = input(context.user(), subject, null, null);
    CacheKey key = key(context.user(), subject, input);
    Result result = cached(key);
    boolean hit = result != null;
    if (!hit) {
      result = calculate(context.user(), subject, input);
      remember(key, result);
    }
    boolean recommendations = "op140".equals(op);
    LOG.info("knowledgeTracing operation={} latencyMs={} source={} fallbackReason={} modelVersion={} predictorVersion={} cacheHit={}",
        op, (System.nanoTime() - start) / 1_000_000,
        recommendations ? result.recommendationSource() : result.masterySource(),
        recommendations ? result.recommendationFallback() : result.masteryFallback(),
        recommendations ? result.recommendationVersion() : result.masteryVersion(), predictor.version(), hit);
    ObjectNode response = obj("subjectId", subject, "asOf", result.asOf().toString(), "items",
        recommendations ? result.recommendations() : result.mastery(), "algorithmVersion",
        recommendations ? result.recommendationVersion() : result.masteryVersion());
    results.save(context.user(), subject, op, response.path("algorithmVersion").asText(),
        (System.nanoTime() - start) / 1_000_000, hit, response);
    return response;
  }

  /** 旧接口保留自己的日期、统计分母和字段，只替换有样本的有效模型估计。 */
  public void applyLegacy(String user, String subject, LocalDate from, LocalDate to,
      String operation, ObjectNode response) {
    long start = System.nanoTime();
    Input input = input(user, subject, from, to);
    CacheKey key = key(user, subject, input);
    Result result = cached(key);
    boolean hit = result != null;
    if (result == null) {
      result = calculate(user, subject, input);
      remember(key, result);
    }
    Map<String, JsonNode> estimates = new HashMap<>();
    for (JsonNode item : result.mastery()) estimates.put(item.path("knowledgeId").asText(), item);
    boolean used = false;
    for (JsonNode item : response.path("items")) {
      JsonNode estimate = estimates.get(item.path("knowledgeId").asText());
      if (item.hasNonNull("mastery") && estimate != null
          && "model".equals(estimate.path("source").asText()) && estimate.hasNonNull("mastery")) {
        ((ObjectNode) item).set("mastery", estimate.get("mastery"));
        used = true;
      }
    }
    response.put("algorithmVersion", used ? result.masteryVersion() : RuleMasteryPredictor.VERSION);
    results.save(user, subject, operation, response.path("algorithmVersion").asText(),
        (System.nanoTime() - start) / 1_000_000, hit, response);
  }

  private Input input(String user, String subject, LocalDate from, LocalDate to) {
    var nodes = catalog.learningNodes(catalog.subject(subject));
    var questions = catalog.questions(subject, null, null);
    var sequence = training.sequence(user, subject, Instant.now()).stream()
        .filter(row -> row.path("correct").isBoolean())
        .filter(row -> from == null || ("practice".equals(row.path("source").asText())
            && !Instant.parse(row.path("createdAt").asText()).atZone(ZONE).toLocalDate().isBefore(from)
            && !Instant.parse(row.path("createdAt").asText()).atZone(ZONE).toLocalDate().isAfter(to)))
        .toList();
    return new Input(nodes, questions, sequence);
  }

  private CacheKey key(String user, String subject, Input input) {
    // 比较完整输入，作答、评分、题库或先修关系变化立即失效，最多保留 512 个键。
    try {
      String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(input.toString().getBytes(StandardCharsets.UTF_8)));
      return new CacheKey(user, subject, predictor.version(), fingerprint);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void remember(CacheKey key, Result result) {
    synchronized (cache) {
      cache.put(key, result);
      if (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
    }
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

  private Result calculate(String user, String subject, Input input) {
    var nodes = input.nodes();
    List<ObjectNode> questions = input.questions();
    Instant asOf = Instant.now();
    var sequence = input.sequence();
    var evidence = rules.summarize(sequence);
    var candidates = questions.stream().map(q -> q.path("knowledgeId").asText())
        .filter(id -> !id.isBlank()).distinct().sorted().toList();
    Map<String, String> questionIds = new HashMap<>();
    for (JsonNode question : questions)
      questionIds.put(question.path("versionId").asText(), question.path("id").asText());
    var recentSequence = sequence.subList(Math.max(0, sequence.size() - MODEL_HISTORY), sequence.size());
    var recentEvidence = rules.summarize(recentSequence);
    var history = recentSequence.stream()
        .map(row -> obj("questionId", questionIds.getOrDefault(row.path("questionVersionId").asText(),
                row.path("questionVersionId").asText()),
            "knowledgeId", row.path("knowledgeId").asText("__unknown__"),
            "correct", row.path("correct").asBoolean() ? 1 : 0,
            "durationMs", row.path("durationMs").asLong(0),
            "attemptNo", row.path("attemptNo").asInt(1), "answeredAt", row.get("createdAt")))
        .toList();
    var prediction = predictor.predict(obj("userId", user, "subjectId", subject, "mode", "practice",
        "history", history, "candidates", candidates, "topK", 10));
    JsonNode output = prediction.orElseGet(() -> obj());
    Map<String, JsonNode> modelItems = new HashMap<>();
    for (JsonNode item : output.path("knowledgeMastery")) {
      if (unit(item.path("mastery")) != null && "model".equals(item.path("source").asText()))
        modelItems.put(item.path("knowledgeId").asText(), item);
    }
    List<ObjectNode> mastery = new ArrayList<>();
    Map<String, ObjectNode> byKnowledge = new HashMap<>();
    for (JsonNode node : nodes) {
      String id = node.path("id").asText();
      var observed = evidence.getOrDefault(id, new RuleMasteryPredictor.Evidence());
      // 没有作答过的知识点必须为 null，不能用模型的冷启动数值冒充已有证据。
      JsonNode item = observed.count() == 0 ? null : modelItems.get(id);
      int sampleCount = observed.count();
      if (item != null) {
        JsonNode count = item.path("sampleCount");
        sampleCount = count.isIntegralNumber() && count.canConvertToInt() && count.asInt() >= 0
            ? count.asInt() : recentEvidence.getOrDefault(id, new RuleMasteryPredictor.Evidence()).count();
      }
      ObjectNode estimate = obj("knowledgeId", id, "sampleCount", sampleCount,
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
    var ranking = rank(questions, output.path("recommendedQuestions"), evidence, byKnowledge);
    Map<String, JsonNode> remoteRecommendations = new HashMap<>();
    for (JsonNode item : output.path("recommendedQuestions"))
      remoteRecommendations.putIfAbsent(item.path("questionId").asText(), item);
    List<ObjectNode> recommendations = new ArrayList<>();
    Map<String, JsonNode> nodeIndex = new HashMap<>();
    for (JsonNode node : nodes) nodeIndex.put(node.path("id").asText(), node);
    for (ObjectNode question : ranking.questions().stream().limit(10).toList()) {
      String id = question.path("knowledgeId").asText();
      var estimate = byKnowledge.getOrDefault(id, obj());
      JsonNode remote = remoteRecommendations.get(question.path("id").asText());
      boolean modelRecommendation = "model".equals(ranking.source()) && remote != null;
      ObjectNode explanation = modelRecommendation && remote.path("reason").isTextual()
          && !remote.path("reason").asText().isBlank()
          && remote.path("reason").asText().length() <= 512
          ? obj("code", "MODEL_RANK", "message", remote.path("reason").asText())
          : reason(id, nodeIndex.getOrDefault(id, obj()), evidence, byKnowledge, ranking.source());
      recommendations.add(obj("questionVersionId", question.get("versionId"), "knowledgeId", id,
          "questionId", question.get("id"), "rank", recommendations.size() + 1,
          "ucbScore", modelRecommendation ? finite(remote.path("ucbScore")) : null,
          "confidence", modelRecommendation ? unit(remote.path("confidence")) : estimate.get("confidence"), "stddev", estimate.get("stddev"),
          "weight", estimate.get("weight"), "source", ranking.source(), "modelVersion", ranking.version(),
          "reason", explanation));
    }
    String unavailable = predictor.enabled() ? "model_unavailable_or_invalid" : "model_disabled";
    return new Result(asOf, Instant.now().plusSeconds(60), mastery, recommendations, masterySource,
        ranking.source(), modelCount == 0 ? RuleMasteryPredictor.VERSION : predictor.version(),
        ranking.version(), modelCount == 0 ? unavailable : modelCount < mastery.size() ? "partial_model_output" : "none",
        "model".equals(ranking.source()) ? "none" : predictor.version().equals(output.path("modelVersion").asText())
            ? "no_valid_model_recommendations" : unavailable);
  }

  private Ranking rank(List<ObjectNode> questions, JsonNode recommended,
      Map<String, RuleMasteryPredictor.Evidence> evidence, Map<String, ObjectNode> estimates) {
    Map<String, ObjectNode> byId = new LinkedHashMap<>();
    Set<String> ambiguous = new HashSet<>();
    for (ObjectNode question : questions) {
      String id = question.path("id").asText();
      if (byId.putIfAbsent(id, question) != null) ambiguous.add(id);
    }
    List<ObjectNode> remote = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    List<JsonNode> ordered = new ArrayList<>();
    recommended.forEach(ordered::add);
    ordered.sort(Comparator.comparingInt(item -> item.path("rank").asInt(Integer.MAX_VALUE)));
    for (JsonNode item : ordered) {
      String id = item.path("questionId").asText();
      if (byId.containsKey(id) && !ambiguous.contains(id) && seen.add(id)) remote.add(byId.get(id));
    }
    if (!remote.isEmpty()) return new Ranking(remote, "model", predictor.version());

    Comparator<ObjectNode> stable = Comparator.comparing(q -> q.path("versionId").asText());
    var predicted = questions.stream().filter(q -> estimates.containsKey(knowledgeId(q))
        && "model".equals(estimates.get(knowledgeId(q)).path("source").asText())
        && estimates.get(knowledgeId(q)).hasNonNull("mastery")).toList();
    if (!predicted.isEmpty()) {
      return new Ranking(predicted.stream().sorted(Comparator.<ObjectNode>comparingDouble(
          q -> estimates.get(knowledgeId(q)).path("mastery").asDouble()).thenComparing(stable)).toList(),
          "greedy", "kt-greedy-v1");
    }
    return new Ranking(questions.stream().sorted(Comparator.<ObjectNode>comparingDouble(q -> {
      var observed = evidence.get(knowledgeId(q));
      return observed == null ? 1.0 : observed.mastery();
    }).thenComparing(stable)).toList(), "rule", RuleMasteryPredictor.VERSION);
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

  private Double finite(JsonNode node) {
    return node.isNumber() && Double.isFinite(node.asDouble()) ? node.asDouble() : null;
  }

  private record Input(List<ObjectNode> nodes, List<ObjectNode> questions, List<ObjectNode> sequence) {}
  private record CacheKey(String user, String subject, String modelVersion, String fingerprint) {}
  private record Ranking(List<ObjectNode> questions, String source, String version) {}
  private record Result(Instant asOf, Instant expiresAt, List<ObjectNode> mastery,
      List<ObjectNode> recommendations, String masterySource, String recommendationSource,
      String masteryVersion, String recommendationVersion, String masteryFallback,
      String recommendationFallback) {}
}
