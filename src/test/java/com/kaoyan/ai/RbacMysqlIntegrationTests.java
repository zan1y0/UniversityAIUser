package com.kaoyan.ai;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
/** 与H2相同的权限及业务流程，在真实MySQL执行，所有业务写入随测试事务回滚。 */
@EnabledIfSystemProperty(named="mysql.integration",matches="true")
@ActiveProfiles(value="mysql",inheritProfiles=false)
@SpringBootTest(classes=KaoyanAiApplication.class,properties="app.seed-local=false")
class RbacMysqlIntegrationTests extends RbacIntegrationTests {
  @org.springframework.beans.factory.annotation.Autowired org.springframework.core.env.Environment environment;
  @org.junit.jupiter.api.BeforeEach
  void requireRealMysqlConfiguration() {
    org.junit.jupiter.api.Assertions.assertEquals("com.mysql.cj.jdbc.Driver",environment.getProperty("spring.datasource.driver-class-name"));
    org.junit.jupiter.api.Assertions.assertTrue(environment.getProperty("spring.datasource.url","").startsWith("jdbc:mysql:"));
  }
}
