package com.tradie.executor.ibkr;

import com.tradie.executor.dto.IBPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionTrackerTest {

    private PositionTracker positionTracker;

    @BeforeEach
    void setUp() {
        positionTracker = new PositionTracker();
    }

    @Test
    void updatePosition_addsNewPosition() {
        IBPosition pos = new IBPosition("AAPL", "NASDAQ", 10.0, 150.00, 0.0, 0.0);
        positionTracker.updatePosition(pos);

        assertThat(positionTracker.getOpenPositionCount()).isEqualTo(1);
        assertThat(positionTracker.getPositions()).containsKey("AAPL");
        assertThat(positionTracker.getPositions().get("AAPL").quantity()).isEqualTo(10.0);
    }

    @Test
    void updatePosition_updatesExistingPosition() {
        positionTracker.updatePosition(new IBPosition("AAPL", "NASDAQ", 10.0, 150.00, 0.0, 0.0));
        positionTracker.updatePosition(new IBPosition("AAPL", "NASDAQ", 20.0, 155.00, 0.0, 0.0));

        assertThat(positionTracker.getOpenPositionCount()).isEqualTo(1);
        assertThat(positionTracker.getPositions().get("AAPL").quantity()).isEqualTo(20.0);
        assertThat(positionTracker.getPositions().get("AAPL").avgCost()).isEqualTo(155.00);
    }

    @Test
    void updatePosition_zeroQuantity_removesPosition() {
        positionTracker.updatePosition(new IBPosition("AAPL", "NASDAQ", 10.0, 150.00, 0.0, 0.0));
        positionTracker.updatePosition(new IBPosition("AAPL", "NASDAQ", 0.0, 0.0, 0.0, 0.0));

        assertThat(positionTracker.getOpenPositionCount()).isEqualTo(0);
        assertThat(positionTracker.getPositions()).doesNotContainKey("AAPL");
    }

    @Test
    void getPositions_isUnmodifiable() {
        positionTracker.updatePosition(new IBPosition("AAPL", "NASDAQ", 5.0, 100.0, 0.0, 0.0));

        assertThat(positionTracker.getPositions())
                .isUnmodifiable();
    }

    @Test
    void clear_removesAllPositions() {
        positionTracker.updatePosition(new IBPosition("AAPL", "NASDAQ", 10.0, 150.0, 0.0, 0.0));
        positionTracker.updatePosition(new IBPosition("MSFT", "NASDAQ", 5.0, 300.0, 0.0, 0.0));

        positionTracker.clear();

        assertThat(positionTracker.getOpenPositionCount()).isEqualTo(0);
        assertThat(positionTracker.getPositions()).isEmpty();
    }

    @Test
    void multiplePositions_trackedIndependently() {
        positionTracker.updatePosition(new IBPosition("AAPL", "NASDAQ", 10.0, 150.0, 0.0, 0.0));
        positionTracker.updatePosition(new IBPosition("MSFT", "NASDAQ", 5.0, 300.0, 0.0, 0.0));
        positionTracker.updatePosition(new IBPosition("GOOG", "NASDAQ", 2.0, 2800.0, 0.0, 0.0));

        assertThat(positionTracker.getOpenPositionCount()).isEqualTo(3);
    }
}
