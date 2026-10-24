package com.shk.generic;

import com.shk.generic.security.KeycloakJwtAuthenticationConverter;
import com.shk.generic.security.Roles;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Mock bearer tokens shaped like Keycloak's ({@code realm_access.roles}), mapped to authorities by the
 * application's real {@link KeycloakJwtAuthenticationConverter}. Only the composite-free role is set,
 * so tests also exercise the role hierarchy.
 */
public final class TestTokens {

    private static final KeycloakJwtAuthenticationConverter CONVERTER = new KeycloakJwtAuthenticationConverter();

    private TestTokens() {
    }

    public static JwtRequestPostProcessor reader() {
        return user("alice", Roles.READER);
    }

    public static JwtRequestPostProcessor editor() {
        return user("bob", Roles.EDITOR);
    }

    public static JwtRequestPostProcessor admin() {
        return user("carol", Roles.ADMIN);
    }

    public static JwtRequestPostProcessor user(String username, String... roles) {
        return jwt()
                .jwt(token -> token
                        .subject(username)
                        .claim("preferred_username", username)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(token -> CONVERTER.convert(token).getAuthorities());
    }
}
