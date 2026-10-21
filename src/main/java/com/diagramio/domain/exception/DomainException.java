package com.diagramio.domain.exception;

/** Gốc của mọi ngoại lệ nghiệp vụ. Tầng API đổi chúng thành mã HTTP. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
