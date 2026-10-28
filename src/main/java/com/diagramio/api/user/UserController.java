package com.diagramio.api.user;

import com.diagramio.api.CallerResolver;
import com.diagramio.api.dto.ErrorResponse;
import com.diagramio.api.user.dto.ChangePasswordRequest;
import com.diagramio.api.user.dto.UserResponse;
import com.diagramio.config.OpenApiConfig;
import com.diagramio.domain.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Users", description = "Tài khoản của người đang đăng nhập")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Lấy thông tin cá nhân")
    @ApiResponse(responseCode = "200", description = "Thông tin tài khoản")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập (chỉ cầm token chia sẻ là chưa đủ)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(userService.getById(CallerResolver.requireUserId(authentication)));
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi mật khẩu (yêu cầu mật khẩu cũ)")
    @ApiResponse(responseCode = "204", description = "Đã đổi mật khẩu")
    @ApiResponse(responseCode = "400", description = "Mật khẩu cũ sai hoặc mật khẩu mới ngoài 8-72 ký tự",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập (chỉ cầm token chia sẻ là chưa đủ)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void changePassword(Authentication authentication,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(CallerResolver.requireUserId(authentication),
                request.currentPassword(), request.newPassword());
    }
}
