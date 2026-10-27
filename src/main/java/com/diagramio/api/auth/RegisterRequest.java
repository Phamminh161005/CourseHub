package com.diagramio.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt chỉ dùng 72 byte đầu của mật khẩu
        @NotBlank @Size(min = 8, max = 72) String password) {
}
