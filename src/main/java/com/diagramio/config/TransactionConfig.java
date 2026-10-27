package com.diagramio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Transaction dùng chung cho mọi service của domain (xem DomainTransactions). */
@Configuration
public class TransactionConfig {

    @Bean
    public DomainTransactions domainTransactions(PlatformTransactionManager transactionManager) {
        return new DomainTransactions(transactionManager);
    }
}
