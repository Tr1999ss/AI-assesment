package com.shk.generic.docs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

/** The docs catalog exists only while {@code docs.enabled} is true; without it the endpoints are absent (404). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "docs.enabled", havingValue = "true", matchIfMissing = true)
public class DocsConfiguration {

    @Bean
    DocsCatalog docsCatalog(ResourcePatternResolver resolver, DocsProperties properties, JsonMapper json) {
        return DocsCatalog.load(resolver, properties.location(), json);
    }
}
