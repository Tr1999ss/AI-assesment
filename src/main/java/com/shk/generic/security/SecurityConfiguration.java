package com.shk.generic.security;

import com.shk.generic.docs.DocsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless OAuth2 resource server: every request is authenticated by a Keycloak-issued JWT
 * (issuer and audience checked via {@code spring.security.oauth2.resourceserver.jwt.*}).
 * <p>
 * All URL rules live here; {@code /api/docs/**} follows {@code docs.public}. MCP is the exception: every tool call arrives on the same endpoint,
 * so tools enforce their roles with {@code @PreAuthorize} (method security).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, KeycloakJwtAuthenticationConverter jwtConverter,
                                    DocsProperties docs) throws Exception {
        ProblemDetailSecurityHandler problems = new ProblemDetailSecurityHandler();
        http
                // Bearer tokens are not sent automatically by browsers, so CSRF protection doesn't apply.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/categories").permitAll();
                    // the docs describe the security setup and dev credentials: readers only, unless docs.public
                    var docsRule = auth.requestMatchers(HttpMethod.GET, "/api/docs", "/api/docs/**");
                    if (docs.publicAccess()) {
                        docsRule.permitAll();
                    } else {
                        docsRule.hasRole(Roles.READER);
                    }
                    auth.requestMatchers("/api/admin/**").hasRole(Roles.ADMIN)
                            .requestMatchers("/api/ingest/**").hasRole(Roles.EDITOR)
                            .requestMatchers("/api/search", "/api/search/**", "/api/ask").hasRole(Roles.READER)
                            .requestMatchers("/sse", "/mcp/**").hasRole(Roles.READER)
                            .anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }

    /** Mirrors the Keycloak composites, so authorization doesn't depend on how roles were assigned. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(Roles.ADMIN).implies(Roles.EDITOR)
                .role(Roles.EDITOR).implies(Roles.READER)
                .build();
    }
}
