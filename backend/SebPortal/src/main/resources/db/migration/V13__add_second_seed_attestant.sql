-- Dubbel attest (ADR 0016): betalningar över two-attestant-threshold kräver två attestanter, och
-- seed-företaget hade bara en. Samma lösenord som övriga seed-användare (password123, se V5 och V7).
-- Läggs bara till om seed-företaget finns kvar.
INSERT INTO users (tenant_id, name, email, password_hash, role)
SELECT johan.tenant_id,
       'Eva Holm',
       'eva@malmobygg.se',
       '$2b$12$bY5T.uCVV.y858KslPx/ZOIFx7pfFiFYNKfM1j.kY..i8Qj4vz406',
       'attestant'
FROM users johan
WHERE johan.email = 'johan@malmobygg.se'
  AND NOT EXISTS (SELECT 1 FROM users existing WHERE existing.email = 'eva@malmobygg.se');
