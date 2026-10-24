package com.shk.generic.ingestion.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turn off with {@code ingestion.store.sweeper-enabled=false} (e.g. in tests that call the sweeper directly). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "ingestion.store.sweeper-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
