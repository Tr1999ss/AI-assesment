package com.shk.generic.ingestion.store;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param staleAfter     a PENDING claim older than this is treated as abandoned (crashed mid-ingestion);
 *                       must comfortably exceed the slowest real ingestion
 * @param sweepInterval  how often abandoned claims are looked for
 * @param sweepBatchSize max claims cleaned per sweep
 */
@ConfigurationProperties("ingestion.store")
public record StoreProperties(
        @DefaultValue("15m") Duration staleAfter,
        @DefaultValue("5m") Duration sweepInterval,
        @DefaultValue("100") int sweepBatchSize
) {
}
