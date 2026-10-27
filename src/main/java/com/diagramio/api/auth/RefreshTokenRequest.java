package com.diagramio.api.auth;

import jakarta.validation.constraints.NotBlank;

/** Dùng cho cả refresh-token và logout. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
