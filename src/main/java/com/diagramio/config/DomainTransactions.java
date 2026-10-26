package com.diagramio.config;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Bọc service của domain trong transaction mà domain không cần @Transactional.
 * Mọi method public của service chạy trong một transaction; gặp RuntimeException (kể cả
 * ngoại lệ nghiệp vụ) thì rollback toàn bộ.
 *
 * <p>Cách dùng khi khai báo bean trong config: {@code return domainTransactions.wrap(new XxxService(...));}
 */
public class DomainTransactions {

    private final TransactionInterceptor interceptor;

    public DomainTransactions(PlatformTransactionManager transactionManager) {
        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        // Mặc định: PROPAGATION_REQUIRED, rollback với RuntimeException và Error
        source.addTransactionalMethod("*", new RuleBasedTransactionAttribute());
        this.interceptor = new TransactionInterceptor((TransactionManager) transactionManager, source);
    }

    @SuppressWarnings("unchecked")
    public <T> T wrap(T service) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true); // service là class, không có interface
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }
}
