package com.shk.generic.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void realmRolesBecomeRoleAuthoritiesAndUsernameIsThePrincipal() {
        AbstractAuthenticationToken auth = converter.convert(jwt(Map.of(
                "sub", "1b2c-uuid",
                "preferred_username", "bob",
                "realm_access", Map.of("roles", List.of("kb-editor", "offline_access")))));

        assertThat(auth.getName()).isEqualTo("bob");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_kb-editor", "ROLE_offline_access");
    }

    @Test
    void missingRolesMeansNoAuthoritiesAndSubjectIsTheFallbackName() {
        AbstractAuthenticationToken auth = converter.convert(jwt(Map.of("sub", "service-account-x")));

        assertThat(auth.getName()).isEqualTo("service-account-x");
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void malformedRealmAccessIsIgnored() {
        assertThat(converter.convert(jwt(Map.of("sub", "x", "realm_access", "not-a-map"))).getAuthorities()).isEmpty();
        assertThat(converter.convert(jwt(Map.of("sub", "x", "realm_access", Map.of("roles", "kb-admin"))))
                .getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").claims(c -> c.putAll(claims))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
