package org.example.aiintegratedspringbootservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AiIntegratedSpringBootServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
