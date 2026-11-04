# Risk Register

Bygger på fliken **Risk register** i `RMR SEB(3).xlsx`. Statusen är kontrollerad mot koden 2026-11-02, på `develop` (commit `b3113c0`). R-18 är uppdaterad samma dag, efter kodändringen som åtgärdar den.

**Viktigt:** statusen gäller koden på `develop`. Prod kör fortfarande v1-koden från `main`, se R-14.

Poängen är Sannolikhet × Konsekvens, med skalan i arbetsboken: 1–4 Low, 5–9 Medium, 10–15 High, 16–25 Critical. CIA anger vad risken främst hotar: C (confidentiality), I (integrity) och A (availability). Ett streck betyder att CIA inte är tillämpligt.

Poängen för R-14 till R-23 och all CIA-märkning är förslag som teamet ännu inte har gått igenom.

## Översikt

| ID | Risk | Kategori | Risknivå | Kvarstående risknivå | CIA | Status |
| --- | --- | --- | --- | --- | --- | --- |
| R-01 | Dubbelgodkännande under samtidig last: samma betalning godkänns/utförs två gånger | Technical / architectural | High | Medium | I | Mitigated |
| R-02 | Icke-atomärt utförande: saldo dras men status/audit-post uteblir (eller tvärtom) | Technical / architectural | High | Medium | I | In progress |
| R-03 | IDOR: en attestant godkänner en annan attestants steg | Security | High | Medium | I | In progress |
| R-04 | Tenant-läckage: en kunds betalnings-/auditdata syns för en annan kund | Compliance / legal | High | Medium | C | Mitigated |
| R-05 | Stor betalning betalas ut oattesterad: tröskel-trigger missar eller är dubbeldefinierad | Technical / architectural | High | Medium | I | Mitigated |
| R-06 | Säkerhetshål i inloggning: SQL-injektion eller knäckbar lösenordslagring | Security | High | Medium | C, I | Mitigated |
| R-07 | Ojämnt bidrag gör enskildas insats ospårbar (individuell risk för IG) | People / knowledge | High | Medium | – | In progress |
| R-08 | Bus factor: bara en person behärskar en kritisk del (t.ex. auth eller native-modul) | People / knowledge | Medium | Low | A | In progress |
| R-09 | Fel kontonummer når utbetalning (validering bara på format) | Operational | Medium | Low | I | Mitigated |
| R-10 | CTO/UX-feedforward tvingar fram sen omprioritering (v7/v8) | Schedule / delivery | Medium | Medium | – | Closed |
| R-11 | Batch/CSV förväntas i demon men är breddning (ej byggd) | Schedule / delivery | Low | Low | – | Accepted |
| R-12 | Access-token lagras i localStorage, läsbar för JavaScript | Security | Medium | Low | C | Closed |
| R-13 | Livedemon strular under kvaldemon (miljö nere, inloggning krånglar, nätverk) | Operational | Medium | Low | A | Closed |
| R-14 | Prod kör en gammal version: åtgärderna i det här registret finns inte i prod | Schedule / delivery | Critical | Low | C, I | Open |
| R-15 | Inloggningen saknar begränsning av antal försök | Security | High | Medium | C | Open |
| R-16 | Sekventiella id:n avslöjar volymer och gör id:n lätta att gissa | Security | Medium | Low | C | Open |
| R-17 | Committad JWT-hemlighet gör det möjligt att förfalska token | Security | Critical | Medium | C, I | Mitigated |
| R-18 | Felsvar läcker interna id:n till klienten | Security | Medium | Low | C | Mitigated |
| R-19 | Sårbara beroenden upptäcks inte | Third-party & data | High | Medium | C, I, A | In progress |
| R-20 | Utvecklingsvärden följer med till drift | Security | High | Low | C, I | Accepted |
| R-21 | Native-, E2E- och röktester körs inte i CI | Technical / architectural | Medium | Low | I, A | Open |
| R-22 | Skadlig kod kan agera i sessionen eftersom CSP saknas | Security | Medium | Low | C, I | Open |
| R-23 | Plattformsversioner utan säkerhetsuppdateringar | Third-party & data | Medium | Low | C, I, A | Open |

## Risker i detalj

### R-01 – Dubbelgodkännande under samtidig last: samma betalning godkänns/utförs två gånger

- **Tillgång/system:** Attest-/godkännandekedja (ApprovalService)
- **Kategori:** Technical / architectural
- **CIA:** I
- **Orsak/sårbarhet:** Race när två beslut på samma betalning, eller två betalningar från samma konto, körs samtidigt
- **Konsekvens/påverkan:** Betalning utförd dubbelt, felaktigt saldo, ekonomisk skada och förtroendetapp
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Radlås (`PESSIMISTIC_WRITE`) på betalningen och kontot. Ett andra beslut ger 409. Testas mot Postgres 12
- **Bevis:** `ApprovalConcurrencyIntegrationTest` (3 scenarier × 20 körningar, 60/60 gröna 2026-11-02). Mutationsprovet i `docs/test-status.md` visar att testet faller utan låsen
- **Ansvarig:** Backend
- **Status:** Mitigated

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 3 | 5 | 15 | High |
| Kvarstående risk | 1 | 5 | 5 | Medium |

### R-02 – Icke-atomärt utförande: saldo dras men status/audit-post uteblir (eller tvärtom)

- **Tillgång/system:** Betalningstjänst (PaymentService), attestkedja (ApprovalService), audit-logg (AuditService)
- **Kategori:** Technical / architectural
- **CIA:** I
- **Orsak/sårbarhet:** Skrivningar utanför samma transaktion (BUG-008/009)
- **Konsekvens/påverkan:** Inkonsistent tillstånd, logg med luckor, går inte att bevisa vad som hänt
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Allt i samma `@Transactional`. `AuditService.record` kräver en pågående transaktion (`MANDATORY`). Rollback-test mot Postgres 12
- **Bevis:** `ApprovalServiceTransactionIntegrationTest` (godkännande, 2/2 gröna 2026-11-02)
- **Kvar:** Rollback-test för avvisning finns i PR #244, som inte är mergad. Sätt status till Mitigated efter merge. Direktbetalningar under tröskeln saknar rollback-test mot riktig databas, men går genom samma transaktion
- **Ansvarig:** Backend
- **Status:** In progress

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 5 | 10 | High |
| Kvarstående risk | 1 | 5 | 5 | Medium |

### R-03 – IDOR: en attestant godkänner en annan attestants steg

- **Tillgång/system:** Attest-/godkännandekedja (ApprovalService)
- **Kategori:** Security
- **CIA:** I
- **Orsak/sårbarhet:** Endpoint kontrollerar inte ägarskap; attestant-id tas från request i stället för JWT (BUG-011)
- **Konsekvens/påverkan:** Obehörigt godkännande, bryter 'rätt person i rätt ordning', brott i reglerat system
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Ägarskapskontroll ur security context; negativt 403-test för fel attestant
- **Bevis:** `ApprovalApiControllerOwnershipTest` (9/9 gröna 2026-11-02), `DualApprovalIntegrationTest` (dubbel attest)
- **Kvar:** Hålet där en admin kunde skapa en betalning med ett atteststeg och godkänna den själv är stängt i #242 (ADR 0017): admin kan varken skapa, godkänna eller avvisa betalningar, och den som skapat en betalning kan aldrig själv godkänna eller avvisa den, oavsett roll. Bevis: `RoleAuthorizationTest`, `ApprovalApiControllerOwnershipTest` och `ApprovalServiceTest`. Postgres-testerna `DualApprovalIntegrationTest` och `TenantIsolationIntegrationTest` är omskrivna men inte körda lokalt; sätt status när CI är grön. Se `docs/accessmatris.md`
- **Ansvarig:** Backend
- **Status:** In progress

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 3 | 5 | 15 | High |
| Kvarstående risk | 1 | 5 | 5 | Medium |

### R-04 – Tenant-läckage: en kunds betalnings-/auditdata syns för en annan kund

- **Tillgång/system:** Hela API:t: konton, audit, betalningar och attestkorg
- **Kategori:** Compliance / legal
- **CIA:** C
- **Orsak/sårbarhet:** Tenant-id från request-parameter i stället för context; filter saknas på någon query
- **Konsekvens/påverkan:** Dataläcka mellan bankkunder, GDPR-brott, allvarligt förtroendetapp
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Tenant ur JWT, isolering by default; negativt cross-tenant-test över HTTP mot Postgres 12
- **Bevis:** `TenantIsolationIntegrationTest` (13/13 gröna 2026-11-02). Mutationsprovet i `docs/test-status.md` visar att testet faller när ett tenant-skydd tas bort
- **Ansvarig:** Backend
- **Status:** Mitigated

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 5 | 10 | High |
| Kvarstående risk | 1 | 5 | 5 | Medium |

### R-05 – Stor betalning betalas ut oattesterad: tröskel-trigger missar eller är dubbeldefinierad

- **Tillgång/system:** Betalningstjänst (PaymentService), attestkedja (ApprovalService)
- **Kategori:** Technical / architectural
- **CIA:** I
- **Orsak/sårbarhet:** Flera tröskeldefinitioner som glider isär (BUG-006); oklart gränsfall > vs >=
- **Konsekvens/påverkan:** Stora belopp släpps utan attest, själva kärnlöftet brutet
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** En enda tröskeldefinition som frontend hämtar från backend; utförandegrind; gränsfall-test; strikt `>` enligt ADR 0015
- **Bevis:** `PaymentServiceTest.amountEqualToThreshold_shouldCompleteImmediately`, `PaymentServiceTest.amountJustOverThreshold_shouldRequireAttestant`, `PaymentServiceTest.amountJustOverTwoAttestantThreshold_shouldRequireTwoDifferentAttestantsInOrder`, `ApprovalServiceTest`, `DualApprovalIntegrationTest`
- **Kvar:** Betalningar som redan väntade på attest när dubbel attest infördes behåller sitt enda steg, även över 50 000 kr (ADR 0016). Flera betalningar under trösklarna stoppas inte (ADR 0015)
- **Ansvarig:** Backend
- **Status:** Mitigated

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 5 | 10 | High |
| Kvarstående risk | 1 | 5 | 5 | Medium |

### R-06 – Säkerhetshål i inloggning: SQL-injektion eller knäckbar lösenordslagring

- **Tillgång/system:** Autentisering & behörighet (Spring Security/JWT)
- **Kategori:** Security
- **CIA:** C, I
- **Orsak/sårbarhet:** Strängkonkatenerade queries; MD5 i stället för BCrypt (BUG-001/002)
- **Konsekvens/påverkan:** Kontoövertagande, dataintrång i reglerat system
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Parameteriserat/JPA; BCrypt; test som matar injektionsförsök och nekas
- **Bevis:** `AuthServiceInjectionTest` (12/12 gröna 2026-11-02), `AuthServiceMd5Test`, `V7PasswordMigrationTest`
- **Kvar:** Gäller `develop`, inte prod (R-14). Stage är inte kontrollerad för kvarvarande MD5-hashar. Antal inloggningsförsök begränsas inte (R-15)
- **Ansvarig:** Backend
- **Status:** Mitigated

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 5 | 10 | High |
| Kvarstående risk | 1 | 5 | 5 | Medium |

### R-07 – Ojämnt bidrag gör enskildas insats ospårbar (individuell risk för IG)

- **Tillgång/system:** Ej angivet
- **Kategori:** People / knowledge
- **CIA:** –
- **Orsak/sårbarhet:** Individuell bedömning; tidigare ingen tydlig ägare per task
- **Konsekvens/påverkan:** Icke-bidragande medlemmar riskerar underkänt; kunskap samlas hos få
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Namngiven ägare per task, synligt på tavlan; dokumentera bidrag; PL-avstämning
- **Kvar:** Sprint v11 har ägare per task, men Team Lead saknas. Sprint v10 och retros efter v8 saknas i `docs/sprints`. Flera git-identiteter försvårar spårningen
- **Ansvarig:** Team Lead
- **Status:** In progress

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 4 | 3 | 12 | High |
| Kvarstående risk | 2 | 3 | 6 | Medium |

### R-08 – Bus factor: bara en person behärskar en kritisk del (t.ex. auth eller native-modul)

- **Tillgång/system:** Ej angivet
- **Kategori:** People / knowledge
- **CIA:** A
- **Orsak/sårbarhet:** Ingen överlämning/parprogrammering; C/C++-ansvarig ensam på native-modulen
- **Konsekvens/påverkan:** Frånvaro hos nyckelperson blockerar leveransen
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Parvis genomgång, PR-review, dokumentera i README/ADR så fler kan ta vid
- **Kvar:** En person står för 9 av 10 commits i `native/`. Auth har två personer
- **Ansvarig:** Team Lead
- **Status:** In progress

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 3 | 3 | 9 | Medium |
| Kvarstående risk | 2 | 2 | 4 | Low |

### R-09 – Fel kontonummer når utbetalning (validering bara på format)

- **Tillgång/system:** IBAN-validering
- **Kategori:** Operational
- **CIA:** I
- **Orsak/sårbarhet:** Format-koll utan MOD97-kontrollsiffra (BUG-003)
- **Konsekvens/påverkan:** Betalning i retur, försening och extra kostnad, förstärkt vid skala (10-500 rader)
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** MOD97 (ISO 13616) i formuläret och i backend, med native-validering i C och Java som reserv; tester med kända ogiltiga IBAN
- **Bevis:** `IbanValidatorServiceTest` (5/5 gröna 2026-11-02), `native/iban/iban_validator_test.c`, `payment/schema.test.ts`
- **Kvar:** Betalningen har inget BIC-fält, så BIC-valideringen används inte i flödet. MOD97 finns i tre implementationer (C, Java, TypeScript). 4 av 5 IBAN i seed-datan klarar inte MOD97
- **Ansvarig:** Backend/Frontend
- **Status:** Mitigated

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 3 | 6 | Medium |
| Kvarstående risk | 1 | 3 | 3 | Low |

### R-10 – CTO/UX-feedforward tvingar fram sen omprioritering (v7/v8)

- **Tillgång/system:** Ej angivet
- **Kategori:** Schedule / delivery
- **CIA:** –
- **Orsak/sårbarhet:** Teknisk och UX-återkoppling kommer efter att mycket redan byggts
- **Konsekvens/påverkan:** Omtag och tappat momentum nära slutleveransen
- **Åtgärdsstrategi:** Tolerate
- **Åtgärd/kontroll:** Omprioriteringen är gjord (`docs/Risk-feedfoward.md`, ADR 0013). Det som återstår av feedbacken följs upp i R-16 till R-19
- **Ansvarig:** Team Lead
- **Status:** Closed

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 3 | 2 | 6 | Medium |
| Kvarstående risk | 3 | 2 | 6 | Medium |

### R-11 – Batch/CSV förväntas i demon men är breddning (ej byggd)

- **Tillgång/system:** Batch/CSV-import
- **Kategori:** Schedule / delivery
- **CIA:** –
- **Orsak/sårbarhet:** Kunden nämnde filuppladdning som återkommande behov; vi la den utanför MVP
- **Konsekvens/påverkan:** Leveransen upplevs missa ett uttalat kundkriterium
- **Åtgärdsstrategi:** Tolerate
- **Åtgärd/kontroll:** Ha motivering redo (kravtolkning.md, avsnitt A). Native-parsern finns, men endpoint och vy saknas. Sprint v11 tar inte in större funktioner, så ingen CSV-import byggs till slutleveransen
- **Ansvarig:** Team Lead
- **Status:** Accepted

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 2 | 4 | Low |
| Kvarstående risk | 2 | 2 | 4 | Low |

### R-12 – Access-token lagras i localStorage, läsbar för JavaScript

- **Tillgång/system:** Autentisering & behörighet (Spring Security/JWT)
- **Kategori:** Security
- **CIA:** C
- **Orsak/sårbarhet:** Token låg i localStorage och kunde stjälas vid XSS
- **Konsekvens/påverkan:** Vid XSS kan token stjälas → kontoövertagande i reglerat betalningssystem
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Access-token gäller 15 minuter och ligger bara i minnet. Refresh-token ligger i HttpOnly-cookie med rotation (ADR 0012)
- **Bevis:** `lib/authToken.test.ts`, `RefreshTokenServiceTest`, `AuthControllerRefreshTest`, `frontend/e2e/session.spec.ts`
- **Kvar:** Skadlig kod kan fortfarande agera i en öppen session, se R-22
- **Ansvarig:** Frontend
- **Status:** Closed

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 4 | 8 | Medium |
| Kvarstående risk | 1 | 4 | 4 | Low |

### R-13 – Livedemon strular under kvaldemon (miljö nere, inloggning krånglar, nätverk)

- **Tillgång/system:** Ej angivet
- **Kategori:** Operational
- **CIA:** A
- **Orsak/sårbarhet:** Beroende av Coolify-deploy och nätverk i realtid, inför publik och kund
- **Konsekvens/påverkan:** Tappad demotid, sämre intryck på kundnytta och trygghet, i värsta fall ingen demo
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Plan B: förinspelad Loom av hela kärnflödet + skärmdumpar, öppna i flik. Lokal miljö som andra reserv. Genrep mot skarp miljö innan. Teknikcheck 08.30 på plats
- **Kvar:** Kvaldemon var planerad till 2026-10-22 och datumet har passerat. Utfallet är inte dokumenterat i repot. Driftläget inför slutleveransen följs upp i R-14
- **Ansvarig:** Team
- **Status:** Closed

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 4 | 8 | Medium |
| Kvarstående risk | 1 | 4 | 4 | Low |

### R-14 – Prod kör en gammal version: åtgärderna i det här registret finns inte i prod

- **Tillgång/system:** Stage och prod (Coolify), `main`
- **Kategori:** Schedule / delivery
- **CIA:** C, I
- **Orsak/sårbarhet:** `main` ligger 372 commits efter `develop`. Prod saknar dagens API:er, och `main` har kvar v1-koden med bland annat `DashboardController`, som byggde SQL genom att lägga ihop strängar
- **Konsekvens/påverkan:** Den publika prod-adressen visar inte det vi levererat och innehåller kända sårbarheter. Stage svarade dessutom 503 vid kontroll 2026-11-02, trots lyckad deploy
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Få igång stage och verifiera. Merga sedan `develop` till `main`. Prod-databasen är skapad av `seed.sql`, så Flyway stoppar troligen utan en nollställning via techsupport
- **Ansvarig:** Team
- **Status:** Open

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 5 | 4 | 20 | Critical |
| Kvarstående risk | 1 | 4 | 4 | Low |

### R-15 – Inloggningen saknar begränsning av antal försök

- **Tillgång/system:** Autentisering & behörighet (Spring Security/JWT)
- **Kategori:** Security
- **CIA:** C
- **Orsak/sårbarhet:** Ingen rate limit eller kontospärr på login och refresh (ADR 0012 lämnar det som separat punkt)
- **Konsekvens/påverkan:** Lösenord kan gissas utan hinder, vilket kan leda till kontoövertagande
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Begränsa försök per konto och IP-adress, och testa att gränsen ger 429
- **Ansvarig:** Backend
- **Status:** Open

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 3 | 4 | 12 | High |
| Kvarstående risk | 2 | 4 | 8 | Medium |

### R-16 – Sekventiella id:n avslöjar volymer och gör id:n lätta att gissa

- **Tillgång/system:** API och databas
- **Kategori:** Security
- **CIA:** C
- **Orsak/sårbarhet:** Autoinkrementerande id:n exponeras i API:t (CTO-feedback, punkt 3). Inget UUID är infört
- **Konsekvens/påverkan:** Id:n går att gissa och avslöjar volymer. Tenant-kontrollerna (R-04) hindrar åtkomst över företagsgränsen
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Lägg till en publik UUID-kolumn och behåll heltal som primärnyckel
- **Ansvarig:** Backend
- **Status:** Open

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 3 | 6 | Medium |
| Kvarstående risk | 1 | 3 | 3 | Low |

### R-17 – Committad JWT-hemlighet gör det möjligt att förfalska token

- **Tillgång/system:** Autentisering & behörighet (Spring Security/JWT)
- **Kategori:** Security
- **CIA:** C, I
- **Orsak/sårbarhet:** Signeringsnyckeln låg i klartext i det publika repot. En förfalskad ADMIN-token fungerade (ADR 0011)
- **Konsekvens/påverkan:** Vem som helst kunde agera som vilken användare som helst, i vilket företag som helst
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Nyckeln slumpas vid start och finns bara i minnet. Den läckta hemligheten avvisas. Rotation sker genom omstart eller redeploy, och inloggade användare förnyas tyst med refresh-token (ADR 0012). Asymmetrisk signering är medvetet bortvald så länge en enda tjänst signerar och verifierar (ADR 0011)
- **Bevis:** `JwtServiceTest.tokenForgedWithAKnownSecret_isOnlyAcceptedByAServerUsingThatSecret`, `JwtServiceTest.compromisedDevSecret_shouldBeRejectedAtStartup`
- **Kvar:** Fungerar bara med en instans. Fler instanser kräver en delad nyckel, och `JWT_SECRET` skickas inte vidare i `infra/docker-compose.yml`
- **Ansvarig:** Backend
- **Status:** Mitigated

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 5 | 5 | 25 | Critical |
| Kvarstående risk | 1 | 5 | 5 | Medium |

### R-18 – Felsvar läcker interna id:n till klienten

- **Tillgång/system:** API (GlobalExceptionHandler)
- **Kategori:** Security
- **CIA:** C
- **Orsak/sårbarhet:** Undantagens text skickades oförändrad till klienten, till exempel steg-, användar- och tenant-id. Ingen catch-all fanns
- **Konsekvens/påverkan:** Interna id:n och detaljer ger en angripare underlag. CTO-feedback, punkt 9
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Fasta felmeddelanden utåt, detaljerna i serverloggen, och en catch-all i `GlobalExceptionHandler`. Fel som Spring och Spring Security ska hantera behåller sin status. `server.error.*` är låst så att Springs egna felsvar inte visar stack trace eller undantagstext
- **Bevis:** `GlobalExceptionHandlerTest` (11/11), `ApprovalApiControllerOwnershipTest`, `TenantIsolationIntegrationTest`, `RoleAuthorizationTest` (gröna 2026-11-02). Mutationsprovet i `docs/test-status.md` visar att testerna faller när en läcka eller vidarekastningen tas tillbaka
- **Kvar:** Texten i `IllegalArgumentException` skickas fortfarande till klienten som 400. Alla ställen i vår kod är genomgångna, men ett sådant undantag från ett bibliotek skulle också visas
- **Ansvarig:** Backend
- **Status:** Mitigated

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 3 | 2 | 6 | Medium |
| Kvarstående risk | 1 | 2 | 2 | Low |

### R-19 – Sårbara beroenden upptäcks inte

- **Tillgång/system:** Frontend- och backendberoenden, CI
- **Kategori:** Third-party & data
- **CIA:** C, I, A
- **Orsak/sårbarhet:** `npm audit` saknas i CI (CTO-feedback, punkt 7). Backendberoendena är inte granskade här
- **Konsekvens/påverkan:** Kända sårbarheter kan följa med ut utan att någon märker det
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Sårbara frontendberoenden är uppdaterade inom tillåtna versionsintervall. Låt CI falla på höga sårbarheter i produktionsberoenden
- **Bevis:** `npm audit --omit=dev` ger 0 sårbarheter 2026-11-02 (före uppdateringen 2 höga). Totalt 15 medelallvarliga, före uppdateringen 21 varav 6 höga
- **Kvar:** CI-steget saknas. De 15 medelallvarliga finns i utvecklingsverktyg (`vitest`, `eslint`, `typescript-eslint` och deras beroenden) och når inte produktionsbygget
- **Ansvarig:** Frontend
- **Status:** In progress

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 4 | 3 | 12 | High |
| Kvarstående risk | 2 | 3 | 6 | Medium |

### R-20 – Utvecklingsvärden följer med till drift

- **Tillgång/system:** Drift, Flyway-seed och konfiguration
- **Kategori:** Security
- **CIA:** C, I
- **Orsak/sårbarhet:** Seed-användare med `password123`, databaslösenordet `seb123` i det publika repot, och en dev-nyckel som standard för auditsignering med signeringskravet avstängt. Miljövariablerna på stage är inte kontrollerade
- **Konsekvens/påverkan:** Vem som helst kan logga in i en publik miljö med seed-konton. Den signerade loggen är inte manipuleringssäker om dev-nyckeln används
- **Åtgärdsstrategi:** Tolerate
- **Åtgärd/kontroll:** Seed-användarna är accepterade för demon (ADR 0011). Före go-live: ta bort seeden, byt lösenord och sätt `AUDIT_SIGNING_KEY` och `AUDIT_SIGNING_REQUIRED=true`
- **Ansvarig:** Team
- **Status:** Accepted

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 4 | 3 | 12 | High |
| Kvarstående risk | 1 | 3 | 3 | Low |

### R-21 – Native-, E2E- och röktester körs inte i CI

- **Tillgång/system:** CI (GitHub Actions)
- **Kategori:** Technical / architectural
- **CIA:** I, A
- **Orsak/sårbarhet:** `maven.yml` och `frontend.yml` kör bara JUnit, Vitest, lint och build. Native C-sviten, E2E och rök-testet körs för hand
- **Konsekvens/påverkan:** Ett fel i C-koden eller i hela användarflödet kan mergas utan att upptäckas
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Lägg till native-sviten och E2E i CI
- **Ansvarig:** Team
- **Status:** Open

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 3 | 3 | 9 | Medium |
| Kvarstående risk | 1 | 3 | 3 | Low |

### R-22 – Skadlig kod kan agera i sessionen eftersom CSP saknas

- **Tillgång/system:** Frontend och säkerhetsheaders
- **Kategori:** Security
- **CIA:** C, I
- **Orsak/sårbarhet:** Ingen Content-Security-Policy är satt (uppföljning i ADR 0009). Access-token i minnet kan läsas av skadlig kod på sidan (ADR 0012)
- **Konsekvens/påverkan:** Vid XSS kan en angripare agera som användaren medan sidan är öppen
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Lägg till CSP. React escapar output och koden använder inte `dangerouslySetInnerHTML`
- **Ansvarig:** Frontend
- **Status:** Open

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 4 | 8 | Medium |
| Kvarstående risk | 1 | 4 | 4 | Low |

### R-23 – Plattformsversioner utan säkerhetsuppdateringar

- **Tillgång/system:** Postgres, Spring Boot
- **Kategori:** Third-party & data
- **CIA:** C, I, A
- **Orsak/sårbarhet:** Postgres 12 och Spring Boot 3.3 får inte längre säkerhetsuppdateringar från sina projekt
- **Konsekvens/påverkan:** Nya sårbarheter åtgärdas inte
- **Åtgärdsstrategi:** Treat
- **Åtgärd/kontroll:** Uppgradera efter slutleveransen. Ett byte av Postgres-version kräver nollställd databas via techsupport (`DRIFT.md`)
- **Ansvarig:** Backend
- **Status:** Open

| Bedömning | Sannolikhet | Konsekvens | Poäng | Nivå |
| --- | ---: | ---: | ---: | --- |
| Ursprunglig risk | 2 | 3 | 6 | Medium |
| Kvarstående risk | 1 | 3 | 3 | Low |
