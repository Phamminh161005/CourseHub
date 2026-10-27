package com.diagramio.api.auth;

import com.diagramio.api.auth.dto.LoginRequest;
import com.diagramio.api.auth.dto.RefreshTokenRequest;
import com.diagramio.api.auth.dto.RegisterRequest;
import com.diagramio.api.auth.dto.TokenResponse;
import com.diagramio.api.user.dto.UserResponse;
import com.diagramio.config.OpenApiConfig;
import com.diagramio.domain.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, refresh token, đăng xuất")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đăng ký tài khoản mới")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request.username(), request.email(), request.password()));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập bằng email/mật khẩu, cấp access token và refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh-token")
    @SecurityRequirements
    @Operation(summary = "Đổi refresh token lấy cặp token mới (token cũ bị thu hồi)")
    public TokenResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Thu hồi refresh token hiện tại")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public void logout(@AuthenticationPrincipal UUID userId, @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(userId, request.refreshToken());
    }
}
