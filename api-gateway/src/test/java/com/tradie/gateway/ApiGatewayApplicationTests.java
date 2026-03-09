package com.tradie.gateway;

import com.tradie.common.repository.TradeSignalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    properties = {
        // Use H2 in-memory so JpaConfig/@EnableJpaRepositories can initialise without a real DB
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "tradie.webhook.secret=test-secret",
        // No Kafka broker needed in context load test
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
class ApiGatewayApplicationTests {

    // JpaConfig/@EnableJpaRepositories scans for TradeSignalRepository; mock it so
    // no real DB queries are executed during the context load test.
    @MockBean
    TradeSignalRepository tradeSignalRepository;

    @Test
    void contextLoads() {
    }
}
