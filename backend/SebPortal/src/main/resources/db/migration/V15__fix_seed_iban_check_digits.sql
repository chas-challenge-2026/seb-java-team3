-- Seed-kontona i V5 fick sina IBAN genom att sista siffran räknades upp (…466, …467, …468) utan att
-- kontrollsiffrorna (position 3–4) räknades om. Bara Driftkontot klarade därför MOD97 (ISO 13616),
-- och betalningar till eller via Lönekonto och Projektkonto stoppades av IBAN-valideringen.
-- Kontonumret (BBAN) är oförändrat, bara kontrollsiffrorna rättas.
-- V5 ändras inte, eftersom Flyway då vägrar starta på databaser där V5 redan har körts.
UPDATE accounts SET iban = 'SE1850000000058398257467' WHERE iban = 'SE4550000000058398257467';
UPDATE accounts SET iban = 'SE8850000000058398257468' WHERE iban = 'SE4550000000058398257468';
