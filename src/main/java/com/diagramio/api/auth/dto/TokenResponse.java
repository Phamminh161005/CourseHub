package com.diagramio.api.auth.dto;

import com.diagramio.domain.auth.AuthTokens;

public record TokenResponse(String accessToken, String refreshToken, String tokenType) {

    public static TokenResponse from(AuthTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer");
    }
}
