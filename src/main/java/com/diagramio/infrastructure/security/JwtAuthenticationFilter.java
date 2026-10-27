package com.diagramio.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đọc "Authorization: Bearer <JWT>". Token hợp lệ thì đặt người dùng hiện tại (principal = UUID id).
 *
 * <p>Filter này KHÔNG bao giờ tự từ chối request: thiếu hoặc sai token thì cho đi tiếp, việc trả 401
 * do SecurityConfig quyết định. Nhờ vậy endpoint công khai vẫn chạy và filter khác (X-Share-Token)
 * có thể gắn thêm mà không phải sửa filter này.
 *
 * <p>Không đánh dấu @Component để Spring Boot không đăng ký nó lần thứ hai ngoài chuỗi security;
 * filter được tạo trong SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtTokenProvider.parseUserId(header.substring(BEARER_PREFIX.length()).trim())
                    .ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
