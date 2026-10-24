package com.shk.generic.retrieval.transform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param multiQueryVariants    alternative phrasings MULTI_QUERY asks for (the original is always searched too)
 * @param maxMultiQueryVariants hard cap, whatever the model returns
 * @param hydeMaxChars          cap on the hypothetical passage length
 * @param rewriteMaxChars       cap on the rewritten query length
 */
@ConfigurationProperties("retrieval.transform")
public record TransformProperties(
        @DefaultValue("3") int multiQueryVariants,
        @DefaultValue("5") int maxMultiQueryVariants,
        @DefaultValue("800") int hydeMaxChars,
        @DefaultValue("300") int rewriteMaxChars
) {
}
