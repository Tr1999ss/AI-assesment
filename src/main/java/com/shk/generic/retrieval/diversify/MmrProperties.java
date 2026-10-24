package com.shk.generic.retrieval.diversify;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param lambda default MMR trade-off: 1 = pure relevance, 0 = pure diversity */
@ConfigurationProperties("retrieval.mmr")
public record MmrProperties(@DefaultValue("0.7") double lambda) {
}
