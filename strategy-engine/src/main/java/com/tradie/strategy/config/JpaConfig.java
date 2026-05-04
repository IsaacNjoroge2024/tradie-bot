package com.tradie.strategy.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.tradie.common.entity")
@EnableJpaRepositories(basePackages = "com.tradie.common.repository")
public class JpaConfig {
}
