# Risk-feedforward från CTO: prioritering och avgränsningar

CTO:n gav nio punkter om säkerhet och robusthet. Här står vad vi har prioriterat och genomfört, vad vi har avgränsat bort och varför.

## Så har vi prioriterat

Vi har vägt varje punkt mot fyra frågor:

1. **Vad händer om risken inträffar?** En portal för företagsbetalningar måste klara två saker framför allt: pengar får inte flyttas fel, och ett företag får inte se ett annat företags data. Risker mot integritet (I) och konfidentialitet (C) i betalnings- och attestflödet har gått först.
2. **Kundvärde.** Kunden litar på portalen för att den betalar rätt belopp, med rätt attest, och visar bara det egna företagets data. Åtgärder som skyddar det har gått före åtgärder som kunden inte märker av.
3. **Tid.** Slutleveransen är 2026-11-04. Ett stort bygge sent i projektet kan ge nya fel i flöden som redan fungerar och är testade.
4. **Finns redan ett annat skydd?** Om en annan kontroll redan täcker det mesta av risken har vi skjutit upp punkten och skrivit ner den kvarstående risken.

## Prioriterat och genomfört

### Punkt 5: MD5 är inte säkert

- **Genomfört:** MD5 är borttaget. Lösenord hashas med BCrypt (migrering `V7__replace_md5_passwords_with_bcrypt.sql`). Inloggningen och användaradministrationen använder samma BCrypt-encoder.
- **Varför prioriterat:** MD5-hashar knäcks snabbt om databasen läcker, och då kan någon logga in som kunden och skapa eller attestera betalningar. Bytet var litet och skyddet stort (R-06).

### Punkt 6: SQL-injektion

- **Genomfört:** All databasåtkomst går via Spring Data JPA, antingen med härledda metoder (`findByIdAndTenantId`) eller med `@Query` i JPQL och namngivna parametrar (`:userId`). Parametrarna skickas separat från frågan, på samma sätt som i ett prepared statement, så indata kan aldrig bli en del av själva SQL-satsen. Koden har ingen native SQL, ingen `EntityManager` och inga frågor som byggs genom att strängar läggs ihop. v1-koden i `DashboardController`, som byggde SQL av strängar, är borttagen.
- **Varför prioriterat:** SQL-injektion i inloggningen ger direkt åtkomst till alla kunders data. Risken är allvarlig och lätt att utnyttja (R-06).

### Punkt 9: Atomära transaktioner och vad klienten får se

- **Genomfört:**
  - Saldo, betalningsstatus och auditpost sparas i samma transaktion (`@Transactional`). Om ett steg misslyckas rullas allt tillbaka. `ApprovalServiceTransactionIntegrationTest` testar det.
  - Samtidiga godkännanden av samma betalning hanteras med radlås. `ApprovalConcurrencyIntegrationTest` kör testet mot Postgres.
  - Felsvaren har fasta texter. Stack traces och interna id:n går bara till serverloggen, och en catch-all fångar oväntade fel (`GlobalExceptionHandlerTest`).
- **Varför prioriterat:** En betalning som dras från kontot utan att status och audit följer med är exakt det kunden inte får råka ut för. Stack traces hos klienten ger en angripare en karta över systemet (R-01, R-02, R-18).

### Punkt 8: JWT-nyckel och rotation

- **Genomfört:**
  - Signeringsnyckeln slumpas vid start och finns inte längre i repot ([ADR 0011](../decisions/0011-jwt-signeringsnyckeln-slumpas-vid-start.md)). Om nyckeln läcker roteras den genom omstart eller redeploy.
  - Access-token lever 15 minuter och ligger bara i minnet i frontend.
  - Refresh-token ligger i en httpOnly-cookie och byts ut vid varje användning ([ADR 0012](../decisions/0012-refresh-token-i-httponly-cookie-med-rotation.md), PR #214).
- **Medvetet bortvalt:** Asymmetrisk nyckel. Det tillför något först när flera tjänster ska verifiera token som bara en tjänst får signera. Hos oss är det samma backend som signerar och verifierar.
- **Varför prioriterat:** En nyckel som ligger i repot gör det möjligt att förfalska token för vilken användare och vilket företag som helst. Risken var Critical (R-17, R-12).

### Punkt 2 och 4: Behörighet och accessmatris

- **Genomfört:**
  - **Vertikal behörighet** (vilken roll som får göra vad): kontrolleras med `@PreAuthorize` på service-metoderna.
  - **Horisontell behörighet** (vems data man får röra): varje fråga filtrerar på företaget (`tenantId`) från token. `TenantIsolationIntegrationTest` och `AccountRepositoryTenantTest` testar det.
  - **Accessmatris:** [`docs/accessmatris.md`](../accessmatris.md) listar alla roller och vad de får göra.
  - **Eskalering stängd:** en admin kunde godkänna sin egen betalning. Admin är nu en administrativ roll som varken skapar eller attesterar betalningar, och ingen kan godkänna en betalning som hen själv har skapat (#242, [ADR 0017](../decisions/0017-admin-ar-en-administrativ-roll.md)).
- **Varför prioriterat:** Attestflödet är hela poängen med produkten. Om en användare kan godkänna sin egen betalning eller se ett annat företags data försvinner kundvärdet (R-03, R-04).

### Punkt 1: CIA-genomgång

- **Genomfört:** Varje risk i [riskregistret](Risk-register.md) är märkt med vad den främst hotar: C (confidentiality), I (integrity) eller A (availability). Genomgången gav sju nya risker (R-14 till R-23). De som återstår finns under avgränsningarna nedan.
- **Genomgånget i teamet:** poängen och CIA-märkningen för R-14 till R-23. R-14 (prod körde gammal kod) är åtgärdad: `develop` mergades till `main` 2026-11-03 (#254), prod-databasen nollställdes av techsupport, och slutversionen mergas till `main` efter kodfrysningen.
- **Varför prioriterat:** Utan en samlad bild går det inte att avgöra vad som måste vara klart före go-live.

## Avgränsningar

Följande är inte byggt inom projektet. För varje punkt står varför och vad som krävs före go-live.

### Punkt 3: GUID i stället för autoinkrementerande id

- **Läge:** Alla tabeller har heltal som id (`IDENTITY`), och API:t exponerar dem (till exempel `/api/approvals/{stepId}/approve`).
- **Varför avgränsat:**
  - *Ett annat skydd finns redan.* Att gissa ett id räcker inte för att komma åt något. Varje fråga kontrollerar både roll och företag, så ett gissat id från ett annat företag ger inget svar. Den kvarstående risken är att id:n avslöjar ungefärliga volymer (till exempel hur många betalningar som skapats). Kvarstående risknivå: Low (R-16).
  - *Tid.* Bytet berör varje tabell, migrering, DTO, endpoint och frontend-route, och de flesta tester. Att göra det sent i projektet riskerar fel i flöden som redan är testade.
  - *Kundvärde.* Kunden märker ingen skillnad i funktion.
- **Före go-live:** Lägg till en publik `UUID`-kolumn som API:t använder, och behåll heltalet som primärnyckel internt.

### Punkt 7: `npm audit` i CI-pipelinen

- **Läge:** CI kör lint, enhetstester, bygge, native-sviten, E2E och ett röktest. Något steg som stoppar bygget vid sårbara beroenden finns inte.
- **Varför avgränsat:**
  - *Risken är redan sänkt för hand.* `npm audit --omit=dev` gav 0 sårbarheter i produktionsberoendena 2026-11-02, efter att två höga åtgärdades. De 15 medelallvarliga som återstår finns i utvecklingsverktyg (`vitest`, `eslint`) och följer inte med i bygget som når kunden (R-19).
  - *CTO:n bedömde punkten som låg, en breddning.*
  - *Kundvärde.* CI-steget skyddar mot framtida sårbarheter, inte mot någon som finns i dag.
- **Före go-live:** Lägg till `npm audit --omit=dev --audit-level=high` i `frontend.yml` och en motsvarande kontroll av Maven-beroendena (till exempel Dependabot).

### Kvarstående risker från CIA-genomgången

| Risk | Hotar | Varför avgränsat | Före go-live |
| --- | --- | --- | --- |
| **R-15** Inloggningen begränsar inte antal försök | C | Kräver beslut om gränser per konto och IP, var räknarna lagras och hur frontend visar spärren. En felaktig gräns kan låsa ute riktiga användare. Det är den högsta kvarstående risken (Medium). | Begränsa försök per konto och IP, och testa att gränsen ger 429. Görs först efter leveransen. |
| **R-22** Content-Security-Policy saknas | C, I | React escapar all output och koden använder inte `dangerouslySetInnerHTML`, så XSS är svårt att få in. En felaktig CSP kan stoppa sidan helt i drift. | Sätt CSP och testa den på stage. |
| **R-23** Postgres 12 och Spring Boot 3.3 får inga säkerhetsuppdateringar | C, I, A | Ett byte av Postgres-version kräver en nollställd databas via techsupport (`DRIFT.md`) och kan inte göras inom leveransen. | Uppgradera efter slutleveransen. |
| **R-20** Utvecklingsvärden i drift | C, I | Seed-användarna behövs för demon och är accepterade där. | Ta bort seeden, byt lösenord och sätt `AUDIT_SIGNING_KEY` och `AUDIT_SIGNING_REQUIRED=true`. |
