package org.example.aiintegratedspringbootservice;

import org.springframework.boot.SpringApplication;

public class TestAiIntegratedSpringBootServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(AiIntegratedSpringBootServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
