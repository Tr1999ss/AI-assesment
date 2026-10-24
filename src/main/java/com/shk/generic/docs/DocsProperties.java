package com.shk.generic.docs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * In-app documentation viewer ({@code /api/docs}).
 *
 * @param enabled      serve the docs at all; on by default, off in the {@code prod} profile
 * @param publicAccess {@code docs.public}: serve without a token. Off by default because the docs describe the
 *                     security setup and list dev-only credentials; when off, the {@code kb-reader} role is required
 * @param location     where the build put the docs (see the {@code docs-site} resources in pom.xml). Read once at
 *                     startup; requests only ever see what was loaded from here
 */
@ConfigurationProperties("docs")
public record DocsProperties(
        @DefaultValue("true") boolean enabled,
        @Name("public") @DefaultValue("false") boolean publicAccess,
        @DefaultValue("classpath:docs-site/") String location
) {
}
