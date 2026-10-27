package com.diagramio.api;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Đổi Authentication do filter xác thực đặt vào thành Caller của domain.
 * Hiện chỉ có filter JWT (principal là UUID người dùng). Khi có filter X-Share-Token (dự kiến),
 * filter đó đặt sẵn principal là Caller và được dùng nguyên.
 */
public final class CallerResolver {

    private CallerResolver() {
    }

    public static Caller resolve(Authentication authentication) {
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
