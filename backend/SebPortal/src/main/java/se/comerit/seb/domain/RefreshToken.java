package se.comerit.seb.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// En refresh-token (ADR 0012). Själva token-värdet sparas aldrig, bara dess SHA-256-hash.
// En token är i exakt ett av fyra lägen:
//  - aktiv:     varken använd eller spärrad, och inte utgången
//  - använd:    usedAt är satt, token har bytts mot en ny (rotation) och får inte visas igen
//  - spärrad:   revokedAt är satt, hela familjen är avstängd (utloggning eller upptäckt återanvändning)
//  - utgången:  expiresAt har passerat
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    // Alla token som härstammar från samma inloggning delar familj. Vid återanvändning spärras hela familjen.
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected RefreshToken() {
        // Krävs av JPA/Hibernate
    }

    public RefreshToken(Long userId, String tokenHash, UUID familyId, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public UUID getFamilyId() { return familyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }

    public boolean isUsed() { return usedAt != null; }

    public boolean isRevoked() { return revokedAt != null; }

    // Utgången när tidpunkten har passerat (samma villkor som städfrågan i RefreshTokenRepository: expiresAt < now).
    public boolean isExpired(LocalDateTime now) { return expiresAt.isBefore(now); }

    // Rotation: token är förbrukad och får inte användas igen.
    public void markUsed(LocalDateTime now) { this.usedAt = now; }
}
