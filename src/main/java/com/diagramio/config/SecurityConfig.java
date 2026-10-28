package com.diagramio.config;

import com.diagramio.api.dto.ErrorResponse;
import com.diagramio.domain.share.ShareService;
import com.diagramio.infrastructure.security.JwtAuthenticationFilter;
import com.diagramio.infrastructure.security.JwtTokenProvider;
import com.diagramio.infrastructure.security.ShareTokenAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Bảo mật: stateless, endpoint công khai được mở, còn lại phải xác thực.
 * /api/v1/shares/** công khai để xem sơ đồ qua link chia sẻ; GET /api/v1/diagrams/* công khai cho sơ đồ public.
 * Người gọi được xác định bởi filter JWT rồi filter X-Share-Token (link chia sẻ); hai filter không tự
 * từ chối, request chưa xác thực vào endpoint cần đăng nhập sẽ nhận 401 dạng JSON.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",
            "/api/v1/shares/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   ShareService shareService,
                                                   ObjectMapper objectMapper)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(jsonUnauthorizedEntryPoint(objectMapper)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/diagrams/*").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ShareTokenAuthenticationFilter(shareService), JwtAuthenticationFilter.class);
        return http.build();
    }

    /** Trả 401 cùng khung JSON với GlobalExceptionHandler. */
    private static AuthenticationEntryPoint jsonUnauthorizedEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            HttpStatus status = HttpStatus.UNAUTHORIZED;
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(Instant.now(), status.value(),
                    status.getReasonPhrase(), "Authentication required", request.getRequestURI()));
        };
    }
}
