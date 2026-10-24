package com.shk.generic.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Keycloak puts realm roles in {@code realm_access.roles}, which Spring's default converter ignores.
 * Maps each role to {@code ROLE_<role>} and uses {@code preferred_username} as the principal name.
 */
@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String REALM_ACCESS = "realm_access";
    static final String ROLES = "roles";
    static final String USERNAME = "preferred_username";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String name = jwt.hasClaim(USERNAME) ? jwt.getClaimAsString(USERNAME) : jwt.getSubject();
        return new JwtAuthenticationToken(jwt, realmRoles(jwt), name);
    }

    static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
        if (!(jwt.getClaims().get(REALM_ACCESS) instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
