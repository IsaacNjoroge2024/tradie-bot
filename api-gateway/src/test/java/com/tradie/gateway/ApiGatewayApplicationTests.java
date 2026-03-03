package com.tradie.gateway;

import com.tradie.common.repository.TradeSignalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    properties = {
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
class ApiGatewayApplicationTests {

    // JPA auto-config is excluded, so JPA repositories are not created.
    // KafkaConfig provides KafkaTemplate; TradeSignalRepository must be mocked.
    @MockBean
    TradeSignalRepository tradeSignalRepository;

    @Test
    void contextLoads() {
    }
}
