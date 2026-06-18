package com.tradie.gateway;

import com.tradie.common.repository.AuditLogRepository;
import com.tradie.common.repository.TradeJournalRepository;
import com.tradie.common.repository.TradeSignalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "tradie.webhook.secret=test-secret",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
class ApiGatewayApplicationTests {

    @MockBean
    TradeSignalRepository tradeSignalRepository;

    @MockBean
    AuditLogRepository auditLogRepository;

    @MockBean
    TradeJournalRepository tradeJournalRepository;

    @Test
    void contextLoads() {
    }
}
