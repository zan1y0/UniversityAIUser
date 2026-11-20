package com.kaoyan.ai.infrastructure.ai;

import static com.kaoyan.ai.common.Jsons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.service.*;
import com.kaoyan.ai.persistence.repository.PredictionResultRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KnowledgeTracingTests {
  @Test
  void modelOutputIsVersionedCachedAndFallsBackWhenProviderFails() {
    String subject = id(), knowledge = id(), question = id();
    String modelUrl = "http://model.test";
    var builder = RestClient.builder().baseUrl(modelUrl);
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(modelUrl + "/v1/mastery")).andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.userId").value("test-user"))
        .andExpect(jsonPath("$.mode").value("practice"))
        .andExpect(jsonPath("$.history[0].correct").value(0))
        .andExpect(jsonPath("$.history[0].durationMs").value(0))
        .andExpect(jsonPath("$.history[0].attemptNo").value(1))
        .andExpect(jsonPath("$.candidates[0]").value(knowledge))
        .andRespond(withSuccess(obj("modelVersion", "kt-current", "knowledgeMastery", arr(obj("knowledgeId", knowledge,
            "mastery", 0.35, "confidence", 0.88, "stddev", 0.13, "weight", 0.7, "source", "model")),
            "recommendedQuestions", arr(obj("questionId", id(), "rank", 1),
                obj("questionId", question, "rank", 2, "confidence", 0.74, "ucbScore", 1.2, "reason", "先修知识未达标"),
                obj("questionId", question, "rank", 3))).toString(), MediaType.APPLICATION_JSON));
    server.expect(requestTo(modelUrl + "/v1/mastery")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    var jdbc = mock(JdbcTemplate.class);
    var predictor = new HttpMasteryPredictor(jdbc, true, "kt-current", "kt-previous", builder.build());
    predictor.run(new DefaultApplicationArguments());
    verify(jdbc).update("INSERT INTO kt_model_versions (algorithm_version) VALUES (?)", "kt-current");
    verify(jdbc).update("INSERT INTO kt_model_versions (algorithm_version) VALUES (?)", "kt-previous");
    var catalog = mock(CatalogService.class);
    var tree = obj("nodes", arr(obj("id", knowledge)));
    when(catalog.subject(subject)).thenReturn(tree);
    when(catalog.learningNodes(tree)).thenReturn(List.of(obj("id", knowledge)));
    when(catalog.questions(subject, null, null)).thenReturn(
        List.of(obj("id", question, "versionId", question, "knowledgeId", knowledge)));
    var sequence = mock(TrainingSequenceService.class);
    when(sequence.sequence(anyString(), eq(subject), any(Instant.class)))
        .thenReturn(List.of(obj("correct", false, "knowledgeId", knowledge)));
    var audit = mock(PredictionResultRepository.class);
    var service = new KnowledgeTracingService(catalog, sequence, new FallbackMasteryPredictor(predictor, new RuleMasteryPredictor()),
        new RuleMasteryPredictor(), audit);
    var query = obj("subjectId", subject);
    var user = new DomainContext("test-user", Map.of(), query, obj());
    JsonNode estimate = service.execute("op139", user).path("items").path(0);
    assertEquals("model", estimate.path("source").asText());
    assertEquals(0.88, estimate.path("confidence").asDouble());
    JsonNode recommendations = service.execute("op140", user).path("items");
    assertEquals(1, recommendations.size());
    assertEquals(question, recommendations.path(0).path("questionVersionId").asText());
    assertEquals(0.74, recommendations.path(0).path("confidence").asDouble());
    assertEquals(1.2, recommendations.path(0).path("ucbScore").asDouble());
    assertEquals("先修知识未达标", recommendations.path(0).path("reason").path("message").asText());
    verify(audit).save(eq("test-user"), eq(subject), eq("op140"), eq("kt-current"), anyLong(), eq(true), any());
    var other = new DomainContext("other-user", Map.of(), query, obj());
    JsonNode fallback = service.execute("op139", other).path("items").path(0);
    assertEquals("rule", fallback.path("source").asText());
    assertEquals(0.0, fallback.path("mastery").asDouble());
    assertTrue(fallback.path("confidence").isNull());
    server.verify();
  }
}
