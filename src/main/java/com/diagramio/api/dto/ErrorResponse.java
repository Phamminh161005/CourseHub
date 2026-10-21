package com.diagramio.api.dto;

import java.time.Instant;

/** Khung JSON lỗi thống nhất cho toàn bộ API. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
