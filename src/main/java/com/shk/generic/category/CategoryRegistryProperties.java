package com.shk.generic.category;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param cacheTtl how long category definitions are cached. Changes made through the admin API refresh
 *                 the cache immediately on this instance; other instances pick them up within this TTL.
 */
@ConfigurationProperties("category.registry")
public record CategoryRegistryProperties(@DefaultValue("60s") Duration cacheTtl) {
}
