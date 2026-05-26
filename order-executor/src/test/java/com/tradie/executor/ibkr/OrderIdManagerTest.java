package com.tradie.executor.ibkr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIdManagerTest {

    private OrderIdManager orderIdManager;

    @BeforeEach
    void setUp() {
        orderIdManager = new OrderIdManager();
    }

    @Test
    void onNextValidId_setsStartingId() {
        orderIdManager.onNextValidId(100);
        assertThat(orderIdManager.peekNextOrderId()).isEqualTo(100);
    }

    @Test
    void getNextOrderId_incrementsAfterEachCall() {
        orderIdManager.onNextValidId(50);

        assertThat(orderIdManager.getNextOrderId()).isEqualTo(50);
        assertThat(orderIdManager.getNextOrderId()).isEqualTo(51);
        assertThat(orderIdManager.getNextOrderId()).isEqualTo(52);
    }

    @Test
    void peekNextOrderId_doesNotIncrement() {
        orderIdManager.onNextValidId(200);

        assertThat(orderIdManager.peekNextOrderId()).isEqualTo(200);
        assertThat(orderIdManager.peekNextOrderId()).isEqualTo(200);
    }

    @Test
    void onNextValidId_resetsCounter_onReconnect() {
        orderIdManager.onNextValidId(10);
        orderIdManager.getNextOrderId(); // consumes 10
        orderIdManager.getNextOrderId(); // consumes 11

        // Simulate reconnect — IBKR provides new starting ID
        orderIdManager.onNextValidId(300);
        assertThat(orderIdManager.peekNextOrderId()).isEqualTo(300);
    }

    @Test
    void initialState_isZero() {
        assertThat(orderIdManager.peekNextOrderId()).isEqualTo(0);
    }
}
