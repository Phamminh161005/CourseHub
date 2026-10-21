package com.diagramio.domain.exception;

/** Chưa đăng nhập, sai thông tin đăng nhập hoặc token không hợp lệ (HTTP 401). */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
