package com.diagramio.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.diagramio.domain.user.User;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final User USER = new User(UUID.randomUUID(), "uet", "uet@gmail.com", "hash", NOW);

    private final JwtTokenProvider provider = providerAt(NOW);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenSetsCurrentUser() throws Exception {
        FilterChain chain = runFilter("Bearer " + provider.issueAccessToken(USER));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(USER.id());
        assertThat(auth.isAuthenticated()).isTrue();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void missingHeaderLeavesRequestAnonymousAndContinues() throws Exception {
        FilterChain chain = runFilter(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void nonBearerHeaderIsIgnored() throws Exception {
        FilterChain chain = runFilter("Basic dXNlcjpwYXNz");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void invalidTokenIsIgnoredAndRequestContinues() throws Exception {
        FilterChain chain = runFilter("Bearer not-a-jwt");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void expiredTokenIsIgnored() throws Exception {
        String expired = providerAt(NOW.minus(Duration.ofHours(1))).issueAccessToken(USER);

        runFilter("Bearer " + expired);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private FilterChain runFilter(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    private static JwtTokenProvider providerAt(Instant now) {
        return new JwtTokenProvider(new JwtProperties(SECRET, 15, 7), Clock.fixed(now, ZoneOffset.UTC));
    }
}
