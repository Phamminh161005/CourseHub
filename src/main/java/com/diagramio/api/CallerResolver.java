package com.diagramio.api;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Đổi Authentication (do filter xác thực đặt vào) thành Caller của domain.
 * Filter X-Share-Token đặt sẵn principal là Caller; filter JWT dùng subject là UUID người dùng.
 */
final class CallerResolver {

    private CallerResolver() {
    }

    static Caller resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication required");
        }
        if (authentication.getPrincipal() instanceof Caller caller) {
            return caller;
        }
        try {
            return Caller.user(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Authentication required");
        }
    }
}
