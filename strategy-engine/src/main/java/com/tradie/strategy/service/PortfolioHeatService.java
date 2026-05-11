package com.tradie.strategy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradie.common.entity.Position;
import com.tradie.common.repository.PositionRepository;
import com.tradie.strategy.dto.PortfolioHeatData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PortfolioHeatService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioHeatService.class);
    private static final String HEAT_KEY = "portfolio:heat";

    private final PositionRepository positionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AccountService accountService;

    public PortfolioHeatService(PositionRepository positionRepository,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 AccountService accountService) {
        this.positionRepository = positionRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.accountService = accountService;
    }

    public double getCurrentHeatPct() {
        return getHeatData().totalHeatPct();
    }

    public PortfolioHeatData getHeatData() {
        try {
            String cached = redisTemplate.opsForValue().get(HEAT_KEY);
            if (cached != null) {
                return objectMapper.readValue(cached, PortfolioHeatData.class);
            }
        } catch (Exception e) {
            log.debug("Could not read portfolio heat from Redis, recalculating: {}", e.getMessage());
        }
        return calculateAndStore();
    }

    public PortfolioHeatData calculateAndStore() {
        BigDecimal accountValue = accountService.getAccountInfo().accountValue();
        List<Position> openPositions = positionRepository.findByStatus(Position.PositionStatus.OPEN);

        List<PortfolioHeatData.PositionHeatEntry> entries = openPositions.stream()
                .filter(p -> p.getStopLoss() != null
                        && p.getEntryPrice() != null
                        && p.getQuantity() != null)
                .map(p -> {
                    BigDecimal stopDistance = p.getEntryPrice().subtract(p.getStopLoss()).abs();
                    BigDecimal riskAmount = p.getQuantity().multiply(stopDistance);
                    double riskPct = accountValue.compareTo(BigDecimal.ZERO) > 0
                            ? riskAmount.multiply(BigDecimal.valueOf(100))
                                        .divide(accountValue, 4, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;
                    return new PortfolioHeatData.PositionHeatEntry(
                            p.getSymbol(), riskAmount, riskPct);
                })
                .collect(Collectors.toList());

        double totalHeat = entries.stream()
                .mapToDouble(PortfolioHeatData.PositionHeatEntry::riskPct)
                .sum();

        PortfolioHeatData data = new PortfolioHeatData(totalHeat, entries, Instant.now());

        try {
            redisTemplate.opsForValue().set(
                    HEAT_KEY, objectMapper.writeValueAsString(data), Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("Failed to cache portfolio heat in Redis: {}", e.getMessage());
        }

        return data;
    }
}
