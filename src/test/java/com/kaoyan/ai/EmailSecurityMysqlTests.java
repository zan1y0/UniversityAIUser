package com.kaoyan.ai;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 使用专为本套测试创建的独立 MySQL 实例。 */
@EnabledIfSystemProperty(named="email.mysql.integration",matches="true")
@ActiveProfiles("mysql")
@SpringBootTest(classes=KaoyanAiApplication.class, properties={
    "app.seed-local=false",
    "spring.datasource.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"})
class EmailSecurityMysqlTests extends EmailSecurityTests {}
