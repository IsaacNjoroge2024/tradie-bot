package com.tradie.executor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({IbkrProperties.class, OrderProperties.class, PositionProperties.class})
public class IbkrConfig {
}
