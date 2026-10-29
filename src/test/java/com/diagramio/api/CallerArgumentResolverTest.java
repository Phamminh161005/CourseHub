package com.diagramio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.exception.UnauthorizedException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class CallerArgumentResolverTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DIAGRAM_ID = UUID.randomUUID();

    private final CallerArgumentResolver resolver = new CallerArgumentResolver();

    @SuppressWarnings("unused")
    static class SampleController {
        void anyCaller(Caller caller) {
        }

        void accountOnly(@CurrentUser UUID userId) {
        }

        void plainUuid(UUID id) {
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsCallerAndCurrentUserParametersOnly() throws Exception {
        assertThat(resolver.supportsParameter(param("anyCaller", Caller.class))).isTrue();
        assertThat(resolver.supportsParameter(param("accountOnly", UUID.class))).isTrue();
        assertThat(resolver.supportsParameter(param("plainUuid", UUID.class))).isFalse();
    }

    @Test
    void noAuthenticationGivesAnonymousCaller() throws Exception {
        assertThat(resolveCaller()).isEqualTo(Caller.anonymous());
    }

    @Test
    void anonymousTokenGivesAnonymousCaller() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(resolveCaller()).isEqualTo(Caller.anonymous());
    }

    @Test
    void jwtLoginGivesUserCallerAndUserId() throws Exception {
        authenticate(Caller.user(USER_ID));

        assertThat(resolveCaller()).isEqualTo(Caller.user(USER_ID));
        assertThat(resolveUserId()).isEqualTo(USER_ID);
    }

    @Test
    void shareLinkWithLoginKeepsBoth() throws Exception {
        Caller combined = new Caller(USER_ID, DIAGRAM_ID, ShareRole.EDIT);
        authenticate(combined);

        assertThat(resolveCaller()).isEqualTo(combined);
        assertThat(resolveUserId()).isEqualTo(USER_ID);
    }

    @Test
    void shareLinkOnlyIsACallerButNotAnAccount() throws Exception {
        authenticate(Caller.shareToken(DIAGRAM_ID, ShareRole.EDIT));

        assertThat(resolveCaller()).isEqualTo(Caller.shareToken(DIAGRAM_ID, ShareRole.EDIT));
        assertThatThrownBy(this::resolveUserId).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void currentUserWithoutAnyAuthenticationIs401() {
        assertThatThrownBy(this::resolveUserId).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void uuidPrincipalIsReadAsUserId() throws Exception {
        authenticate(USER_ID);

        assertThat(resolveCaller()).isEqualTo(Caller.user(USER_ID));
    }

    private static void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private Object resolveCaller() throws Exception {
        return resolver.resolveArgument(param("anyCaller", Caller.class), null, null, null);
    }

    private Object resolveUserId() throws Exception {
        return resolver.resolveArgument(param("accountOnly", UUID.class), null, null, null);
    }

    private static MethodParameter param(String methodName, Class<?> type) throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod(methodName, type);
        return new MethodParameter(method, 0);
    }
}
