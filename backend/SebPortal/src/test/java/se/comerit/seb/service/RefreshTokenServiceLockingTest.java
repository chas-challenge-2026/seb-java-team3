package se.comerit.seb.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import se.comerit.seb.domain.RefreshToken;
import se.comerit.seb.domain.Role;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.RefreshTokenRepository;
import se.comerit.seb.repository.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Granskningen av ADR 0012: all refresh-token-hantering för en användare ska serialiseras med ett lås
// på användarens rad, och låset ska tas INNAN token läses och prövas. Annars kan en spärr av familjen
// missa en efterföljare som skapas samtidigt. Själva samtidigheten bevisas mot riktig Postgres; här
// låses ordningen fast, så att ett oavsiktligt borttaget eller omflyttat lås upptäcks i den vanliga sviten.
class RefreshTokenServiceLockingTest {

    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");
    private static final Long USER_ID = 7L;
    private static final String RAW = "some-raw-token";

    private RefreshTokenRepository tokens;
    private UserRepository users;
    private RefreshTokenService service;
    private String hash;

    @BeforeEach
    void setUp() {
        tokens = mock(RefreshTokenRepository.class);
        users = mock(UserRepository.class);
        service = new RefreshTokenService(tokens, users, Duration.ofDays(7), Clock.fixed(START, ZoneOffset.UTC));
        hash = RefreshTokenService.hash(RAW);

        RefreshToken token = new RefreshToken(USER_ID, hash, UUID.randomUUID(),
                LocalDateTime.ofInstant(START, ZoneOffset.UTC).plusDays(7));
        User user = new User(1L, "Lisa", "lisa@test.se", "hash", Role.INITIATOR);

        when(tokens.findUserIdByTokenHash(hash)).thenReturn(Optional.of(USER_ID));
        when(tokens.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(tokens.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rotate_locksTheUserBeforeTheTokenIsReadAndJudged() {
        service.rotate(RAW);

        InOrder order = inOrder(tokens, users);
        order.verify(tokens).findUserIdByTokenHash(hash);
        order.verify(users).findByIdForUpdate(USER_ID);
        order.verify(tokens).findByTokenHashForUpdate(hash);
    }

    @Test
    void revoke_locksTheUserBeforeTheFamilyIsRevoked() {
        service.revoke(RAW);

        InOrder order = inOrder(tokens, users);
        order.verify(tokens).findUserIdByTokenHash(hash);
        order.verify(users).findByIdForUpdate(USER_ID);
        order.verify(tokens).findByTokenHashForUpdate(hash);
        order.verify(tokens).revokeFamily(any(UUID.class), any(LocalDateTime.class));
    }

    @Test
    void issueForLogin_locksTheUserBeforeCleaningUpAndIssuing() {
        service.issueForLogin(USER_ID);

        InOrder order = inOrder(tokens, users);
        order.verify(users).findByIdForUpdate(USER_ID);
        order.verify(tokens).deleteExpiredOrRevokedForUser(any(Long.class), any(LocalDateTime.class));
        order.verify(tokens).save(any(RefreshToken.class));
    }

    @Test
    void rotate_withAnUnknownToken_takesNoLock() {
        when(tokens.findUserIdByTokenHash(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());

        service.rotate("unknown");

        // Det finns ingen användare att låsa, och inget arbete att serialisera
        verifyNoInteractions(users);
        verify(tokens, org.mockito.Mockito.never()).findByTokenHashForUpdate(RefreshTokenService.hash("unknown"));
    }
}
