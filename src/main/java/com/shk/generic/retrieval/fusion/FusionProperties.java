package com.shk.generic.retrieval.fusion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param rrfK RRF damping constant: higher flattens the advantage of top ranks (60 is the common default) */
@ConfigurationProperties("retrieval.fusion")
public record FusionProperties(@DefaultValue("60") int rrfK) {
}
