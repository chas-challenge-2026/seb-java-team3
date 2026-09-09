package se.comerit.seb.service;

import org.junit.jupiter.api.Test;
import se.comerit.seb.config.ApprovalThresholds;
import se.comerit.seb.domain.*;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    @Test
    void amountUnderThreshold_shouldCompleteImmediately() {

        // ARRANGE: bygg upp testmiljön
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalThresholds thresholds = new ApprovalThresholds();
        thresholds.setNoAttestantThreshold(new BigDecimal("5000"));
        thresholds.setTwoAttestantThreshold(new BigDecimal("10000"));

        // "När paymentRepository.save(...) anropas med vad som helst,
        //  returnera bara samma objekt tillbaka" - vi testar inte
        //  databasen här, bara vår egen logik i PaymentService.
        when(paymentRepo.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(paymentRepo, userRepo, thresholds, auditService);

        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, "SE8550000000054910000003",
                new BigDecimal("3000"), "Testfaktura", 1L
        );

        // ACT: kör koden vi testar
        PaymentResponse response = service.createPayment(request);

        // ASSERT: verifiera att resultatet är rätt
        assertEquals(PaymentStatus.COMPLETED, response.status());
        verify(userRepo, never()).findByTenantIdAndRole(any(), any());
        // ^ vi ska ALDRIG slå upp en attestant för ett belopp under tröskeln
    }

    @Test
    void amountOverThreshold_shouldRequireAttestant() {

        // ARRANGE
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalThresholds thresholds = new ApprovalThresholds();
        thresholds.setNoAttestantThreshold(new BigDecimal("5000"));
        thresholds.setTwoAttestantThreshold(new BigDecimal("10000"));

        // Bygg en "låtsas-attestant" som vår mock ska returnera
        User attestant = new User(1L, "Johan Berg", "johan@malmobygg.se", null, Role.ATTESTANT);

        // "När userRepository.findByTenantIdAndRole anropas med
        //  tenant 1 och ATTESTANT, ge tillbaka en lista med vår
        //  låtsas-attestant" - vi testar inte den riktiga databasen,
        //  bara att PaymentService använder svaret rätt.
        when(userRepo.findByTenantIdAndRole(1L, Role.ATTESTANT))
                .thenReturn(List.of(attestant));

        when(paymentRepo.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(paymentRepo, userRepo, thresholds, auditService);

        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, "SE8550000000054910000003",
                new BigDecimal("7500"), "Testfaktura", 1L
        );

        // ACT
        PaymentResponse response = service.createPayment(request);

        // ASSERT
        assertEquals(PaymentStatus.PENDING_APPROVAL, response.status());
    }

    @Test
    void zeroAmount_shouldThrowException() {

        // ARRANGE
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        ApprovalThresholds thresholds = new ApprovalThresholds();
        AuditService auditService = mock(AuditService.class);

        PaymentService service = new PaymentService(paymentRepo, userRepo, thresholds, auditService);

        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, "SE8550000000054910000003",
                BigDecimal.ZERO, "Testfaktura", 1L
        );

        // ACT + ASSERT (i samma rad denna gång - se förklaring nedan)
        assertThrows(IllegalArgumentException.class, () -> service.createPayment(request));
    }

    @Test
    void createPayment_shouldRecordAuditEntry() {

        // ARRANGE
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        AuditService auditService = mock(AuditService.class);

        ApprovalThresholds thresholds = new ApprovalThresholds();
        thresholds.setNoAttestantThreshold(new BigDecimal("5000"));
        thresholds.setTwoAttestantThreshold(new BigDecimal("10000"));

        when(paymentRepo.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService service = new PaymentService(paymentRepo, userRepo, thresholds, auditService);

        CreatePaymentRequest request = new CreatePaymentRequest(
                1L, 1L, "SE8550000000054910000003",
                new BigDecimal("3000"), "Testfaktura", 1L
        );

        // ACT
        service.createPayment(request);

        // ASSERT: bekräfta att audit-loggning faktiskt skedde, med rätt action/entityType
        verify(auditService).record(
                eq(1L),                 // tenantId
                eq(1L),                 // userId (createdBy)
                eq("CREATE_PAYMENT"),   // action
                eq("PAYMENT"),          // entityType
                any(),                  // entityId (vi bryr oss inte om exakt vilket, bara att något skickas)
                anyString()             // description
        );
    }
}