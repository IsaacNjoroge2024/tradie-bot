package com.tradie.strategy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(FuturesProperties.class)
public class FuturesConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
