package com.kaoyan.ai.domain;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaoyan.ai.common.ApiException;

/** 模型评分失败时保留已经完成的客观题成绩。 */
public class PartialScoringException extends ApiException {
  private final ObjectNode partial;

  public PartialScoringException(ObjectNode partial, ApiException cause) {
    super(cause.status(), cause.code(), cause.getMessage());
    this.partial = partial;
  }

  public ObjectNode partial() {
    return partial;
  }
}
