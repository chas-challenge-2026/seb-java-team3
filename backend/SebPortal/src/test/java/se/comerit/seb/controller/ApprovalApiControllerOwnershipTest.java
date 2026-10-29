package se.comerit.seb.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.Account;
import se.comerit.seb.domain.ApprovalStep;
import se.comerit.seb.domain.ApprovalStepStatus;
import se.comerit.seb.domain.Payment;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.domain.Role;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.JwtUserContext;
import se.comerit.seb.security.RoleAccessDeniedHandler;
import se.comerit.seb.service.ApprovalService;
import se.comerit.seb.service.AuditService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// #116:s ägarkontroll genom hela kedjan: JWT-filter -> säkerhetskedjor -> controller ->
// RIKTIGA ApprovalService -> GlobalExceptionHandler. Bara persistenslagret (repositories,
// audit) är mockat, så det är den verkliga ägarkollen i servicen som ger 403 här - till
// skillnad från ApprovalApiControllerSecurityTest, där servicen själv är mockad.
@WebMvcTest(ApprovalApiController.class)
@Import({JwtSecurityConfig.class, RoleAccessDeniedHandler.class, JwtService.class, JwtUserContext.class, ApprovalService.class})
class ApprovalApiControllerOwnershipTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ATTESTANT_A_ID = 2L;   // steget tillhör A
    private static final Long ATTESTANT_B_ID = 3L;   // B försöker agera på A:s steg
    private static final Long PAYMENT_ID = 100L;
    private static final Long STEP_ID = 200L;
    private static final Long ACCOUNT_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private AccountRepository accountRepository;

    @MockBean
    private AuditService auditService;

    // ApprovalService slår upp initiatörens namn för attestkorgen
    @MockBean
    private UserRepository userRepository;

    private Payment payment;
    private ApprovalStep step;

    @BeforeEach
    void setUp() {
        payment = new Payment(TENANT_ID, ACCOUNT_ID, "SE8550000000054910000003",
                new BigDecimal("200.00"), "Testfaktura", 1L);
        step = new ApprovalStep(ATTESTANT_A_ID, 1);
        payment.addApprovalStep(step);

        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        ReflectionTestUtils.setField(step, "id", STEP_ID);

        when(paymentRepository.findByApprovalStepIdForUpdate(STEP_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    void approve_asAttestantWhoDoesNotOwnTheStep_returns403AndChangesNothing() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_B_ID)))
                .andExpect(status().isForbidden());

        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        verifyNoInteractions(accountRepository, auditService);
    }

    @Test
    void reject_asAttestantWhoDoesNotOwnTheStep_returns403AndChangesNothing() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/reject", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_B_ID))
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"Fel person\"}"))
                .andExpect(status().isForbidden());

        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(step.getComment());
        verifyNoInteractions(accountRepository, auditService);
    }

    // Attestanten och en admin beslutar om samma betalning samtidigt: den som kommer tvåa ska få 409 och
    // ett begripligt meddelande, inte 500, och ingenting ska ändras en gång till.
    @Test
    void approve_whenThePaymentWasAlreadyRejected_returns409AndChangesNothing() throws Exception {
        step.setStatus(ApprovalStepStatus.REJECTED);
        payment.setStatus(PaymentStatus.REJECTED);

        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Betalningen är redan behandlad. Ladda om sidan för att se det senaste."));

        assertEquals(ApprovalStepStatus.REJECTED, step.getStatus());
        verifyNoInteractions(accountRepository, auditService);
    }

    @Test
    void reject_whenThePaymentWasAlreadyApproved_returns409AndChangesNothing() throws Exception {
        step.setStatus(ApprovalStepStatus.APPROVED);
        payment.setStatus(PaymentStatus.COMPLETED);

        mockMvc.perform(post("/api/approvals/{stepId}/reject", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID))
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"För sent\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Betalningen är redan behandlad. Ladda om sidan för att se det senaste."));

        assertEquals(ApprovalStepStatus.APPROVED, step.getStatus());
        assertNull(step.getComment());
        verifyNoInteractions(accountRepository, auditService);
    }

    @Test
    void approve_asAttestantWhoOwnsTheStep_returns204AndApprovesTheStep() throws Exception {
        Account account = mock(Account.class);
        when(account.getBalance()).thenReturn(new BigDecimal("1000.00"));
        when(accountRepository.findByIdAndTenantIdForUpdate(ACCOUNT_ID.intValue(), TENANT_ID))
                .thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID)))
                .andExpect(status().isNoContent());

        assertEquals(ApprovalStepStatus.APPROVED, step.getStatus());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
    }

    // #228: meddelandet följer med i JSON-bodyn hela vägen till steget
    @Test
    void approve_withAMessage_returns204AndSavesTheMessageOnTheStep() throws Exception {
        Account account = mock(Account.class);
        when(account.getBalance()).thenReturn(new BigDecimal("1000.00"));
        when(accountRepository.findByIdAndTenantIdForUpdate(ACCOUNT_ID.intValue(), TENANT_ID))
                .thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID))
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"Stämmer mot avtalet\"}"))
                .andExpect(status().isNoContent());

        assertEquals("Stämmer mot avtalet", step.getComment());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
    }

    @Test
    void approve_withATooLongMessage_returns400AndChangesNothing() throws Exception {
        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID))
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Kommentaren får vara högst 255 tecken."));

        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        assertNull(step.getComment());
        verifyNoInteractions(accountRepository, auditService);
    }

    // R-04: rätt attestant, men betalningen pekar på ett konto som ägs av ett annat företag.
    // Den tenant-scopade frågan svarar tomt (som databasen gör), så pengarna flyttas inte.
    @Test
    void approve_whenTheAccountBelongsToAnotherTenant_returns400AndDoesNotCompleteThePayment() throws Exception {
        when(accountRepository.findByIdAndTenantIdForUpdate(ACCOUNT_ID.intValue(), TENANT_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + tokenFor(ATTESTANT_A_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Från-kontot finns inte eller tillhör inte ditt företag"));

        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        verifyNoInteractions(auditService);
    }

    private String tokenFor(Long userId) {
        return jwtService.generateToken(new AuthenticatedUserContext(userId, TENANT_ID, Role.ATTESTANT));
    }

    @Test
    void admin_canApproveAnotherAttestantsStepInTheirCompany() throws Exception {
        Account account = mock(Account.class);
        when(account.getBalance()).thenReturn(new BigDecimal("1000.00"));
        when(accountRepository.findByIdAndTenantIdForUpdate(ACCOUNT_ID.intValue(), TENANT_ID))
                .thenReturn(Optional.of(account));
        String token = jwtService.generateToken(new AuthenticatedUserContext(99L, TENANT_ID, Role.ADMIN));
        mockMvc.perform(post("/api/approvals/{stepId}/approve", STEP_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        org.mockito.Mockito.verify(account).setBalance(new BigDecimal("800.00"));
        org.mockito.Mockito.verify(auditService).record(org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.eq("APPROVE_PAYMENT"),
                org.mockito.ArgumentMatchers.eq("PAYMENT"), org.mockito.ArgumentMatchers.eq(PAYMENT_ID),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void admin_canRejectAnotherAttestantsStepInTheirCompany() throws Exception {
        String token = jwtService.generateToken(new AuthenticatedUserContext(99L, TENANT_ID, Role.ADMIN));
        mockMvc.perform(post("/api/approvals/{stepId}/reject", STEP_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertEquals(PaymentStatus.REJECTED, payment.getStatus());
        verifyNoInteractions(accountRepository);
    }

    // Ett annat företags steg ser ut som ett steg som inte finns (400), så att steg-id hos andra kunder
    // inte går att kartlägga (R-04)
    @Test
    void admin_cannotApproveOrRejectAnotherCompanysStep() throws Exception {
        String token = jwtService.generateToken(new AuthenticatedUserContext(99L, TENANT_ID + 1, Role.ADMIN));
        for (String action : new String[] {"approve", "reject"}) {
            mockMvc.perform(post("/api/approvals/{stepId}/" + action, STEP_ID)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Approval step not found: " + STEP_ID));
        }
        assertEquals(ApprovalStepStatus.PENDING, step.getStatus());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payment.getStatus());
        verifyNoInteractions(accountRepository, auditService);
    }
}
