# v2 Mål — SEB Företagsbetalningar (Java)

Detta dokument beskriver vad v2 ska uppnå. Era uppgifter är organiserade i moduler — ni behöver inte bygga allt, men varje modul ska ge en märkbar förbättring av kodkvalitet, säkerhet eller skalbarhet.

---

## Backend: Spring Boot REST API

Ersätt Spring MVC + Thymeleaf-monoliten med ett separat REST-API och ett separat frontend.

### Prioriterade förbättringar

**Lager**
- Inför Repository-mönstret: `PaymentRepository`, `UserRepository`, etc. (`@Repository`)
- Lägg till ett servicelager: `PaymentService`, `ApprovalService` (`@Service`)
- Controllers ska bara anropa services — ingen SQL i controllern

**ORM**
- Migrera från rå `JdbcTemplate` till Spring Data JPA (Hibernate)
- Skriv en proper migration-kedja med Flyway eller Liquibase
- Ingen hårdkodad uppkopplingssträng i källkod

**Autentisering**
- Spring Security med JWT Bearer tokens
- `BCryptPasswordEncoder` för lösenordshashning (ta bort MD5)
- Role-based authorization via `@PreAuthorize("hasRole('attestant')")` — inte strängjämförelse
- Refresh tokens med rotation

**Notifieringar**
- Ersätt inline `JavaMailSender`-anrop med ett dedikerat notifieringslager
- Lägg till en bakgrundskö: `@Async` + en kö (t.ex. `BlockingQueue` eller meddelandebroker)
- Retry-logik med Spring Retry: exponentiell backoff, max 3 försök
- Loggning av misslyckade notifieringar till DB

**IBAN-validering**
- Implementera MOD97-kontrollsumma (ISO 13616)
- Alternativ: anropa native C-modul via JNI/JNA (se nedan)

**Audit log**
- Samlad loggning: antingen DB eller append-only fil — inte båda
- Strukturerad loggning med Logback/SLF4J
- Tenant-filtrerad vy i API:t

**Betalningsflöde**
- Kontobalans uppdateras atomärt med betalningsstatus i en `@Transactional`-metod
- Optimistisk låsning med `@Version` för att förhindra dubbla godkännanden
- Idempotency key på betalningsskapande

---

## Frontend: React 18

- Vite + TypeScript
- React Query för serverstate
- Tanstack Router för routing
- Zod för formulärvalidering
- Port 3000 (dev), byggd till statiska filer för produktion

---

## Native C/C++-moduler

Se `native/README.md` för detaljer. Tre kandidater:

### 1. CSV-batchparser (prestandakritisk)
- Hantera RFC 4180-kompatibel CSV (citerade fält, escape-sekvenser)
- Parallell körning med OpenMP
- JNI/JNA-bindings från Java
- Mål: parse 500 rader < 5ms

### 2. IBAN/BIC-validator (ISO 13616)
- MOD97-kontrollsiffra
- BIC-format-validering (ISO 9362)
- Kompileras som ett delat bibliotek (`libiban.so`)
- Java-wrapper som fallback om biblioteket saknas

### 3. Audit-signering
- Append-only logg med HMAC-SHA256 per post
- Tamper-evidens: varje post kedjas till föregående hash
- Verifieringsfunktion: returnerar första trasiga posten

---

## Infrastruktur

- Dockerfile multi-stage (Maven build → JRE runtime) — redan finns, kan förbättras
- docker-compose med healthchecks och volumes
- Nginx reverse proxy med TLS-terminering
- Miljöspecifik konfiguration (dev/staging/prod via Spring profiles)

---

## Acceptanskriterier för v2

| Krav | Mät med |
|------|---------|
| Inga SQL-injektioner | CodeQL eller SonarQube scan |
| BCrypt-lösenord | Unit test på `UserService.createUser` |
| IBAN MOD97 korrekt | Parameteriserad testsvit med 50 kända IBAN:er |
| CSV parse RFC 4180 | Testfil med citerade fält, kommanamn, tomrader |
| Atomär balansuppdatering | Integrationstest med samtidiga requests |
| Alla audit-händelser i DB | Integrationstest för hela betalningsflödet |
| Notifieringsfel loggas | Mock SMTP som kastar, verifiera DB-post |
| Inga hårdkodade lösenord | `grep -r "seb123"` returnerar tom |
