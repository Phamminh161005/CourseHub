package com.diagramio.api.auth;

import com.diagramio.api.CallerResolver;
import com.diagramio.api.auth.dto.LoginRequest;
import com.diagramio.api.auth.dto.RefreshTokenRequest;
import com.diagramio.api.auth.dto.RegisterRequest;
import com.diagramio.api.auth.dto.TokenResponse;
import com.diagramio.api.dto.ErrorResponse;
import com.diagramio.api.user.dto.UserResponse;
import com.diagramio.config.OpenApiConfig;
import com.diagramio.domain.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
    @ApiResponse(responseCode = "201", description = "Đã tạo tài khoản")
    @ApiResponse(responseCode = "400", description = "Thiếu trường, email sai định dạng, mật khẩu ngoài 8-72 ký tự",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email hoặc username đã tồn tại",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request.username(), request.email(), request.password()));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập bằng email/mật khẩu, cấp access token và refresh token")
    @ApiResponse(responseCode = "200", description = "Cặp access token và refresh token")
    @ApiResponse(responseCode = "400", description = "Thiếu email hoặc mật khẩu",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sai email hoặc mật khẩu",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh-token")
    @SecurityRequirements
    @Operation(summary = "Đổi refresh token lấy cặp token mới (token cũ bị thu hồi)")
    @ApiResponse(responseCode = "200", description = "Cặp token mới")
    @ApiResponse(responseCode = "400", description = "Thiếu refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token sai, hết hạn hoặc đã dùng",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TokenResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Thu hồi refresh token hiện tại")
    @ApiResponse(responseCode = "204", description = "Đã thu hồi refresh token")
    @ApiResponse(responseCode = "400", description = "Thiếu refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập (chỉ cầm token chia sẻ là chưa đủ)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public void logout(Authentication authentication, @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(CallerResolver.requireUserId(authentication), request.refreshToken());
    }
}
