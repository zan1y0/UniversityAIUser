package com.kaoyan.ai.common;

import static com.kaoyan.ai.common.Jsons.*;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiAdvice {
  private static final Logger LOG = LoggerFactory.getLogger(ApiAdvice.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<?> business(ApiException e, HttpServletRequest r) {
    return ResponseEntity.status(e.status())
        .headers(e.status() == 429 ? retryHeaders() : new HttpHeaders())
        .body(
            obj(
                "code",
                e.code(),
                "message",
                e.getMessage(),
                "data",
                null,
                "traceId",
                r.getAttribute("traceId"),
                "details",
                arr()));
  }

  private HttpHeaders retryHeaders() {
    HttpHeaders h = new HttpHeaders();
    h.set("Retry-After", "60");
    return h;
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MaxUploadSizeExceededException.class})
  public ResponseEntity<?> invalid(Exception e, HttpServletRequest r) {
    return business(new ApiException(400, "INVALID_REQUEST", "请求内容或文件大小不符合契约"), r);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> unexpected(Exception e, HttpServletRequest r) {
    LOG.error("请求失败 traceId={}", r.getAttribute("traceId"), e);
    return business(new ApiException(500, "INTERNAL_ERROR", "服务发生异常，请通过 traceId 查询日志"), r);
  }
}
