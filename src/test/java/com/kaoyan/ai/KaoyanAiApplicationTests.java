package com.kaoyan.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:context;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.seed-local=false"
    })
class KaoyanAiApplicationTests {
  @Test
  void contextLoads() {}
}
