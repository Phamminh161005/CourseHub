package com.diagramio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diagramio.domain.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class DomainTransactionsTest {

    /** Giả lập một service của domain: class thường, không annotation. */
    static class SampleService {
        public String succeed() {
            return "done";
        }

        public void failWithDomainError() {
            throw new ConflictException("stale version");
        }
    }

    private PlatformTransactionManager transactionManager;
    private TransactionStatus status;
    private SampleService service;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        service = new DomainTransactions(transactionManager).wrap(new SampleService());
    }

    @Test
    void wrappedServiceKeepsItsType() {
        assertThat(service).isInstanceOf(SampleService.class);
    }

    @Test
    void successfulCallIsCommitted() {
        assertThat(service.succeed()).isEqualTo("done");

        verify(transactionManager).commit(status);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void domainExceptionRollsBackAndIsRethrown() {
        assertThatThrownBy(() -> service.failWithDomainError())
                .isInstanceOf(ConflictException.class);

        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(any());
    }
}
