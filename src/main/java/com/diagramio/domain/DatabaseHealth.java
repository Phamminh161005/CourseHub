package com.diagramio.domain;

/**
 * Kiểm tra CSDL còn trả lời được không (dùng cho /api/v1/health và Docker healthcheck).
 * Tầng infrastructure cài đặt interface này.
 */
public interface DatabaseHealth {

    boolean isUp();
}
