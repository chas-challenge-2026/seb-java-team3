package se.comerit.seb.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.config.ApprovalThresholds;
import se.comerit.seb.domain.*;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.exception.PaymentRequestConflictException;
import se.comerit.seb.infrastructure.iban.IbanValidatorService;
import se.comerit.seb.repository.PaymentRepository;
import se.comerit.seb.repository.AccountRepository;
import se.comerit.seb.repository.UserRepository;
import se.comerit.seb.security.AuthenticatedUserContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PaymentService {

    // Ett enda meddelande för saknat konto, ogiltigt id, saknad tenant och annat företags konto,
    // så att konto-id hos andra kunder inte går att kartlägga.
    private static final String FROM_ACCOUNT_NOT_AVAILABLE =
            "Från-kontot finns inte eller tillhör inte ditt företag";
    // Används när användaren aldrig markerat något som sett: då räknas alla beslut.
    private static final LocalDateTime NEVER_SEEN = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ApprovalThresholds thresholds;
    private final AuditService auditService;
    private final IbanValidatorService ibanValidator;

    // Constructor injection: Spring skapar automatiskt en instans av
    // PaymentService och fyller i dessa tre beroenden åt dig, baserat
    // på att de redan är @Service/@Component/@Repository någon annanstans.
    public PaymentService(PaymentRepository paymentRepository,
                          UserRepository userRepository,
                          AccountRepository accountRepository,
                          ApprovalThresholds thresholds,
                          AuditService auditService,
                          IbanValidatorService ibanValidator) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.thresholds = thresholds;
        this.auditService = auditService;
        this.ibanValidator = ibanValidator;
    }

    private String normalizeAndValidateIban(CreatePaymentRequest request) {
        if (request.toIban() == null || request.toIban().isBlank()) {
            throw new IllegalArgumentException("IBAN får inte vara tomt");
        }

        String normalizedIban = ibanValidator.normalize(request.toIban());

        if (!ibanValidator.validateIban(normalizedIban)) {
            throw new IllegalArgumentException(
                    "Invalid IBAN: " + ibanValidator.getIbanErrorMessage(normalizedIban)
            );
        }

        return normalizedIban;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Beloppet måste vara större än noll");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Beloppet får ha högst två decimaler.");
        }
        if (amount.compareTo(new BigDecimal("9999999999999.99")) > 0) {
            throw new IllegalArgumentException("Beloppet är för stort.");
        }
    }

    private void validateFromAccountBelongsToTenant(Long fromAccountId, Long tenantId) {
        if (fromAccountId == null) {
            throw new IllegalArgumentException("Från-konto måste anges");
        }

        // Utan företag finns inget att jämföra kontot mot. Spring Datas härledda existsBy...-fråga
        // översätter dessutom ett null-argument till "tenant_id IS NULL" och skulle då släppa igenom
        // konton som saknar företag (se AccountRepositoryTenantTest). tenantId kommer från JWT och
        // ska alltid finnas, men kontrollen får inte vila på det.
        if (tenantId == null) {
            throw new IllegalArgumentException(FROM_ACCOUNT_NOT_AVAILABLE);
        }

        // accounts.id är INT i databasen. Ett för stort Long-värde skulle annars
        // "slå runt" vid omvandlingen och kunna peka på ett helt annat konto.
        if (fromAccountId <= 0 || fromAccountId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(FROM_ACCOUNT_NOT_AVAILABLE);
        }

        boolean ownsAccount = accountRepository.existsByIdAndTenantId(fromAccountId.intValue(), tenantId);

        // Samma meddelande oavsett om kontot saknas eller tillhör ett annat företag,
        // så att man inte kan lista ut vilka konto-id som finns hos andra kunder.
        if (!ownsAccount) {
            throw new IllegalArgumentException(FROM_ACCOUNT_NOT_AVAILABLE);
        }
    }

    // Samma regel som för den första tröskeln (ADR 0015): strikt över, så exakt tröskelbeloppet räcker
    // med en attestant (ADR 0016).
    private int requiredAttestants(BigDecimal amount) {
        return amount.compareTo(thresholds.getTwoAttestantThreshold()) > 0 ? 2 : 1;
    }

    // Skaparen väljs aldrig (ADR 0017): en initiator som just blivit attestant kan ha kvar en initiator-token
    // en stund, och en betalning vars steg tillhör skaparen skulle fastna, eftersom skaparen inte får godkänna
    // sin egen. Skaparen räknas bort före kontrollen av antalet, så att "för få attestanter" gäller de
    // attestanter som faktiskt kan godkänna.
    private List<User> findAttestants(Long tenantId, int required, Long creatorId) {
        List<User> attestants = userRepository.findByTenantIdAndRoleOrderByIdAsc(tenantId, Role.ATTESTANT).stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), creatorId))
                .toList();

        // Betalningen stoppas redan här. Annars hade den reserverat pengar i väntan på ett godkännande som
        // ingen kan ge.
        if (attestants.size() < required) {
            throw new NoAttestantAvailableException(tenantId);
        }

        // MVP-regel: ta de första attestanterna, i id-ordning.
        // Dokumenterad avgränsning - riktig urvalslogik (t.ex.
        // arbetsbelastning, roterande tilldelning) är inte MVP.
        return attestants.subList(0, required);
    }

    // Bara INITIATOR. ADMIN är en administrativ roll och flyttar inga pengar (ADR 0017).
    @PreAuthorize("hasRole('INITIATOR')")
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        return createPayment(request, null);
    }

    @PreAuthorize("hasRole('INITIATOR')")
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {

        String normalizedIban = normalizeAndValidateIban(request);
        validateAmount(request.amount());
        String reference = request.reference() == null ? "" : request.reference();
        if (reference.codePointCount(0, reference.length()) > 140) {
            throw new IllegalArgumentException("Referensen får vara högst 140 tecken.");
        }
        if (reference.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Referensen får inte innehålla NUL.");
        }
        if (idempotencyKey != null) {
            if (!idempotencyKey.matches("[A-Za-z0-9_-]{1,128}")) {
                throw new IllegalArgumentException("Ogiltig Idempotency-Key.");
            }
            // Lås skaparen före uppslagningen. Då serialiseras samma nyckel även om anropen anger
            // olika konton. Låset och betalningen ingår i samma transaktion; rollback lämnar ingen nyckel.
            userRepository.findByIdAndTenantIdForUpdate(request.createdBy(), request.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Användaren finns inte i företaget."));
            var previous = paymentRepository.findByTenantIdAndCreatedByAndIdempotencyKey(
                    request.tenantId(), request.createdBy(), idempotencyKey);
            if (previous.isPresent()) {
                Payment payment = previous.get();
                if (!Objects.equals(payment.getFromAccountId(), request.fromAccountId())
                        || !Objects.equals(payment.getToIban(), normalizedIban)
                        || payment.getAmount().compareTo(request.amount()) != 0
                        || !Objects.equals(payment.getReference(), reference)) {
                    throw new PaymentRequestConflictException();
                }
                return PaymentResponse.from(payment);
            }
        }
        validateFromAccountBelongsToTenant(request.fromAccountId(), request.tenantId());

        Account account = accountRepository
                .findByIdAndTenantIdForUpdate(request.fromAccountId().intValue(), request.tenantId())
                .orElseThrow(() -> new IllegalArgumentException(FROM_ACCOUNT_NOT_AVAILABLE));
        if (account.getBalance() == null) {
            throw new IllegalStateException("Account balance is missing: " + request.fromAccountId());
        }
        // Tillgängligt = saldo minus det som är reserverat för betalningar som väntar på attest. Kontoraden
        // är låst ovan, så två samtidiga betalningar kan inte reservera samma pengar.
        BigDecimal reserved = paymentRepository.sumPendingAmountForAccount(
                request.tenantId(), request.fromAccountId());
        BigDecimal available = account.getBalance().subtract(reserved == null ? BigDecimal.ZERO : reserved);
        if (request.amount().compareTo(available) > 0) {
            throw new IllegalArgumentException("Beloppet överstiger tillgängligt saldo.");
        }

        Payment payment = new Payment(
                request.tenantId(),
                request.fromAccountId(),
                normalizedIban,
                request.amount(),
                reference,
                request.createdBy()
        );
        payment.setIdempotencyKey(idempotencyKey);

        if (request.amount().compareTo(thresholds.getNoAttestantThreshold()) <= 0) {
            // Upp till och med tröskeln (strikt > krävs för attest): ingen attestant
            // behövs, betalningen är klar direkt
            payment.recordAccountDebit(account.getBalance());
            account.setBalance(payment.getBalanceAfterPayment());
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExecutedAt(LocalDateTime.now());

        } else {
            // Över tröskeln: ett godkännandesteg per attestant som krävs, i tur och ordning. Skaparen väljs aldrig.
            List<User> attestants = findAttestants(
                    request.tenantId(), requiredAttestants(request.amount()), request.createdBy());

            int stepNumber = 1;
            for (User attestant : attestants) {
                payment.addApprovalStep(new ApprovalStep(attestant.getId(), stepNumber++));
            }
            // status är redan PENDING_APPROVAL som default i Payment
        }

        Payment saved = paymentRepository.save(payment);

        String description = "Betalning skapad: %s %s till %s".formatted(
                saved.getAmount(), saved.getCurrency(), saved.getToIban());

        auditService.record(
                saved.getTenantId(),
                saved.getCreatedBy(),
                "CREATE_PAYMENT",
                "PAYMENT",
                saved.getId(),
                description
        );

        return PaymentResponse.from(saved);
    }

    // Badge för initiatorn: samma mönster som ApprovalService.countPendingByAttestant, men databasen
    // räknar. Räknar beslut som fattats efter att användaren senast markerade sina betalningar som
    // sedda. Användare och företag kommer från JWT, aldrig från anropet.
    @PreAuthorize("hasRole('INITIATOR')")
    @Transactional(readOnly = true)
    public int countChangedForInitiator(AuthenticatedUserContext user) {
        LocalDateTime seenAt = userRepository
                .findPaymentsSeenAt(user.userId(), user.tenantId())
                .orElse(NEVER_SEEN);
        return (int) paymentRepository.countDecidedByInitiator(
                user.tenantId(), user.userId(), seenAt);
    }

    // Initiatorn har sett sina ändrade betalningar: badgen nollas tills nästa beslut fattas.
    @PreAuthorize("hasRole('INITIATOR')")
    @Transactional
    public void markChangedSeen(AuthenticatedUserContext user) {
        userRepository.markPaymentsSeen(user.userId(), user.tenantId(), LocalDateTime.now());
    }

}
