package com.gouyan.web.controller.hall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
class GouyanHallApplicationTests {

    @Test
    void contextLoads() {
    }

}
