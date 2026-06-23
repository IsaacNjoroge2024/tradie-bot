package com.tradie.strategy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FuturesProperties.class)
public class FuturesConfig {
}
