package com.diagramio.domain.exception;

/** Xung đột dữ liệu, ví dụ sai version khi sửa sơ đồ hoặc email đã tồn tại (HTTP 409). */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
