package com.diagramio.api;

import com.diagramio.api.dto.ErrorResponse;
import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.exception.DiagramNotFoundException;
import com.diagramio.domain.exception.DomainException;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.NotFoundException;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import com.diagramio.domain.exception.UnauthorizedException;
import com.diagramio.domain.exception.UnauthorizedDiagramAccessException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Đổi ngoại lệ nghiệp vụ (và lỗi validate) thành mã HTTP + JSON lỗi thống nhất. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "Dữ liệu sơ đồ đã bị thay đổi bởi phiên làm việc khác. Vui lòng làm mới trang!", request);
    }

    @ExceptionHandler(UnauthorizedDiagramAccessException.class)
    public ResponseEntity<ErrorResponse> handleDiagramAccess(
            UnauthorizedDiagramAccessException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(DiagramNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDiagramNotFound(
            DiagramNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest request) {
        return build(statusOf(ex), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getName() + ": invalid value", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private HttpStatus statusOf(DomainException ex) {
        if (ex instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof ForbiddenException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof UnauthorizedException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof ConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof InvalidInputException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
