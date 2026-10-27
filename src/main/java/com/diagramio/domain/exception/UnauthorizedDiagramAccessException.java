package com.diagramio.domain.exception;

/** Đã xác định được người gọi nhưng không đủ quyền với sơ đồ (HTTP 403). */
public class UnauthorizedDiagramAccessException extends ForbiddenException {
    public UnauthorizedDiagramAccessException(String message) {
        super(message);
    }
}
