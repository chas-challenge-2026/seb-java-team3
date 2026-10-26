package se.comerit.seb.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import se.comerit.seb.domain.AuditEntry;
import se.comerit.seb.infrastructure.audit.AuditSignerService;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.AuditRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceSigningTest {

    private final AuditRepository auditRepository = mock(AuditRepository.class);
    private final AuditSignerService signer = mock(AuditSignerService.class);
    private final AuditService auditService = new AuditService(
            auditRepository, mock(UserRepository.class), mock(PaymentRepository.class),
            mock(AccountRepository.class), signer);

    @BeforeEach
    void startTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        when(auditRepository.save(any(AuditEntry.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void record() {
        auditService.record(5L, 42L, "APPROVE_PAYMENT", "PAYMENT", 7L, "Betalning godkänd");
    }

    @Test
    void record_savesTheDatabaseRowAndReturnsIt() {
        AuditEntry saved = auditService.record(5L, 42L, "APPROVE_PAYMENT", "PAYMENT", 7L, "x");

        verify(auditRepository).save(any(AuditEntry.class));
        assertEquals("APPROVE_PAYMENT", saved.getAction());
        assertEquals(7L, saved.getEntityId());
    }

    @Test
    void record_doesNotSignBeforeCommit() {
        record();

        verifyNoInteractions(signer);
    }

    @Test
    void record_signsAfterCommit() {
        record();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(signer).append(42L, "APPROVE_PAYMENT", 7L, "Betalning godkänd");
    }

    @Test
    void record_doesNotSignWhenTransactionRollsBack() {
        record();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(signer);
    }

    @Test
    void record_signsEachEventOnce() {
        record();
        record();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(signer, times(2)).append(42L, "APPROVE_PAYMENT", 7L, "Betalning godkänd");
    }

    @Test
    void record_withoutActiveTransactionSynchronization_signsDirectly() {
        TransactionSynchronizationManager.clearSynchronization();

        record();

        verify(signer).append(42L, "APPROVE_PAYMENT", 7L, "Betalning godkänd");
    }
}
