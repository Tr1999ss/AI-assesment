package com.shk.generic.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApiKeyFilterTest {

    @Test
    void withoutAConfiguredKeyAdminEndpointsAreDisabled() throws Exception {
        MockHttpServletResponse response = run(new AdminSecurityProperties("", "X-Admin-Api-Key", "/api/admin/"),
                "/api/admin/categories", "anything");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @Test
    void nonAdminPathsAreNotFiltered() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(new AdminSecurityProperties("", "X-Admin-Api-Key", "/api/admin/"),
                "/api/search", null, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void correctKeyPassesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        run(new AdminSecurityProperties("s3cret", "X-Admin-Api-Key", "/api/admin/"), "/api/admin/x", "s3cret", chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletResponse run(AdminSecurityProperties props, String uri, String key) throws Exception {
        return run(props, uri, key, new MockFilterChain());
    }

    private static MockHttpServletResponse run(AdminSecurityProperties props, String uri, String key,
                                               FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (key != null) {
            request.addHeader(props.header(), key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AdminApiKeyFilter(props).doFilter(request, response, chain);
        return response;
    }
}
