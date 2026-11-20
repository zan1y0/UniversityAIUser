package com.kaoyan.ai.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.service.RuleMasteryPredictor;
import java.util.*;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 不可用策略直接跳过：没有 LinUCB 权重/策略调用协议时，不伪造 bandit 结果。 */
@Primary
@Component
public class FallbackMasteryPredictor implements MasteryPredictor {
  private final HttpMasteryPredictor model;
  private final RuleMasteryPredictor rules;

  public FallbackMasteryPredictor(HttpMasteryPredictor model, RuleMasteryPredictor rules) {
    this.model = model;
    this.rules = rules;
  }

  @Override
  public Optional<JsonNode> predict(JsonNode input) {
    try {
      Optional<JsonNode> result = model.predict(input);
      if (result.isPresent()) return result;
    } catch (RuntimeException ignored) {
      // 上层不会因推理服务异常而中断。贪心推荐在业务层有有效估计时执行。
    }
    return rules.predict(input);
  }

  @Override public String version() { return model.version(); }
  @Override public boolean enabled() { return model.enabled(); }
}
