package com.kaoyan.ai.service.task;

import com.kaoyan.ai.KaoyanAiApplication;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Run against an explicitly supplied, disposable MySQL schema. */
@EnabledIfSystemProperty(named = "study.mysql.test", matches = "true")
@ActiveProfiles(value = "mysql", inheritProfiles = false)
@SpringBootTest(classes = KaoyanAiApplication.class, properties = {
    "spring.datasource.url=${STUDY_TEST_MYSQL_URL}",
    "app.seed-local=false"
})
class StudySupervisionMysqlTests extends StudySupervisionTests {}
