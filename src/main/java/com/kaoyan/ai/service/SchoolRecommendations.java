package com.kaoyan.ai.service;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

final class SchoolRecommendations {
  private static final int HISTORY_YEARS = 3;
  private static final int REACH_MINIMUM = -10;
  private static final int SAFETY_MINIMUM = 15;
  private static final Pattern PROGRAM_TITLE =
      Pattern.compile("^([0-9]{4}[0-9A-Z]{2})\\s+.+?\\s*·\\s*(?:(\\d+)\\s+)?(.+)$");
  private static final Pattern REGION_SUFFIX =
      Pattern.compile("(?:维吾尔自治区|壮族自治区|回族自治区|自治区|特别行政区|省|市)$");
  private static final Set<String> SPECIAL_EXAM_MODES = Set.of("单考", "单独考试", "推荐免试", "推免");

  private static final Set<String> COMPUTING =
      Set.of("0812", "0835", "0839", "085404", "085405", "085410", "085411", "085412", "1405");
  private static final Map<String, Set<String>> RELATED_CODES = Map.ofEntries(
      Map.entry("计算机科学与技术", COMPUTING),
      Map.entry("软件工程", COMPUTING),
      Map.entry("计算机技术", COMPUTING),
      Map.entry("网络工程", COMPUTING),
      Map.entry("网络空间安全", COMPUTING),
      Map.entry("人工智能", COMPUTING),
      Map.entry("数据科学与大数据技术", COMPUTING),
      Map.entry("信息安全", COMPUTING),
      Map.entry("教育学", Set.of("0401", "0451")),
      Map.entry("心理学", Set.of("0402", "0454")),
      Map.entry("汉语言文学", Set.of("0501", "045103")),
      Map.entry("法学", Set.of("0301", "035102")),
      Map.entry("经济学", Set.of("0201", "0202", "0251", "0252", "0254", "0255", "0256")),
      Map.entry("临床医学", Set.of("1002", "1051")),
      Map.entry("电气工程及其自动化", Set.of("0808", "085801")));

  private record ProgramKey(String school, String region, String code, String department,
                            String studyMode, String examMode) {}

  private record Program(ObjectNode data, ProgramKey key, String departmentName, String subjects) {
    int year() { return data.path("year").asInt(); }

    JsonNode scoreLine() { return data.path("scoreLine"); }
  }

  private record Assessment(String tier, Double reference, Double gap, List<Program> samples,
                            String reason, boolean limitedScope) {}

  private record Ranked(ObjectNode result, int preference, int majorMatch, double score, int samples) {}

  private SchoolRecommendations() {}

  static ObjectNode recommend(JsonNode input, List<ObjectNode> records, Map<String, JsonNode> originals) {
    String undergraduate = majorName(input.path("undergraduateMajor").asText());
    String target = majorName(input.path("targetMajor").asText());
    require(!undergraduate.isEmpty(), "请填写本科专业");
    if (input.has("targetMajor")) require(!target.isEmpty(), "请填写目标招生专业");
    int year = input.path("year").asInt();
    JsonNode estimatedScore = input.path("estimatedScore");
    Set<String> regions = new HashSet<>();
    input.path("regions").forEach(region -> regions.add(regionName(region.asText())));
    String preferredTier = switch (input.path("difficulty").asText()) {
      case "easy" -> "safety";
      case "hard" -> "reach";
      default -> "match";
    };

    List<Program> programs = records.stream()
        .filter(row -> row.path("year").asInt() >= year - HISTORY_YEARS
            && row.path("year").asInt() <= year)
        .map(row -> program(row, originals.getOrDefault(row.path("id").asText(), row)))
        .toList();
    Map<ProgramKey, List<Program>> history = new HashMap<>();
    for (Program program : programs) {
      if (program.year() < year && program.scoreLine().isNumber() && hasIdentity(program.key())) {
        history.computeIfAbsent(program.key(), key -> new ArrayList<>()).add(program);
      }
    }

    List<Ranked> results = new ArrayList<>();
    for (Program program : programs) {
      if (program.year() != year || (!regions.isEmpty() && !regions.contains(program.key().region()))) continue;
      if (SPECIAL_EXAM_MODES.contains(program.key().examMode())) continue;
      int majorMatch = matchMajor(program, undergraduate, target);
      if (majorMatch == 0) continue;

      Assessment assessment = assess(program, history.getOrDefault(program.key(), List.of()), estimatedScore);
      Double gap = assessment.gap();
      Double score = gap == null ? null : Math.max(0, Math.min(100, 70 + gap / 3));
      String majorReason = !target.isEmpty() ? "符合指定招生专业"
          : majorMatch == 2 ? "与本科专业名称一致" : "属于本科专业的相关报考方向";
      List<String> risks = new ArrayList<>();
      if (assessment.samples().size() == 1) risks.add("仅有一年可参考的复试线");
      if (assessment.limitedScope()) risks.add("部分初试科目或学习方式未收录，请核对历年考试口径");
      if (assessment.tier().equals("highRisk")) risks.add("预估分低于参考线超过 10 分");
      if (majorMatch == 1 || (!target.isEmpty() && !target.equals(undergraduate))) {
        risks.add("请核对招生简章中的本科专业及其他报考条件");
      }
      ObjectNode result = obj(
          "program", program.data(),
          "matchScore", score,
          "tier", assessment.tier(),
          "studyMode", program.key().studyMode().isEmpty() ? null : program.key().studyMode(),
          "referenceScore", assessment.reference(),
          "scoreGap", gap,
          "scoreHistory", assessment.samples().stream().map(sample -> obj(
              "year", sample.year(), "scoreLine", sample.scoreLine(), "sources", sample.data().path("sources")))
              .toList(),
          "reasons", arr(majorReason, assessment.reason()),
          "risks", risks);
      int preference = switch (assessment.tier()) {
        case "pending" -> 2;
        case "highRisk" -> 3;
        default -> assessment.tier().equals(preferredTier) ? 0 : 1;
      };
      results.add(new Ranked(result, preference, majorMatch, score == null ? -1 : score, assessment.samples().size()));
    }
    results.sort(Comparator.comparingInt(Ranked::preference)
        .thenComparing(Comparator.comparingInt(Ranked::majorMatch).reversed())
        .thenComparing(Comparator.comparingDouble(Ranked::score).reversed())
        .thenComparing(Comparator.comparingInt(Ranked::samples).reversed())
        .thenComparing(row -> row.result().path("program").path("programName").asText())
        .thenComparing(row -> row.result().path("program").path("id").asText()));
    return obj("items", results.stream().limit(10_000).map(Ranked::result).toList());
  }

  private static Assessment assess(Program program, List<Program> history, JsonNode estimatedScore) {
    // For a year with several published lines at the same scope, use the highest line.
    Map<Integer, Program> byYear = new TreeMap<>();
    for (Program row : history) {
      if (!program.subjects().isEmpty() && !row.subjects().isEmpty()
          && !program.subjects().equals(row.subjects())) continue;
      byYear.merge(row.year(), row, (first, second) ->
          first.scoreLine().asDouble() >= second.scoreLine().asDouble() ? first : second);
    }
    List<Program> samples = byYear.isEmpty() && program.scoreLine().isNumber()
        ? List.of(program) : List.copyOf(byYear.values());
    if (samples.isEmpty()) {
      return new Assessment("pending", null, null, samples, "暂无同院系、专业和学习方式的可参考复试线", false);
    }
    double[] scores = samples.stream().mapToDouble(row -> row.scoreLine().asDouble()).sorted().toArray();
    double reference = (scores[(scores.length - 1) / 2] + scores[scores.length / 2]) / 2;
    String basis = byYear.isEmpty() ? "采用当年已公布的复试线" : "采用近三年内已收录的复试线中位数";
    boolean limitedScope = program.key().studyMode().isEmpty() || program.subjects().isEmpty()
        || samples.stream().anyMatch(sample -> sample.subjects().isEmpty());
    if (!estimatedScore.isNumber()) {
      return new Assessment("pending", reference, null, samples, basis + "，填写预估初试总分后可分档", limitedScope);
    }
    double gap = estimatedScore.asDouble() - reference;
    String tier = gap >= SAFETY_MINIMUM ? "safety"
        : gap >= 0 ? "match" : gap >= REACH_MINIMUM ? "reach" : "highRisk";
    return new Assessment(tier, reference, gap, samples, basis, limitedScope);
  }

  private static Program program(ObjectNode data, JsonNode original) {
    String title = Normalizer.normalize(data.path("programName").asText(), Normalizer.Form.NFKC).strip();
    var titleParts = PROGRAM_TITLE.matcher(title);
    boolean codedTitle = titleParts.matches();
    String code = original.path("majorCode").asText(codedTitle ? titleParts.group(1) : "");
    String departmentName = original.path("department").asText(codedTitle ? titleParts.group(3) : "");
    String deptCode = original.path("departmentCode").asText(codedTitle ? titleParts.group(2) : "");
    String department = deptCode == null || deptCode.isBlank() ? normalize(departmentName) : deptCode.strip();
    ProgramKey key = new ProgramKey(normalize(data.path("schoolName").asText()),
        regionName(data.path("region").asText()), code.strip(), department,
        normalize(original.path("studyMode").asText()), normalize(original.path("examMode").asText()));
    return new Program(data, key, normalize(departmentName), normalize(original.path("examSubjects").asText()));
  }

  private static boolean hasIdentity(ProgramKey key) {
    return !key.school().isEmpty() && !key.code().isEmpty() && !key.department().isEmpty();
  }

  private static int matchMajor(Program program, String undergraduate, String target) {
    String programMajor = majorName(program.data().path("major").asText());
    String code = program.key().code();
    if (!target.isEmpty()) return target.equals(programMajor) || target.equalsIgnoreCase(code) ? 2 : 0;
    if (undergraduate.equals(programMajor) || undergraduate.equalsIgnoreCase(code)) return 2;
    Set<String> related = RELATED_CODES.getOrDefault(undergraduate, Set.of());
    if (related.stream().anyMatch(code::startsWith)) return 1;
    if (related == COMPUTING && code.equals("085400")
        && (program.departmentName().contains("计算机") || program.departmentName().contains("软件")
            || program.departmentName().contains("人工智能"))) return 1;
    return 0;
  }

  private static String majorName(String value) {
    String name = normalize(value);
    return switch (name) {
      case "计算机", "计科" -> "计算机科学与技术";
      case "软工" -> "软件工程";
      default -> name;
    };
  }

  private static String regionName(String value) {
    return REGION_SUFFIX.matcher(normalize(value)).replaceFirst("");
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }
}
