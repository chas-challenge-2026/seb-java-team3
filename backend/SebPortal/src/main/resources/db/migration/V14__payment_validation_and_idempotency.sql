ALTER TABLE payments ALTER COLUMN reference TYPE VARCHAR(140);
ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(128);

-- NULL behåller kompatibilitet med äldre klienter och befintliga betalningar.
-- Samma nyckel får användas av olika användare och företag, men aldrig två gånger av samma skapare.
ALTER TABLE payments ADD CONSTRAINT payments_request_key_unique
    UNIQUE (tenant_id, created_by, idempotency_key);
