package com.diagramio.infrastructure;

import com.diagramio.domain.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Cài đặt TransactionRunner bằng TransactionTemplate: ngoại lệ runtime sẽ rollback toàn bộ. */
@Component
public class TransactionTemplateRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public TransactionTemplateRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
