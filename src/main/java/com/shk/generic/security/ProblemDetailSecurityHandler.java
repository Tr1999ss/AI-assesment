package com.shk.generic.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 401 and 403 as RFC 9457 problem details, like every other API error. The 401 keeps the
 * {@code WWW-Authenticate: Bearer ...} header that OAuth clients rely on.
 */
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        bearerEntryPoint.commence(request, response, authException);   // status + WWW-Authenticate
        write(response, HttpStatus.UNAUTHORIZED, request,
                "A valid bearer token from the identity provider is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        write(response, HttpStatus.FORBIDDEN, request, "Your account does not have the role this operation requires");
    }

    private static void write(HttpServletResponse response, HttpStatus status, HttpServletRequest request,
                              String detail) throws IOException {
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail,
                        request.getRequestURI().replace("\"", "")));
    }
}
