package com.shk.generic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Minimal guard for admin endpoints until real authentication (e.g. Spring Security + OAuth2) is added.
 * Requests under {@code admin.path-prefix} must carry {@code admin.api-key} in the {@code admin.header} header.
 * Fails closed: with no key configured, admin endpoints answer 403.
 */
@Slf4j
@Component
public class AdminApiKeyFilter extends OncePerRequestFilter {

    private final AdminSecurityProperties properties;
    private final byte[] expectedKey;

    public AdminApiKeyFilter(AdminSecurityProperties properties) {
        this.properties = properties;
        this.expectedKey = properties.enabled() ? properties.apiKey().getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (!properties.enabled()) {
            log.warn("admin.api-key is not set: admin endpoints under {} are disabled", properties.pathPrefix());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + properties.pathPrefix());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.enabled()) {
            reject(response, HttpStatus.FORBIDDEN, "Admin API is disabled; set admin.api-key to enable it");
            return;
        }
        String provided = request.getHeader(properties.header());
        if (provided == null || !MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            reject(response, HttpStatus.UNAUTHORIZED, "Missing or invalid " + properties.header() + " header");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
