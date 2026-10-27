package com.diagramio.api.user.dto;

import com.diagramio.domain.user.User;
import java.time.Instant;
import java.util.UUID;

/** Thông tin công khai của người dùng; cố ý không có passwordHash. */
public record UserResponse(UUID id, String username, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.email(), user.createdAt());
    }
}
