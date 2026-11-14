package com.kaoyan.ai.service.auth;

import static com.kaoyan.ai.common.Jsons.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class AccountInputs {
  private static final Pattern EMAIL = Pattern.compile(
      "[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

  private AccountInputs() {}

  public static void fields(JsonNode body, String... allowed) {
    require(body != null && body.isObject(), "请输入请求数据");
    var names = Set.of(allowed);
    body.fieldNames().forEachRemaining(key -> require(names.contains(key), "未知请求参数"));
  }

  public static String string(JsonNode body, String key) {
    require(body.path(key).isTextual(), "请填写完整的账号信息");
    return body.path(key).asText();
  }

  public static String email(String input) {
    String value = input.strip().toLowerCase(Locale.ROOT);
    int at = value.indexOf('@');
    require(value.length() <= 254 && EMAIL.matcher(value).matches() && !value.startsWith(".")
        && !value.contains("..") && at > 0 && value.charAt(at - 1) != '.', "请输入有效的邮箱");
    return value;
  }

  public static String password(JsonNode body) {
    String password = credential(body, "password");
    require(password.length() >= 8 && password.matches("(?s).*[A-Za-z].*")
        && password.matches("(?s).*\\d.*"), "密码必须至少8个字符并包含字母和数字");
    require(password.equals(string(body, "confirmPassword")), "两次输入的密码不一致");
    return password;
  }

  public static String credential(JsonNode body, String key) {
    String password = string(body, key);
    require(!password.isEmpty() && password.getBytes(StandardCharsets.UTF_8).length <= 72,
        "密码长度无效");
    return password;
  }

  public static String name(String value) {
    String stripped = value.strip();
    require(!stripped.isBlank() && stripped.length() <= 32
        && stripped.chars().noneMatch(Character::isISOControl), "姓名或昵称需为1～32个字符");
    return stripped;
  }
}
