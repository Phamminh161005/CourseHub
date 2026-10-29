package com.diagramio.api;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Cấp người gọi cho controller, để controller không tự đọc Authentication:
 * <ul>
 *   <li>tham số {@link Caller}: luôn có giá trị; chưa đăng nhập và không có link thì là {@link Caller#anonymous()}.
 *       Endpoint không công khai đã bị SecurityConfig chặn 401 trước khi tới controller.</li>
 *   <li>tham số {@code @CurrentUser UUID}: id người dùng đăng nhập; không có tài khoản thì 401.</li>
 * </ul>
 * Filter JWT đặt principal là {@code Caller.user(id)}, filter X-Share-Token đặt Caller gộp quyền link.
 */
public class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Caller.class
                || (parameter.hasParameterAnnotation(CurrentUser.class) && parameter.getParameterType() == UUID.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Caller caller = currentCaller(SecurityContextHolder.getContext().getAuthentication());
        if (parameter.hasParameterAnnotation(CurrentUser.class)) {
            if (caller.userId() == null) {
                throw new UnauthorizedException("Authentication required");
            }
            return caller.userId();
        }
        return caller;
    }

    static Caller currentCaller(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Caller.anonymous();
        }
        if (authentication.getPrincipal() instanceof Caller caller) {
            return caller;
        }
        // Principal khác (ví dụ test đặt thẳng UUID): tên là id người dùng
        try {
            return Caller.user(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Authentication required");
        }
    }
}
