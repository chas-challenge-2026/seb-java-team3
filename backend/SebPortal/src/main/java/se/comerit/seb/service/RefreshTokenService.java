package se.comerit.seb.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.domain.RefreshToken;
import se.comerit.seb.domain.User;
import se.comerit.seb.repository.RefreshTokenRepository;
import se.comerit.seb.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

// Refresh-token (ADR 0012): utfärdar, roterar och spärrar de långlivade token som gör att en kort
// access-token kan förnyas utan ny inloggning.
//
// Tre val som är lätta att missa:
//  - Ogiltiga förnyelser KASTAR INTE undantag utan returnerar tomt (Optional.empty). Ett undantag
//    hade rullat tillbaka transaktionen, och därmed ångrat spärren av familjen när en återanvänd
//    token upptäckts. Controllern gör tomt resultat till 401.
//  - All hantering för en användare serialiseras med ett lås på användarens rad (lockUser). Det
//    räcker inte att låsa den token som prövas: en tjuv som visar en gammal token och en användare
//    som förnyar den aktuella låser olika rader, och spärren (en UPDATE) ser då inte den efterföljare
//    som användaren skapar medan UPDATE:n väntar. Med ett lås per användare kan de aldrig överlappa.
//  - Själva token-värdet sparas aldrig, bara dess SHA-256-hash. Slås ett värde upp görs det på hashen,
//    så det finns ingen jämförelse av hemligheten som kan läcka via svarstid.
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    // 32 byte (256 bit) slump blir en 43 tecken lång URL-säker sträng, lämplig i en cookie.
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Duration sessionLifetime;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    // Resultatet av en lyckad förnyelse: den nya refresh-token (för cookien) och användaren som den
    // tillhör, inläst på nytt ur databasen så att roll- och tenantändringar slår igenom.
    public record RotatedSession(String refreshToken, User user) {
    }

    @Autowired
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${app.refresh.expiration-days:7}") long expirationDays) {
        this(refreshTokenRepository, userRepository, Duration.ofDays(expirationDays), Clock.systemDefaultZone());
    }

    // Paketsynlig så att testerna kan styra tiden.
    RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                        UserRepository userRepository,
                        Duration sessionLifetime,
                        Clock clock) {
        if (sessionLifetime.isZero() || sessionLifetime.isNegative()) {
            // Annars skulle varje token vara utgången direkt och ingen kunde förnya sig.
            throw new IllegalStateException("app.refresh.expiration-days måste vara minst 1 dag");
        }

        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.sessionLifetime = sessionLifetime;
        this.clock = clock;
    }

    // Hur länge en refresh-token gäller; används även som cookiens livslängd.
    public Duration sessionLifetime() {
        return sessionLifetime;
    }

    // Vid inloggning: ny familj, ny token. Användarens gamla utgångna och spärrade rader städas
    // samtidigt, så tabellen inte växer utan gräns.
    @Transactional
    public String issueForLogin(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);

        lockUser(userId);

        refreshTokenRepository.deleteExpiredOrRevokedForUser(userId, now);

        return createToken(userId, UUID.randomUUID(), now);
    }

    // Byter en refresh-token mot en ny i samma familj (rotation). Tomt resultat betyder att token
    // inte får användas: okänd, utgången, spärrad, redan använd eller tillhör en användare som inte
    // längre finns. Raden låses medan den prövas, så två samtidiga förnyelser med samma token går
    // efter varandra och den andra räknas som återanvändning.
    @Transactional
    public Optional<RotatedSession> rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String tokenHash = hash(rawToken);

        // Okänd token: det finns ingen användare att låsa och ingen familj att spärra.
        Optional<Long> owner = refreshTokenRepository.findUserIdByTokenHash(tokenHash);
        if (owner.isEmpty()) {
            return Optional.empty();
        }

        // Användarens lås tas FÖRST, innan token läses och prövas. Allt som följer ser därför
        // färdiga, committade rader för användaren, och en spärr av familjen ser alla dess token.
        lockUser(owner.get());

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashForUpdate(tokenHash);
        if (found.isEmpty()) {
            // Raden städades bort medan vi väntade på låset.
            return Optional.empty();
        }
        RefreshToken token = found.get();

        if (token.isUsed() || token.isRevoked()) {
            // En token som redan bytts mot en ny, eller som hör till en spärrad familj, ska aldrig
            // visas igen. Händer det antas den vara stulen och hela familjen spärras, så att även den
            // efterföljare som tjuven eller den riktiga användaren håller blir oanvändbar.
            boolean reused = token.isUsed();
            Long userId = token.getUserId();
            UUID familyId = token.getFamilyId();

            int revoked = refreshTokenRepository.revokeFamily(familyId, now);

            if (reused) {
                log.warn("Refresh-token återanvänd: user={}, family={}, {} token spärrade",
                        userId, familyId, revoked);
            }
            return Optional.empty();
        }

        if (refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNotNull(token.getFamilyId())) {
            // Försvar på djupet: en familj som spärrats en gång är spärrad. Med lås per användare ska en
            // aktiv token aldrig finnas kvar i en familj med spärrade token, men om det ändå händer
            // (till exempel efter ett fel eller en manuell ändring) stoppas den här i stället för att
            // få leva vidare med glidande giltighet.
            log.warn("Aktiv refresh-token i en spärrad familj: user={}, family={}",
                    token.getUserId(), token.getFamilyId());
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            return Optional.empty();
        }

        if (token.isExpired(now)) {
            return Optional.empty();
        }

        Optional<User> user = userRepository.findById(token.getUserId());
        if (user.isEmpty()) {
            // Användaren är borttagen: sessionen ska inte kunna fortsätta.
            log.warn("Refresh-token för användare som inte finns: user={}, family={}",
                    token.getUserId(), token.getFamilyId());
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
            return Optional.empty();
        }

        token.markUsed(now);
        String next = createToken(token.getUserId(), token.getFamilyId(), now);

        return Optional.of(new RotatedSession(next, user.get()));
    }

    // Utloggning: spärrar hela familjen. Saknas token eller är den okänd görs inget, så att
    // utloggning är idempotent och inte avslöjar om ett värde fanns.
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String tokenHash = hash(rawToken);

        Optional<Long> owner = refreshTokenRepository.findUserIdByTokenHash(tokenHash);
        if (owner.isEmpty()) {
            return;
        }

        lockUser(owner.get());

        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), now));
    }

    // Serialiserar all refresh-token-hantering för en användare. Låset hålls till transaktionen är
    // klar. Saknas användaren (till exempel en borttagen) finns inget att låsa och ingenting händer.
    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId);
    }

    // Glidande session: varje ny token gäller sessionLifetime från utfärdandet.
    private String createToken(Long userId, UUID familyId, LocalDateTime now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(new RefreshToken(userId, hash(rawToken), familyId, now.plus(sessionLifetime)));

        return rawToken;
    }

    // SHA-256 som hex (64 tecken). Paketsynlig så att testet kan räkna ut samma värde.
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 saknas i Java-miljön", e);
        }
    }
}
