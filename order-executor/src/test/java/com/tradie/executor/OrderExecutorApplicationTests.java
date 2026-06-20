package com.tradie.executor;

import com.tradie.common.repository.AuditLogRepository;
import com.tradie.common.repository.CurrencyPairRepository;
import com.tradie.common.repository.OHLCVCandleRepository;
import com.tradie.common.repository.OrderRepository;
import com.tradie.common.repository.PositionRepository;
import com.tradie.common.repository.TradeJournalRepository;
import com.tradie.common.repository.TradeSignalRepository;
import com.tradie.executor.ibkr.IBConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
class OrderExecutorApplicationTests {

    // Mock the connection manager so no real IBKR connection is attempted
    @MockBean
    IBConnectionManager ibConnectionManager;

    @MockBean
    TradeSignalRepository tradeSignalRepository;

    @MockBean
    PositionRepository positionRepository;

    @MockBean
    OrderRepository orderRepository;

    @MockBean
    OHLCVCandleRepository ohlcvCandleRepository;

    @MockBean
    StringRedisTemplate stringRedisTemplate;

    @MockBean
    AuditLogRepository auditLogRepository;

    @MockBean
    TradeJournalRepository tradeJournalRepository;

    @MockBean
    CurrencyPairRepository currencyPairRepository;

    @Test
    void contextLoads() {
    }
}
