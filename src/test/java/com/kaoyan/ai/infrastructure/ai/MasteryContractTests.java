package com.kaoyan.ai.infrastructure.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.persistence.repository.PredictionResultRepository;
import com.kaoyan.ai.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MasteryContractTests {
  final String subject = id(), knowledge = id(), unseen = id(), question = id(), version = id();
  final CatalogService catalog = mock(CatalogService.class);
  final TrainingSequenceService sequence = mock(TrainingSequenceService.class);
  final MasteryPredictor predictor = mock(MasteryPredictor.class);

  ObjectNode row(boolean correct) {
    return obj("correct", correct, "knowledgeId", knowledge, "source", "practice",
        "questionVersionId", version, "createdAt", Instant.now().minusSeconds(60).toString());
  }

  ObjectNode output() {
    return obj("modelVersion", "kt-gkt-v1", "knowledgeMastery", arr(
        obj("knowledgeId", knowledge, "mastery", 0.35, "source", "model"),
        obj("knowledgeId", unseen, "mastery", 0.9, "source", "model")), "recommendedQuestions", arr());
  }

  KnowledgeTracingService service() {
    var tree = obj("nodes", arr(obj("id", knowledge), obj("id", unseen)));
    when(catalog.subject(subject)).thenReturn(tree);
    when(catalog.learningNodes(tree)).thenReturn(List.of(obj("id", knowledge), obj("id", unseen)));
    when(catalog.questions(subject, null, null)).thenReturn(
        List.of(obj("id", question, "versionId", version, "knowledgeId", knowledge)));
    when(predictor.version()).thenReturn("kt-gkt-v1");
    when(predictor.predict(any())).thenReturn(Optional.of(output()));
    when(sequence.sequence(anyString(), eq(subject), any(Instant.class))).thenReturn(List.of(row(false)));
    return new KnowledgeTracingService(catalog, sequence, predictor, new RuleMasteryPredictor(),
        mock(PredictionResultRepository.class));
  }

  JsonNode execute(KnowledgeTracingService service, String op) {
    return service.execute(op, new DomainContext("student", Map.of(), obj("subjectId", subject), obj()));
  }

  @Test
  void latest100HistoryAndAttemptChangesInvalidateCache() {
    var service = service();
    List<ObjectNode> rows = new ArrayList<>();
    for (int i = 0; i < 110; i++) rows.add(row(false).put("attemptNo", i + 1));
    when(sequence.sequence(anyString(), eq(subject), any(Instant.class))).thenReturn(rows);
    var result = execute(service, "op139");
    assertTrue(result.path("items").path(1).path("mastery").isNull());
    assertEquals(100, result.path("items").path(0).path("sampleCount").asInt());
    execute(service, "op140");
    verify(predictor).predict(argThat(request -> request.path("history").size() == 100
        && request.path("history").path(0).path("attemptNo").asInt() == 11
        && question.equals(request.path("history").path(0).path("questionId").asText())));
    rows.add(row(true));
    execute(service, "op139");
    verify(predictor, times(2)).predict(any());
  }

  @Test
  void validEstimatesUseGreedyButInvalidValuesAndUnknownQuestionsUseRule() {
    var service = service();
    assertEquals("kt-greedy-v1", execute(service, "op140").path("algorithmVersion").asText());
    service = service();
    var bad = output();
    ((ObjectNode) bad.path("knowledgeMastery").path(0)).put("mastery", 1.2);
    bad.set("recommendedQuestions", arr(obj("questionId", id(), "rank", 1)));
    when(predictor.predict(any())).thenReturn(Optional.of(bad));
    var result = execute(service, "op139");
    assertEquals(RuleMasteryPredictor.VERSION, result.path("algorithmVersion").asText());
    assertTrue(result.path("items").path(0).path("confidence").isNull());
    assertEquals("rule", execute(service, "op140").path("items").path(0).path("source").asText());
  }

  @Test
  void legacyFieldsAndDateWindowArePreserved() {
    var service = service();
    var yesterday = LocalDate.now(ZONE).minusDays(1);
    var within = row(false).put("createdAt", yesterday.atTime(12, 0).atZone(ZONE).toInstant().toString());
    var old = row(true).put("createdAt", yesterday.minusDays(3).atStartOfDay(ZONE).toInstant().toString());
    when(sequence.sequence(anyString(), eq(subject), any(Instant.class)))
        .thenReturn(List.of(old, within, within.deepCopy().put("source", "exam")));
    var legacy = obj("items", arr(obj("knowledgeId", knowledge, "attemptCount", 1,
        "correctCount", 0, "mastery", 0.0), obj("knowledgeId", unseen,
        "attemptCount", 0, "correctCount", 0, "mastery", null)));
    service.applyLegacy("student", subject, yesterday, yesterday, "op049", legacy);
    assertEquals(0.35, legacy.path("items").path(0).path("mastery").asDouble());
    assertTrue(legacy.path("items").path(1).path("mastery").isNull());
    assertEquals(4, legacy.path("items").path(0).size());
    verify(predictor).predict(argThat(request -> request.path("history").size() == 1));
  }

  @Test
  void malformedJsonVersionAndValidationErrorsAreUnavailableWithoutRetry() {
    for (int scenario = 0; scenario < 3; scenario++) {
      var builder = RestClient.builder().baseUrl("http://model.test");
      var server = MockRestServiceServer.bindTo(builder).build();
      var response = server.expect(requestTo("http://model.test/v1/mastery"));
      if (scenario == 0) response.andRespond(withSuccess("{bad-json", MediaType.APPLICATION_JSON));
      if (scenario == 1) response.andRespond(withSuccess(output().put("modelVersion", "unexpected").toString(), MediaType.APPLICATION_JSON));
      if (scenario == 2) response.andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
      var http = new HttpMasteryPredictor(mock(JdbcTemplate.class), true, "kt-gkt-v1", "", builder.build());
      assertTrue(http.predict(obj()).isEmpty());
      server.verify();
    }
  }

  @Test
  void realHttpReadTimeoutDoesNotEscape() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/mastery", exchange -> {
      try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      exchange.close();
    });
    server.start();
    try {
      var http = new HttpMasteryPredictor(mock(JdbcTemplate.class), true,
          "http://127.0.0.1:" + server.getAddress().getPort(), 100, "kt-gkt-v1", "");
      long start = System.nanoTime();
      assertTrue(http.predict(obj()).isEmpty());
      assertTrue((System.nanoTime() - start) / 1_000_000 < 1500);
    } finally { server.stop(0); }
  }

  @Test
  void ruleFallbackUsesAllEvidenceAndKeepsUnseenMasteryNull() {
    var service = service();
    when(predictor.predict(any())).thenReturn(Optional.empty());
    List<ObjectNode> rows = new ArrayList<>();
    for (int i = 0; i < 150; i++) rows.add(row(i < 50));
    when(sequence.sequence(anyString(), eq(subject), any(Instant.class))).thenReturn(rows);
    var result = execute(service, "op139");
    assertEquals(1.0 / 3, result.path("items").path(0).path("mastery").asDouble());
    assertEquals(150, result.path("items").path(0).path("sampleCount").asInt());
    assertTrue(result.path("items").path(1).path("mastery").isNull());
  }

  @Test
  void rulePredictorSharesStatisticalDefinitionAndRejectsInvalidLabels() {
    var result = new RuleMasteryPredictor().predict(obj("history", arr(
        obj("knowledgeId", knowledge, "correct", 1), obj("knowledgeId", knowledge, "correct", 0),
        obj("knowledgeId", knowledge, "correct", 7)), "candidates", arr(knowledge, unseen))).orElseThrow();
    Map<String, JsonNode> estimates = new HashMap<>();
    result.path("knowledgeMastery").forEach(item -> estimates.put(item.path("knowledgeId").asText(), item));
    assertEquals(0.5, estimates.get(knowledge).path("mastery").asDouble());
    assertEquals(2, estimates.get(knowledge).path("sampleCount").asInt());
    assertTrue(estimates.get(unseen).path("mastery").isNull());
  }
}
