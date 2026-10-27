package com.diagramio.api;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Đổi Authentication do filter xác thực đặt vào thành Caller của domain.
 * Filter JWT đặt principal là UUID người dùng; filter X-Share-Token đặt sẵn principal là Caller
 * và được dùng nguyên.
 */
public final class CallerResolver {

    private CallerResolver() {
    }

    public static Caller resolve(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
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

    /** Dùng cho endpoint công khai: chưa đăng nhập thì là Caller.anonymous() thay vì 401. */
    public static Caller resolveOrAnonymous(Authentication authentication) {
        return isAuthenticated(authentication) ? resolve(authentication) : Caller.anonymous();
    }

    /** Endpoint cần tài khoản (sơ đồ của tôi, tạo sơ đồ, quản lý chia sẻ): chỉ cầm token chia sẻ là chưa đủ. */
    public static UUID requireUserId(Authentication authentication) {
        UUID userId = resolve(authentication).userId();
        if (userId == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return userId;
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
