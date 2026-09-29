# Teststatus

Här finns en samlad bild av vad som är testat och vilka luckor som återstår. Teststödet anges med fil eller testnamn så att varje punkt går att kontrollera i koden. Uppdatera dokumentet när en task går till Done.

**Senast uppdaterad:** 2026-09-24  
**Ansvarig denna sprint:** ____

## 1. Läget just nu

| Svit | Omfattning | Senaste besked |
| --- | --- | --- |
| Frontend · Vitest | 7 filer, 54 tester | Alla gröna vid lokal körning 2026-09-24 |
| Backend · JUnit | 12 klasser, 67 tester | 62 gröna i lokal Surefire-rapport 2026-09-21; 5 tillkomna tester är inte lokalt verifierade |
| Native C · Check | 36 tester | Ej körda: `check` saknas lokalt och sviten körs inte i CI |
| E2E · hela användarflödet | 0 tester | Ingen E2E-svit finns |

De fem backendtesterna finns i `IbanValidatorServiceTest` och lades till efter den senaste lokala körningen. `maven.yml` kör `mvn package` vid push och PR mot `develop`. Kontrollera resultatet i Actions innan de räknas som verifierade.

**Status:** Grön = test finns och passerar; Delvis = delar är testade; Ej testad = test saknas; Röd = test finns men fallerar.  
**Typer:** Unit = enhetstest; Int = integrationstest med exempelvis Spring, MockMvc eller H2; Komp = React-komponenttest; E2E = ett test som går genom hela användarflödet.

Backendens unit-tester använder mockade repositories. De verifierar servicelogik men inte transaktioner eller rollback mot en riktig databas.

## 2. MVP-flödet

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Logga in → skapa betalning → se attest-badge → godkänn → saldo dras → händelsen syns i audit | E2E | Ej testad | Ingen E2E-svit |
| Sista godkännandet slutför betalningen, drar saldo och skriver audit | Unit | Grön | `ApprovalServiceTest.finalApproval_shouldCompletePaymentDeductBalanceAndRecordAudit` |

Delarna i användarflödet är testade var för sig. Det gröna servicetestet använder mockade repositories och verifierar inte att hela flödet fungerar ihop.

## 3. Teststatus per område

### Inloggning och behörighet

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Login skyddas mot SQL-injektion (BUG-001, R-06) | Int | Grön | `AuthServiceInjectionTest` · 12 tester med H2 |
| Lösenord hanteras med BCrypt (BUG-002, R-06) | Int | Delvis | `AuthServiceInjectionTest.controlCase_validCredentialsAuthenticate` |
| Attestant får bara hantera egna steg (BUG-011, R-03) | Int | Grön | `ApprovalApiControllerOwnershipTest` · 3; `ApprovalServiceTest.approve/reject_shouldThrowAccessDenied_*` |
| Roller och åtkomst via `@PreAuthorize` (#79) | Int | Delvis | `RoleAuthorizationTest` · 17 |
| JWT-signering, utgång och ogiltiga token | Unit | Grön | `JwtServiceTest` · 4; `JwtAuthenticationFilterTest` · 3; `JwtUserContextTest` · 5 |
| Saknad eller ogiltig token mot API ger 401 | Int | Grön | `ApprovalApiControllerSecurityTest.approve_withoutToken/withGarbageToken_returns401` |
| Loginformulärets validering och fel | Komp | Grön | `LoginForm.test.tsx` · 4; `auth/schema.test.ts` · 7 |
| Skyddade routes och fel roll | Komp | Ej testad | Test för `requireAuth` och `requireRole` saknas |
| API-klientens Bearer-token och felhantering | Unit | Grön | `lib/api.test.ts` · 8 |

**Avgränsningar och kvar att testa**

- SQL-injektionstesterna gäller login: sex payloads i e-post, tre i lösenord och ett försök med `DROP TABLE` som lämnar data orörd. Äldre `DashboardController` bygger fortfarande SQL genom att lägga ihop strängar med `tenantId` från sessionen och saknar test.
- BCrypt verifieras indirekt. Test saknas för att neka en MD5-hash och för V7-migreringen.
- Ägarskapstestet går genom JWT, controller och riktig service. Fel attestant får 403 utan att steg, saldo eller audit ändras. `RoleAuthorizationTest` täcker de tre rollerna, lista, godkänn, avvisa, audit, tidslinje, betalningsskapande och 401. `/api/my-payments` och `/api/approvals/count` återstår.
- JWT-testerna täcker fel nyckel och trasig token; ogiltig eller saknad token ger ingen autentisering. JWT går före en äldre sessionscookie. Formulärtesterna täcker ogiltig e-post, tomt lösenord, trimning och låst knapp under inloggning. API-klienttesterna täcker token med och utan värde, `ApiError` och validering av svar.
- `ProtectedRoute.test.tsx` finns inte. Test behövs för omdirigering till login och spärr vid fel roll.

### Betalning och godkännande

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Skapa betalning med belopp och mottagare | Unit + Komp | Grön | `PaymentServiceTest` · 6; `PaymentForm.test.tsx` · 7; `payment/schema.test.ts` |
| Tröskelvärdet ger rätt attestkedja (BUG-006, R-05) | Unit + Komp | Grön | `PaymentServiceTest.amountEqualToThreshold_*`, `amountJustOverThreshold_*`; PaymentForm-test |
| Stegen godkänns i ordning och betalningen slutförs först vid sista steget | Unit | Grön | `ApprovalServiceTest.approve_shouldNotCompletePaymentWhileAnotherStepIsStillPending`, `approve_concurrentApproval_shouldThrowAndRollback` |
| Saldo dras vid slutligt godkännande (BUG-009, R-02) | Unit | Delvis | `ApprovalServiceTest.finalApproval_*` |
| Samtidiga godkännanden ger inte dubbel debitering (R-01) | – | Ej testad | Inget incheckat test |

**Avgränsningar och kvar att testa**

- Backend nekar belopp 0. Frontend nekar tomma och negativa belopp samt fler än två decimaler; referens får vara högst 140 tecken. Gränsfallen vid exakt tröskel är testade och frontend hämtar tröskeln från backend.
- `approve_concurrentApproval_shouldThrowAndRollback` testar att ett tidigare steg fortfarande väntar. Namnet antyder samtidighet, men det är inte vad testet verifierar.
- Saldo, status och audit sätts i samma `@Transactional`-metod. Rollback mot riktig databas är inte testad.
- Koden använder `PESSIMISTIC_WRITE` via `PaymentRepository.findByApprovalStepIdForUpdate`. `ApprovalConcurrencyTest` med 20 tester kördes lokalt 2026-09-21 men är inte incheckad. Dubbelgodkännande saknar därför ett test i repot.

### IBAN och BIC

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| IBAN kontrolleras med MOD97 (BUG-003, R-10) | Unit | Grön | `IbanValidatorServiceTest`; `payment/schema.test.ts` (`ibanSchema`) |
| BIC-format enligt ISO 9362 | Unit | Delvis | `IbanValidatorServiceTest.testValidBic/testInvalidBic` |
| Native C-validering via JNA | Unit | Delvis | `native/iban_validator_test.c` · 36 Check-tester |
| Felaktigt IBAN stoppas i formuläret | Komp | Delvis | `PaymentForm.test.tsx` · test för ogiltigt IBAN |

**Avgränsningar och kvar att testa**

- IBAN-fallen omfattar giltiga svenska och tyska nummer, mellanslag och fel kontrollsiffra. Backendtesterna ingår bland de fem som inte verifierats i senaste lokala körningen.
- BIC har bara två fall i regex-fallbacken. Native-modulen är byggd, men Check-testerna körs varken i CI eller Java-sviten; Java kör med native avstängt. `normalize()` saknar bland annat tester för NUL och längd över 34 tecken.
- Formulärvalideringen är testad, men fokus och tabbordning är inte det.

### Badge och notifiering

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Badge visar väntande steg för inloggad attestant (BUG-007) | Komp | Ej testad | Inget komponenttest |
| Bara den aktuella attestantens steg räknas | Unit | Delvis | `ApprovalServiceTest.countPendingByAttestant_shouldReturnNumberOfPendingStepsForUser` |

**Avgränsningar och kvar att testa**

- Badge finns i `SideBar` och `SideBarItem`, men `Badge.test.tsx` saknas.
- Servicetestet frågar på rätt tenant och attestant. `/api/approvals/count` är inte testad på webbnivå.

### Audit och spårbarhet

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Tidslinje visar aktör, händelse och tid i ordning | Int | Delvis | `AuditControllerTest`; `RoleAuthorizationTest` |
| Skapande och godkännande skriver audit; nekad åtgärd gör det inte | Unit | Grön | `PaymentServiceTest.createPayment_shouldRecordAuditEntry`; `ApprovalServiceTest.finalApproval_*`; `ApprovalApiControllerOwnershipTest` |
| Beslutshändelser skrivs atomärt (BUG-008, R-02) | Int | Delvis | Testerna ovan |
| Tenant-filtrering hindrar läckage (R-04) | Int | Ej testad | Inget test av faktisk filtrering |
| Ordningen är stabil vid samma tidsstämpel | Int | Ej testad | Inget sorteringstest |

**Avgränsningar och kvar att testa**

- `AuditControllerTest` använder mockad service och `containsInAnyOrder`. Varken den faktiska sorteringen eller `AuditService.findPaymentEvents` verifieras.
- Audit vid lyckad avvisning och rollback mot riktig databas saknar tester. Nekade åtgärder kontrolleras i ägarskapstesterna.
- Frågorna filtrerar på `tenantId`, men inget negativt test verifierar åtkomst mellan kunder för audit, betalningar eller `my-payments`. `AuditControllerMyPaymentsTest.shouldReturnOnlyOwnPayments` mockar servicen och testar därför inte filtreringen.
- `findPaymentEvents` sorterar på `createdAt, id`, men lika tidsstämplar är inte testade. Även namnet `getMyPaymentStatuses_shouldReturnOnlyOwnPayments` beskriver inte tydligt vad testet verifierar.

### Övriga frontendtester

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Datum och tid visas i svensk tid och sorteras rätt | Unit | Grön | `lib/dateTime.test.ts` · 4 |
| Zod-fel kopplas till rätt formulärfält | Unit | Grön | `lib/zodErrors.test.ts` · 4 |

## 4. Funktioner utanför MVP

| Funktion | Status | Läget |
| --- | --- | --- |
| Avvisa betalning med kommentar | Delvis | Backend har `/reject`. Nekad avvisning är testad; lyckad avvisning, sparad kommentar och UI återstår. |
| Påminnelse via e-post med kö och retry | Ej testad | Inte byggt. |
| Dubbel attest / attestant 2, till exempel VD | Ej testad | Datamodellen stöder flera steg, men hela flödet för attestant 2 är inte byggt. |
| Batch/CSV enligt RFC 4180 med allt-eller-inget | Ej testad | Inte byggt. |

## 5. Nästa testinsatser

| Prioritet | Att göra | Varför |
| --- | --- | --- |
| 1 | Lägg till ett E2E-test av MVP-flödet. | Ingen automatisk körning verifierar hela användarresan. |
| 2 | Checka in och kör samtidighetstestet; testa rollback för saldo och audit mot databas. | Låsning och transaktionsutfall är inte verifierade i repot. |
| 3 | Testa tenant-gränser och de två saknade behörighetsvägarna. | Filtrering och åtkomst till `/api/my-payments` och `/api/approvals/count` behöver verifieras. |
| 4 | Testa skyddade routes och badge; kör native-sviten i CI. | Frontendbeteenden och C-validering saknar löpande verifiering. |
| 5 | Verifiera de fem IBAN-testerna i Actions och rätta de två missvisande testnamnen. | Status och testnamn ska spegla vad som faktiskt körs och testas. |

## 6. Köra testerna

Kör varje block från projektets rot:

```bash
# Backend · JUnit (Maven utan wrapper)
cd backend/SebPortal
mvn test
```

```bash
# Frontend · Vitest
cd frontend
npm run test:run
npm run test:coverage
```

```bash
# Native C · kräver Check (Ubuntu/Debian: sudo apt-get install check)
cd native
make test
```

GitHub Actions körs vid push och PR mot `develop`: `maven.yml` kör `mvn package` och `frontend.yml` kör lint, Vitest och build. Native C körs inte i CI. Något E2E-kommando finns ännu inte.

Täckningsgrad hjälper till att hitta luckor. Det viktiga är att varje AC har ett test som faktiskt verifierar beteendet.