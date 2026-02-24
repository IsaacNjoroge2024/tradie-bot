package com.tradie.alert;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
class AlertServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
