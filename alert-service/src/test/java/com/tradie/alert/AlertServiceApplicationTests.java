package com.tradie.alert;

import com.tradie.common.repository.OrderRepository;
import com.tradie.common.repository.PositionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "tradie.telegram.enabled=false",
        "tradie.telegram.bot-token=test-token",
        "tradie.telegram.chat-id=test-chat-id",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
    }
)
class AlertServiceApplicationTests {

    @MockBean
    OrderRepository orderRepository;

    @MockBean
    PositionRepository positionRepository;

    @MockBean
    StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }
}
