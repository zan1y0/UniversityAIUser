package com.kaoyan.ai.persistence;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 明确标注为本地联调样例，不伪造真实院校招生信息或历年真题。 */
@Component
public class LocalDataInitializer implements ApplicationRunner {
  public static final String USER = "10000000-0000-4000-8000-000000000001";
  public static final String SECOND = "10000000-0000-4000-8000-000000000002";
  public static final String PROGRAM = "20000000-0000-4000-8000-000000000001";
  public static final String PROGRAM2 = "20000000-0000-4000-8000-000000000002";
  public static final String MATH = "30000000-0000-4000-8000-000000000001";
  public static final String ENGLISH = "30000000-0000-4000-8000-000000000002";
  public static final String POLITICS = "30000000-0000-4000-8000-000000000003";
  public static final String PROFESSIONAL = "30000000-0000-4000-8000-000000000004";
  private final RecordRepository db;
  private final boolean seed;
  private final String studentPassword;
  private final UserAccountRepository accounts;

  public LocalDataInitializer(
      RecordRepository db,
      @Value("${app.seed-local:false}") boolean seed,
      @Value("${app.local-student-password:}") String studentPassword,
      UserAccountRepository accounts) {
    this.db = db;
    this.seed = seed;
    this.studentPassword = studentPassword;
    this.accounts = accounts;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (!seed) return;
    if (accounts.existsById(USER)) return;
    if (studentPassword.isBlank()) {
      throw new IllegalStateException(
          "首次初始化本地演示账号必须设置 LOCAL_STUDENT_PASSWORD 或 app.local-student-password");
    }
    account(USER, "student", "本地联调同学");
    account(SECOND, "student2", "第二位本地联调同学");
    for (String program : List.of(PROGRAM, PROGRAM2)) {
      ObjectNode p =
          obj(
              "id",
              program,
              "schoolName",
              "本地联调示例院校 " + (program.equals(PROGRAM) ? "A" : "B"),
              "programName",
              "计算机技术（联调样例）",
              "major",
              "计算机",
              "region",
              "北京",
              "year",
              2027,
              "difficulty",
              "medium",
              "scoreLine",
              null,
              "enrollment",
              null,
              "admissionRatio",
              null,
              "sources",
              arr(),
              "snapshotId",
              id());
      db.create("programs", program, "public", p);
    }
    String[] subjects = {MATH, ENGLISH, POLITICS, PROFESSIONAL},
        categories = {"math", "english", "politics", "professional"},
        names = {"导数与极限", "英语词汇", "政治理论基础", "数据结构"};
    for (int i = 0; i < subjects.length; i++) {
      String sid = subjects[i], kid = String.format("40000000-0000-4000-8000-%012d", i + 1);
      ObjectNode tree =
          obj(
              "subjectId",
              sid,
              "examType",
              i == 0 ? "math1" : categories[i],
              "year",
              2027,
              "edition",
              "local-v1",
              "nodes",
              arr(
                  obj(
                      "id",
                      kid,
                      "parentId",
                      null,
                      "name",
                      names[i],
                      "depth",
                      0,
                      "order",
                      0,
                      "subjectId",
                      sid)),
              "sources",
              arr(),
              "category",
              categories[i]);
      String treeKey =
          i < 3 ? categories[i] + ":" + (i == 0 ? "math1" : categories[i]) + ":2027" : sid;
      db.create("knowledge_trees", treeKey, "public", tree);
      for (int number = 0; number < 3; number++) {
        String qid = String.format("50000000-0000-4000-8000-%012d", i * 10 + number + 1),
            vid = String.format("51000000-0000-4000-8000-%012d", i * 10 + number + 1);
        String stem =
            i == 0
                ? "【本地练习样例】函数 f(x)=x² 在 x=1 处的导数是多少？"
                : i == 1
                    ? "【本地练习样例】单词 consistent 表示什么？"
                    : i == 2 ? "【本地练习样例】理论学习应重视哪一种方法？" : "【本地练习样例】顺序表按下标访问的时间复杂度是什么？";
        String[] opts =
            i == 0
                ? new String[] {"1", "2", "3", "4"}
                : i == 1
                    ? new String[] {"随机的", "一致的", "临时的", "相反的"}
                    : i == 2
                        ? new String[] {"孤立记忆", "理论联系实际", "只背答案", "忽略概念"}
                        : new String[] {"O(n)", "O(1)", "O(log n)", "O(n²)"};
        ArrayNode options = arr();
        for (int k = 0; k < 4; k++)
          options.add(obj("id", String.valueOf((char) ('A' + k)), "text", opts[k]));
        ObjectNode question =
            obj(
                "id",
                qid,
                "versionId",
                vid,
                "subjectId",
                sid,
                "knowledgeId",
                kid,
                "kind",
                "singleChoice",
                "stem",
                stem,
                "options",
                options,
                "difficulty",
                "medium",
                "maxScore",
                5,
                "sources",
                arr());
        ObjectNode explanation =
            obj(
                "kind",
                "explanation",
                "title",
                names[i] + "：样例讲解",
                "summary",
                i == 0 ? "使用幂函数求导法则，f'(x)=2x。" : "结合概念定义判断选项，并梳理常见误区。",
                "steps",
                arr("先识别题目考查的知识点。", "根据定义与条件逐项检查。"),
                "hints",
                arr("先写出定义和已知条件。"),
                "method",
                "定义与条件分析",
                "sources",
                arr(),
                "modelVersion",
                "authored-local-v1");
        db.create(
            "questions",
            vid,
            "public",
            obj(
                "question",
                question,
                "correctAnswer",
                obj("kind", "singleChoice", "optionId", "B"),
                "explanation",
                explanation,
                "year",
                2026));
      }
      if (i < 3) {
        String pid = String.format("60000000-0000-4000-8000-%012d", i + 1),
            pv = String.format("61000000-0000-4000-8000-%012d", i + 1);
        ArrayNode versions = arr();
        for (int n = 0; n < 3; n++)
          versions.add(String.format("51000000-0000-4000-8000-%012d", i * 10 + n + 1));
        ObjectNode paper =
            obj(
                "id",
                pid,
                "versionId",
                pv,
                "title",
                "本地联调练习卷（非全真试卷）",
                "subject",
                categories[i],
                "examType",
                i == 0 ? "math1" : categories[i],
                "year",
                2027,
                "durationMinutes",
                30,
                "maxScore",
                15,
                "questionCount",
                3,
                "sources",
                arr());
        db.create("papers", pv, "public", obj("paper", paper, "questionVersionIds", versions));
      }
    }
    String book = "70000000-0000-4000-8000-000000000001",
        chapter = "71000000-0000-4000-8000-000000000001";
    db.create(
        "subjects",
        PROFESSIONAL,
        "public",
        obj(
            "id",
            PROFESSIONAL,
            "name",
            "数据结构（本地示例）",
            "discipline",
            "computer",
            "programId",
            PROGRAM,
            "year",
            2027,
            "bookIds",
            arr(book)));
    db.create(
        "books",
        book,
        "public",
        obj(
            "id",
            book,
            "edition",
            "local-v1",
            "title",
            "课程联调资料",
            "chapters",
            arr(obj("id", chapter, "title", "顺序表", "content", "顺序存储结构通过连续地址与下标实现常数时间访问。"))));
    String nid = "80000000-0000-4000-8000-000000000001";
    ObjectNode notice =
        obj(
            "id",
            nid,
            "title",
            "本地联调样例通知",
            "content",
            "此为测试数据，不包含真实报名日期。请以研招网和院校官方通知为准。",
            "programId",
            null,
            "region",
            null,
            "year",
            2027,
            "category",
            "policy",
            "publishedAt",
            now(),
            "sources",
            arr(),
            "version",
            1);
    db.create("notices", nid, "public", notice);
    db.create("notice_revisions", nid + ":1", "public", notice);
    for (String type : List.of("registration", "confirmation", "admissionTicket", "exam")) {
      String mid = id();
      db.create(
          "milestones",
          mid,
          "public",
          obj(
              "id",
              mid,
              "type",
              type,
              "year",
              2027,
              "region",
              null,
              "programId",
              null,
              "startsAt",
              null,
              "endsAt",
              null,
              "verified",
              false,
              "sources",
              arr(),
              "version",
              1));
    }
  }

  private void account(String uid, String login, String display) {
    accounts.create(
        new UserAccountRepository.Account(
            uid, login, display, new BCryptPasswordEncoder(12).encode(studentPassword)));
    ObjectNode profile =
        obj(
            "user",
            obj("id", uid, "loginName", login, "displayName", display),
            "undergraduateMajor",
            "尚未填写",
            "targetProgramId",
            null,
            "targetDate",
            null,
            "dailyMinutes",
            120,
            "timezone",
            "Asia/Shanghai",
            "baseline",
            arr(),
            "activePlanId",
            null,
            "version",
            1);
    db.create("profiles", uid, uid, profile);
  }
}
