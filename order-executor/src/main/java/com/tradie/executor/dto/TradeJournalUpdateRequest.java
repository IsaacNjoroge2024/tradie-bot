package com.tradie.executor.dto;

import java.util.List;

public record TradeJournalUpdateRequest(
        String notes,
        String setupQuality,
        String executionQuality,
        List<String> tags,
        String marketCondition,
        String newsContext,
        String entryChartUrl,
        String exitChartUrl
) {}
