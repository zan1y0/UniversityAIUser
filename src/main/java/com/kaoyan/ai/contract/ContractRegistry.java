package com.kaoyan.ai.contract;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.networknt.schema.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** OpenAPI 3.1 是字段、枚举、请求与返回格式的唯一来源。 */
@Component
public class ContractRegistry {
  private final JsonNode document;
  private final Map<String, JsonNode> operations = new HashMap<>();
  private final Map<String, JsonSchema> schemas = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<String, JsonSchema> compiled = new java.util.concurrent.ConcurrentHashMap<>();
  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  public ContractRegistry() throws Exception {
    document = MAPPER.readTree(new ClassPathResource("contract/openapi.json").getInputStream());
    document
        .path("paths")
        .fields()
        .forEachRemaining(
            p ->
                p.getValue()
                    .fields()
                    .forEachRemaining(
                        o ->
                            operations.put(
                                o.getValue().path("operationId").asText(), o.getValue())));
  }

  public JsonNode operation(String id) {
    return operations.get(id);
  }

  public JsonNode schema(String name) {
    return expand(document.path("components").path("schemas").path(name));
  }

  private JsonNode expand(JsonNode node) {
    if (node.isObject()) {
      if (node.has("$ref")) return expand(document.at(node.path("$ref").asText().substring(1)));
      ObjectNode result = obj();
      node.fields().forEachRemaining(e -> result.set(e.getKey(), expand(e.getValue())));
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = arr();
      node.forEach(e -> result.add(expand(e)));
      return result;
    }
    return node;
  }

  public int status(String op) {
    return Integer.parseInt(operation(op).path("responses").fieldNames().next());
  }

  private JsonSchema compile(JsonNode schema) {
    return compiled.computeIfAbsent(
        schema.toString(),
        key -> {
          ObjectNode root =
              obj(
                  "$schema",
                  "https://json-schema.org/draft/2020-12/schema",
                  "$defs",
                  document.path("components").path("schemas"));
          schema.fields().forEachRemaining(e -> root.set(e.getKey(), e.getValue()));
          JsonNode standard = parse(root.toString().replace("#/components/schemas/", "#/$defs/"));
          removeOpenApiAnnotations(standard);
          SchemaValidatorsConfig cfg = new SchemaValidatorsConfig();
          cfg.setFormatAssertionsEnabled(true);
          return factory.getSchema(standard, cfg);
        });
  }

  private void removeOpenApiAnnotations(JsonNode n) {
    if (n.isObject()) {
      ((ObjectNode) n).remove("discriminator");
      n.forEach(this::removeOpenApiAnnotations);
    } else if (n.isArray()) n.forEach(this::removeOpenApiAnnotations);
  }

  public void validate(JsonNode schema, JsonNode value, int status) {
    Set<ValidationMessage> errors = compile(schema).validate(value);
    if (!errors.isEmpty())
      throw new ApiException(
          status,
          status == 500 ? "INTERNAL_ERROR" : "INVALID_REQUEST",
          "数据不符合接口约定：" + errors.iterator().next().getMessage());
  }

  public void model(String name, JsonNode value) {
    JsonSchema schema =
        schemas.computeIfAbsent(name, k -> compile(obj("$ref", "#/components/schemas/" + k)));
    var errors = schema.validate(value);
    if (!errors.isEmpty())
      throw new ApiException(
          500, "INTERNAL_ERROR", "结果格式异常：" + name + " " + errors.iterator().next().getMessage());
  }

  public ObjectNode query(String op, HttpServletRequest request) {
    ObjectNode q = obj();
    Set<String> allowed = new HashSet<>();
    for (JsonNode param : operation(op).path("parameters")) {
      if (!param.path("in").asText().equals("query")) continue;
      String name = param.path("name").asText();
      allowed.add(name);
      String raw = request.getParameter(name);
      if (raw == null) {
        if (param.path("required").asBoolean())
          throw new ApiException(400, "INVALID_REQUEST", "缺少查询参数：" + name);
        continue;
      }
      if (request.getParameterValues(name).length != 1)
        throw new ApiException(400, "INVALID_REQUEST", "查询参数不能重复：" + name);
      JsonNode schema = param.path("schema");
      JsonNode value;
      try {
        value =
            switch (schema.path("type").asText()) {
              case "integer" -> MAPPER.valueToTree(Integer.parseInt(raw));
              case "number" -> MAPPER.valueToTree(Double.parseDouble(raw));
              case "boolean" -> {
                if (!raw.equals("true") && !raw.equals("false"))
                  throw new IllegalArgumentException();
                yield MAPPER.valueToTree(Boolean.parseBoolean(raw));
              }
              case "array" -> MAPPER.valueToTree(raw.split(",", -1));
              default -> MAPPER.valueToTree(raw);
            };
      } catch (Exception e) {
        throw new ApiException(400, "INVALID_REQUEST", "查询参数类型错误：" + name);
      }
      validate(schema, value, 400);
      q.set(name, value);
    }
    for (String k : request.getParameterMap().keySet())
      if (!allowed.contains(k)) throw new ApiException(400, "INVALID_REQUEST", "未知查询参数：" + k);
    return q;
  }

  public void request(String op, JsonNode body, Map<String, String> paths, HttpServletRequest r) {
    JsonNode o = operation(op);
    for (JsonNode p : o.path("parameters")) {
      String in = p.path("in").asText(), k = p.path("name").asText();
      String v = in.equals("path") ? paths.get(k) : in.equals("header") ? r.getHeader(k) : null;
      if (in.equals("query")) continue;
      if (v == null) {
        if (p.path("required").asBoolean())
          throw new ApiException(400, "INVALID_REQUEST", "缺少参数：" + k);
      } else validate(p.path("schema"), MAPPER.valueToTree(v), 400);
    }
    JsonNode content = o.path("requestBody").path("content");
    if (content.has("application/json"))
      validate(content.path("application/json").path("schema"), body, 400);
    else if (!body.isMissingNode() && !body.isNull() && !body.isEmpty())
      throw new ApiException(400, "INVALID_REQUEST", "此接口不接受请求体");
  }

  public boolean idempotent(String op) {
    for (JsonNode p : operation(op).path("parameters"))
      if (p.path("name").asText().equals("Idempotency-Key")) return true;
    return false;
  }

  public void response(String op, JsonNode envelope) {
    JsonNode schema =
        operation(op)
            .path("responses")
            .path(Integer.toString(status(op)))
            .path("content")
            .path("application/json")
            .path("schema");
    if (!schema.isMissingNode()) validate(schema, envelope, 500);
  }
}
