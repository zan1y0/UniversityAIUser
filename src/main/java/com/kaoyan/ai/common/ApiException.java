package com.kaoyan.ai.common;

public class ApiException extends RuntimeException {
  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static ApiException missing() {
    return new ApiException(404, "RESOURCE_NOT_FOUND", "资源不存在或不属于当前用户");
  }

  public static ApiException state(String message) {
    return new ApiException(409, "INVALID_STATE", message);
  }
}
