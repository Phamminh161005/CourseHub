package com.diagramio.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.share.ShareService;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ShareTokenAuthenticationFilterTest {

    private static final UUID DIAGRAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final ShareService shareService = mock(ShareService.class);
    private final ShareTokenAuthenticationFilter filter = new ShareTokenAuthenticationFilter(shareService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenWithoutLoginSetsShareCaller() throws Exception {
        when(shareService.resolveShareToken("edit-token"))
                .thenReturn(Optional.of(Caller.shareToken(DIAGRAM_ID, ShareRole.EDIT)));

        FilterChain chain = runFilter("edit-token");

        assertThat(principal()).isEqualTo(new Caller(null, DIAGRAM_ID, ShareRole.EDIT));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void validTokenAfterJwtLoginKeepsUserId() throws Exception {
        loginAs(USER_ID);
        when(shareService.resolveShareToken("view-token"))
                .thenReturn(Optional.of(Caller.shareToken(DIAGRAM_ID, ShareRole.VIEW)));

        runFilter("view-token");

        assertThat(principal()).isEqualTo(new Caller(USER_ID, DIAGRAM_ID, ShareRole.VIEW));
    }

    @Test
    void missingHeaderKeepsJwtPrincipal() throws Exception {
        loginAs(USER_ID);

        FilterChain chain = runFilter(null);

        assertThat(principal()).isEqualTo(Caller.user(USER_ID));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void unknownOrRevokedTokenIsIgnoredAndRequestContinues() throws Exception {
        when(shareService.resolveShareToken("revoked")).thenReturn(Optional.empty());

        FilterChain chain = runFilter("revoked");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    private static void loginAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(Caller.user(userId), null, List.of()));
    }

    private static Object principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        return auth.getPrincipal();
    }

    private FilterChain runFilter(String shareToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/diagrams/" + DIAGRAM_ID);
        if (shareToken != null) {
            request.addHeader(ShareTokenAuthenticationFilter.HEADER, shareToken);
        }
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }
}
