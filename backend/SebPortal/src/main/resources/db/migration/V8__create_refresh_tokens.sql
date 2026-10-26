-- Refresh-tokens (ADR 0012). Servern sparar bara SHA-256-hashen av token, aldrig själva värdet,
-- så en läckt databas ger inga användbara token.
CREATE TABLE refresh_tokens (
                                id BIGSERIAL PRIMARY KEY,
                                user_id INT NOT NULL REFERENCES users(id),
                                token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 som hex (64 tecken)
                                family_id UUID NOT NULL,                -- alla token från en och samma inloggning
                                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                expires_at TIMESTAMP NOT NULL,
                                used_at TIMESTAMP,                      -- satt när token bytts mot en ny (rotation)
                                revoked_at TIMESTAMP                    -- satt när familjen spärrats (utloggning eller återanvändning)
);

-- token_hash har redan ett index genom UNIQUE (slås upp vid varje förnyelse).
-- family_id används när en hel familj spärras, user_id när en användares gamla token städas bort.
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
