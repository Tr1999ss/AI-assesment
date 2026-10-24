package com.shk.generic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param apiKey     shared secret for admin endpoints; when blank, admin endpoints are disabled entirely
 * @param header     request header carrying the key
 * @param pathPrefix requests under this path require the key
 */
@ConfigurationProperties("admin")
public record AdminSecurityProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("X-Admin-Api-Key") String header,
        @DefaultValue("/api/admin/") String pathPrefix
) {
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
