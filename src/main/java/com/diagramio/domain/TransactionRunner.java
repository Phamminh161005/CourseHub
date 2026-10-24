package com.diagramio.domain;

import java.util.function.Supplier;

/**
 * Chạy một khối nghiệp vụ trong một transaction: tất cả thành công hoặc rollback toàn bộ.
 * Domain không được dùng @Transactional nên tầng infrastructure cài đặt interface này.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
