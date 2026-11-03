# Teststatus

Här finns en samlad bild av vad som är testat och vilka luckor som återstår. Teststödet anges med fil eller testnamn så att varje punkt går att kontrollera i koden. Uppdatera dokumentet när en task går till Done.

**Senast uppdaterad:** 2026-11-03
**Ansvarig denna sprint:** ____

## 1. Läget just nu

| Svit | Omfattning | Senaste besked |
| --- | --- | --- |
| Backend · JUnit | 55 klasser, 550 tester | Alla passerade 2026-11-03 med `mvn package` i Maven-container, native-bibliotek byggda och Postgres 12 via Testcontainers. Inga överhoppade tester. |
| Frontend · Vitest | 30 filer, 259 tester | Alla passerade 2026-11-03; `npm run lint` och `npm run build` passerade också. |
| Native C · Check | 112 tester: `iban` 36, `csv` 37, `audit` 39 | Alla passerade 2026-11-03 i gcc-container med Check. Maven-CI kör nu samma svit före Java-testerna. |
| Rök-test · byggd app | `scripts/smoke-spa.mjs`, 18 kontroller | Alla passerade mot Docker 2026-11-03, inklusive kontrollerna med token. |
| E2E · Playwright | 6 filer, 9 tester: admin, dubbel attest, MVP, avvisningsorsak, session och smoke | Alla passerade mot isolerad Docker-stack 2026-11-03 med en Chromium-worker. |

Backend verifierades med en ren byggning för att få bort gamla kompilerade migrationsfiler. Därefter passerade hela `mvn package` med de slutliga ändringarna. Native-integrationen kördes med `-Djna.library.path` satt till de byggda biblioteken. Docker-bygget omfattade frontend, backend och native och användes av E2E och röktestet.

`ApprovalServiceTransactionIntegrationTest`, `ApprovalConcurrencyIntegrationTest`, `TenantIsolationIntegrationTest`, `DualApprovalIntegrationTest` och nya `PaymentCreationIntegrationTest` kördes mot Postgres 12. De nya CI-stegen är konfigurerade men själva GitHub-körningen är ännu inte verifierad; ändringarna har inte pushats eller deployats. Tidigare manuella kontroller nedan behåller sina datum. Se [projektgenomgången](project-review.md) för åtgärder och kvarstående produktutökningar.

**Status:** Grön = test finns och passerar; Delvis = delar är testade; Ej testad = test saknas; Röd = test finns men fallerar.  
**Typer:** Unit = enhetstest; Int = integrationstest med exempelvis Spring, MockMvc eller H2; Komp = React-komponenttest; E2E = ett test som går genom hela användarflödet; Rök = kontroller mot en app som är igång, i Docker eller på stage.

Backendens unit-tester använder mockade repositories. De verifierar servicelogik men inte transaktioner eller rollback mot en riktig databas. `AuthServiceInjectionTest`, `AccountRepositoryTenantTest`, `AccountServiceTest`, `AccountBalanceAfterApprovalTest`, `RefreshTokenRepositoryTest`, `RefreshTokenServiceTest`, `UserRepositoryLockTest` och `V10AuditCommentMigrationTest` (som kör de riktiga Flyway-migrationerna) är undantagen: de kör mot H2 i minnet, så där körs SQL-frågorna på riktigt. `ApprovalServiceTransactionIntegrationTest` kör mot Postgres 12 och verifierar att saldo, status, atteststeg och audit antingen sparas tillsammans eller rullas tillbaka tillsammans, vid både godkännande och avvisning, och att meddelandet vid avvisning följer med beslutet (#193, #228). `TenantIsolationIntegrationTest` kör hela appen över HTTP mot Postgres 12 och verifierar att två företag inte kan se eller ändra varandras data (R-04).

## 2. MVP-flödet

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Logga in → skapa betalning → se attest-badge → godkänn → saldo dras → händelsen syns i audit | E2E | Grön | `frontend/e2e/mvp-flow.spec.ts` (#164), passerade mot Docker 2026-11-03 |
| Sista godkännandet slutför betalningen, drar saldo och skriver audit | Unit | Grön | `ApprovalServiceTest.finalApproval_shouldCompletePaymentDeductBalanceAndRecordAudit` |

`mvp-flow.spec.ts` stänger nu kvittodialogen innan menyklicket. Felet från 2026-11-02 är rättat och hela sviten med nio tester passerade mot den byggda Docker-appen 2026-11-03.

E2E-testet går genom hela användarflödet i webbläsaren: initiatören skapar en betalning över tröskeln, attestanten ser badgen, öppnar granskningsrutan, kontrollerar mottagaren och godkänner beloppet, rutan stängs, badgen räknas ned och audit visar vem som skapade och vem som godkände. Saldot läses från `GET /api/accounts` med initiatörens token före och efter, och testet kräver att Driftkonto minskat med exakt beloppet. Kontrollen görs mot API:t. Dashboarden hämtar nu konton från `/api/accounts`, så en kontroll av saldot i UI:t kan läggas till. E2E körs mot en redan startad app. Frontend-CI startar nu en isolerad Docker-stack och kör hela sviten med en worker.

## 3. Teststatus per område

### Inloggning och behörighet

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Login skyddas mot SQL-injektion (BUG-001, R-06) | Int | Grön | `AuthServiceInjectionTest` · 12 tester med H2 |
| Lösenord hanteras med BCrypt (BUG-002, R-06) | Int | Grön | `AuthServiceInjectionTest.controlCase_validCredentialsAuthenticate`; `AuthServiceMd5Test` · 3 (H2); `AuthControllerMd5LoginTest` · 3 (401 för MD5-hash); `V7PasswordMigrationTest` · 5 (Flyway V1–V7) |
| Attestant får bara hantera egna steg (BUG-011, R-03) | Int | Grön | `ApprovalApiControllerOwnershipTest` · 3; `ApprovalServiceTest.approve/reject_shouldThrowAccessDenied_*` |
| Roller och åtkomst via `@PreAuthorize` (#79) | Int | Delvis | `RoleAuthorizationTest` · 30; se `docs/accessmatris.md` |
| Admin är en administrativ roll: skapar, godkänner och avvisar inga betalningar och har inga egna betalningar (ADR 0017) | Int | Grön | `RoleAuthorizationTest.admin*` (403 på skapa, godkänn, avvisa, `/config`, `/changed-count`, `/changed-count/seen`, `/api/my-payments`); `ApprovalApiControllerOwnershipTest.admin_cannotApproveOrRejectAnyStep`; `TenantIsolationIntegrationTest.admin_cannotApproveOrReject_inTheOwnCompanyOrAnother` och `DualApprovalIntegrationTest.anAdminCannotDecideOnAnyStep_butSeesWhatWaits` (Postgres, körda 2026-11-03) |
| Den som skapat en betalning kan inte godkänna eller avvisa den, ens som tilldelad attestant (ADR 0017) | Unit + Int | Grön | `ApprovalServiceTest.approve/reject_shouldRefuseTheCreatorOfThePayment_*`; `ApprovalApiControllerOwnershipTest.approveAndReject_asTheCreatorOfThePayment_*`; `DualApprovalIntegrationTest.theCreatorOfAPaymentCannotCountAsOneOfTheTwo` (Postgres, körd 2026-11-03) |
| Betalningen tilldelas aldrig sin skapare, inte heller vid dubbel attest | Unit + Int | Grön | `PaymentServiceTest.attestantSelection_*` · 2 och `twoStepPayment_*` · 2; `DualApprovalIntegrationTest.aCreatorWhoHasJustBecomeAnAttestant_*` (Postgres, körd 2026-11-03) |
| Delad BCrypt-encoder (kostnad 12) och normaliserad e-post vid inloggning, tom adress slås aldrig upp | Unit + Int | Grön | `PasswordEncoderConfigTest` · 6; `EmailAddressTest` · 4; `AuthServiceEmailTest` · 10 (H2) |
| JWT-signering, utgång och ogiltiga token | Unit | Grön | `JwtServiceTest` · 11; `JwtAuthenticationFilterTest` · 3; `JwtUserContextTest` · 5 |
| Token förfalskad med den gamla, publika hemligheten nekas; nyckeln slumpas vid start (ADR 0011) | Unit | Grön | `JwtServiceTest.tokenForgedWithAKnownSecret_isOnlyAcceptedByAServerUsingThatSecret`, `blankSecret_*`, `compromisedDevSecret_shouldBeRejectedAtStartup`, `explicitSecret_mustBeAtLeast32Bytes` |
| Refresh token: utfärda, rotera, återanvändning spärrar hela familjen, utloggning, utgång och borttagen användare (ADR 0012) | Int | Grön | `RefreshTokenServiceTest` · 15 (H2); `RefreshTokenRepositoryTest` · 9 (H2); `RefreshTokenTest` · 2 |
| Refresh-token: en aktiv token i en familj med spärrad token nekas, och städningen vid inloggning tar inte bort den spärrade raden (granskningen av ADR 0012) | Int | Grön | `RefreshTokenServiceTest.rotate_withAnActiveTokenInAFamilyThatAlreadyHasARevokedToken_*`, `issueForLogin_doesNotWipeTheRevokedMarker*`; `RefreshTokenRepositoryTest.deleteExpiredOrRevokedForUser_keepsARevokedRow*` |
| Refresh-token: lås per användare tas före token läses (inloggning, förnyelse, utloggning) | Unit | Grön | `RefreshTokenServiceLockingTest` · 4; `UserRepositoryLockTest` · 2 (H2) |
| Refresh-cookie och endpoints: HttpOnly, SameSite=Strict, Path, header-krav, 401, 403 och 204 (ADR 0012) | Int | Grön | `AuthControllerRefreshTest` · 13 |
| Inloggning spärrar webbläsarens gamla session, men inte vid fel lösenord | Int | Grön | `AuthControllerRefreshTest.login_revokesThePreviousSessionOfThisBrowser_*`, `login_withWrongCredentials_doesNotRevokeThePreviousSession` |
| Saknad eller ogiltig token mot API ger 401 | Int | Grön | `ApprovalApiControllerSecurityTest.approve_withoutToken/withGarbageToken_returns401` |
| Felsvar lämnar inte ut interna id:n, undantagstexter eller stack traces, och fel som Spring ska hantera behåller sin status (R-18) | Int | Grön | `GlobalExceptionHandlerTest` · 11; `ApprovalApiControllerOwnershipTest`; `TenantIsolationIntegrationTest`; `RoleAuthorizationTest` |
| Loginformulärets validering och fel | Komp | Grön | `LoginForm.test.tsx` · 6; `auth/schema.test.ts` · 7 |
| Skyddade routes och fel roll (#198) | Unit | Grön | `lib/requireAuth.test.ts` · 9; `lib/requireRole.test.ts` · 6 |
| Omladdning förnyar sessionen tyst och sparar användaren; utloggning går via servern (ADR 0012) | Unit | Grön | `lib/requireAuth.test.ts` (utan token i minnet) · 4; `features/auth/useLogout.test.tsx` · 3 |
| Access-token hålls bara i minnet, och en gammal token i `localStorage` tas bort (ADR 0012) | Unit | Grön | `lib/authToken.test.ts` · 5 |
| Tyst förnyelse: en förnyelse åt gången, Web Locks mellan flikar, nytt försök en gång efter 401, ingen loop, cookies bara till session-endpoints (ADR 0012) | Unit | Grön | `lib/authSession.test.ts` · 21; `lib/api.test.ts` (blocket "session refresh on 401") · 10 |
| Sessionen finns kvar efter omladdning, cookien är HttpOnly, utloggning håller (ADR 0012) | E2E | Grön | `frontend/e2e/session.spec.ts` · 3, gröna mot Docker 2026-11-03 och inkluderade i CI |
| API-klientens Bearer-token och felhantering | Unit | Grön | `lib/api.test.ts` · 22 |
| Access-token gäller 15 minuter med standardkonfigurationen (ADR 0012) | Int | Grön | `JwtLifetimeConfigTest` · 1 (läser `application.properties`) |

**Avgränsningar och kvar att testa**

- SQL-injektionstesterna gäller login: sex payloads i e-post, tre i lösenord och ett försök med `DROP TABLE` som lämnar data orörd. Den äldre `DashboardController`, som byggde SQL genom att lägga ihop strängar, togs bort i #153.
- En kvarvarande MD5-hash nekas vid inloggning, även med rätt lösenord eller med hashen som lösenord, och ger 401 utan token. Efter V7 finns inga MD5-hashar kvar i seeden, alla hashar är BCrypt och matchar `password123`, och kolumnen `password_md5` är borta. V7 byter bara ut seedens hash: en användare med annat lösenord behåller sin MD5-hash men nekas vid inloggning. Den lokala Docker-databasen kontrollerades 2026-10-12 (0 MD5-hashar); stage är inte kontrollerad.
- Ägarskapstestet går genom JWT, controller och riktig service. Fel attestant får 403 utan att steg, saldo eller audit ändras. `RoleAuthorizationTest` täcker de tre rollerna, lista, godkänn, avvisa, audit, tidslinje, betalningsskapande och 401. `/api/approvals/count` testas i `ApprovalApiControllerCountTest`. Nekad attestant på `/api/payments/config` och nekad initiator på tidslinjen har nu också rolltest.
- JWT-testerna täcker fel nyckel och trasig token; ogiltig eller saknad token ger ingen autentisering. Nyckelhanteringen (ADR 0011) testas på enhetsnivå: slumpad nyckel per instans, förfalskning med den gamla hemligheten, avvisad läckt hemlighet och minsta längd. En helt startad applikationskontext (utan `jwt.secret`, och med `JWT_SECRET` satt) kontrollerades manuellt 2026-10-06 men finns inte som incheckat test. Formulärtesterna täcker ogiltig e-post, tomt lösenord, trimning och låst knapp under inloggning. API-klienttesterna täcker token med och utan värde, `ApiError` och validering av svar.
- Frontenddelen av refresh token (ADR 0012) är enhetstestad, och kördes för hand 2026-10-14 mot en riktig stack (Postgres 12, backend med refresh, Vite, webbläsare): inloggning lämnar `localStorage` tomt, en omladdning förblir inloggad och roterar token i databasen, utloggning spärrar hela familjen, och en omstart av servern (ny signeringsnyckel) följs av 401, förnyelse och nytt försök utan ny inloggning. Det är inte incheckade tester. `session.spec.ts` täcker omladdning, HttpOnly-cookien och utloggning, och ingår nu i frontend-CI. Två flikar som förnyar samtidigt (Web Locks) är bara enhetstestat. `jwt.expiration-ms` är 15 minuter, så frontend måste ha refresh-token för att inte logga ut användaren var 15:e minut.
- Felsvaren (R-18) är kontrollerade med mutationsprov 2026-11-02, i isolerade kopior: när vidarekastningen till Spring stängdes av föll 13 tester, bland annat de i `RoleAuthorizationTest` som kräver 403. När en läcka togs tillbaka (undantagstext i 500, 403 och 409, steg-id i svaret, eller `include-stacktrace=always`) föll minst ett test varje gång. `IllegalArgumentException` visar fortfarande sin text som 400. Alla ställen i vår kod är genomgångna, men ett sådant undantag från ett bibliotek skulle också visas.
- Omdirigering till login och spärr vid fel roll testas i `requireAuth.test.ts` och `requireRole.test.ts`. Testerna verifierar hjälpfunktionerna; att varje route använder rätt rollista måste också hållas i synk med backend (se adminavvikelsen i accessmatrisen).
- Refresh token (ADR 0012) används av både backend och frontend, och `jwt.expiration-ms` är 15 minuter. Hela flödet över riktig HTTP mot Postgres 12 (Flyway V1–V8, cookie-attribut, rotation, återanvändning som spärrar familjen, utloggning och två samtidiga förnyelser som ger exakt en lyckad) kördes manuellt 2026-10-12. Racet från granskningen (en gammal token och den aktuella används samtidigt) reproducerades mot Postgres 12 2026-10-13: före fixen blev en aktiv token kvar i en spärrad familj, efter låset per användare blev ingen kvar, i båda ordningarna. Enhetstesterna låser fast att låset tas först, men själva samtidigheten bevisas bara av de manuella körningarna, eftersom H2 inte har samma radlåssemantik. De finns inte som incheckade tester. Testcontainers finns nu i sviten (#193), så de kan checkas in som tester mot Postgres.

### Användaradministration (admin)

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Lista och skapa användare kräver ADMIN: 401 utan token, 403 för initiator och attestant, och tjänsten anropas aldrig | Int | Grön | `AdminUserControllerTest` · 19; `UserAdministrationIntegrationTest.onlyAnAdmin_mayListOrCreate_*` (H2) |
| Admin ser bara det egna företaget, med id, namn, e-post och roll och aldrig en hash | Int | Grön | `UserAdministrationIntegrationTest.anAdmin_seesOnlyTheirOwnCompany_*` (H2, två företag) |
| En ny användare hamnar alltid i admins företag, även med ett annat `tenantId` i anropet | Int | Grön | `UserAdministrationIntegrationTest.aNewUser_alwaysBelongsToTheAdminsCompany_*`; `AdminUserControllerTest.create_asAdmin_returns201*` |
| Validering: namn, e-post, lösenord (minst 12 tecken, högst 72 byte) och roll. ADMIN kan inte tilldelas | Unit + Int | Grön | `UserAdminServiceTest` · 42 (parametriserad); `UserAdministrationIntegrationTest.invalidInput_*` |
| Lösenordet sparas som BCrypt kostnad 12 och den nya användaren kan logga in | Int | Grön | `UserAdministrationIntegrationTest.aCreatedUser_getsAHashedPassword_andCanLogIn` (H2) |
| Upptagen e-post ger 409, oavsett stavning och även hos annat företag. Samtidiga dubbletter fångas | Unit + Int | Delvis | `UserAdministrationIntegrationTest.aTakenEmail_*` (H2); `UserAdminServiceTest.createUser_whenTheDatabaseRefusesADuplicate_*` (mockad databas) |
| Rolländring: administratörer (även admins egen) kan inte ändras, annat företag ger 404, attestant med väntande steg ger 409, samma roll är ett no-op | Unit + Int | Grön | `UserAdminServiceTest.changeRole_*`; `UserAdministrationIntegrationTest` (H2); `AdminUserControllerTest.changeRole_*` |
| `CREATE_USER` och `UPDATE_USER_ROLE` loggas utan lösenord och syns bara för admin i det egna företaget | Int | Grön | `UserAdministrationIntegrationTest.creatingAUser_isLoggedForTheAdminOnly_*`, `changingARole_*` |

**Avgränsningar och kvar att testa**

- H2-tabellen har inget unikt index på e-post (det finns bara i Postgres, V1). Dubblettfångsten vid två samtidiga anrop bevisas därför bara av ett enhetstest med en mockad databas, och radlåset i `findByIdAndTenantIdForUpdate` är inte testat mot Postgres.
- En rolländring samtidigt som en betalning skapas är inte testad. `PaymentServiceTest` visar att en betalning aldrig tilldelas sin skapare, men inte tävlingen mellan de två.
- E-postadressen är unik i hela databasen, så en admin kan se om en adress finns hos ett annat företag (409). Testet dokumenterar det.
- Frontend för användaradministration är API-kopplad och har tester i `features/users/`: `api.test.ts`, `schema.test.ts`, `CreateUserModal.test.tsx`, `UserList.test.tsx` och `UserRoleCell.test.tsx`. De ingår i den gröna Vitest-körningen 2026-11-03. Borttagningsdialogen har inget eget test och användarflödet saknar E2E-test. Admins rollanpassning är verifierad i `frontend/e2e/admin.spec.ts`; användarhändelser i audit är inte längre expanderbara och blandas inte ihop med betalningar med samma id.

### Betalning och godkännande

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Skapa betalning med belopp och mottagare, initiatörsbekräftelse och kvitto efter skickad betalning | Unit + Komp + E2E | Grön | `PaymentServiceTest`; `PaymentForm.test.tsx` (bekräftelse, avbrytande, kvitto och väntar-på-attest-status); `PaymentConfirmationDialog.tsx` i `features/confirmation`; `frontend/e2e/mvp-flow.spec.ts`; `payment/schema.test.ts` |
| Betalning från annat företags konto nekas innan något sparas eller loggas (#187) | Unit | Grön | `PaymentServiceTest.fromAccountBelongingToOtherTenant_shouldBeRejected`, `missingFromAccount_shouldBeRejected`, `fromAccountIdOutOfRange_shouldBeRejectedWithoutDatabaseLookup`, `missingTenant_shouldBeRejectedWithoutDatabaseLookup` |
| Godkännande belastar bara kontot om det tillhör betalningens företag, och låser kontot (#191, R-04) | Unit + Int | Grön | `ApprovalServiceTest.approve_shouldRefuseToDebitAccountOfAnotherTenant`, `approve_shouldRefuseInvalidFromAccountIdWithoutLookup` · 5; `ApprovalApiControllerOwnershipTest.approve_whenTheAccountBelongsToAnotherTenant_*`; `AccountRepositoryTenantTest` · 6 (H2) |
| Tröskelvärdet ger rätt attestkedja (BUG-006, R-05) | Unit + Komp | Grön | `PaymentServiceTest.amountEqualToThreshold_*`, `amountJustOverThreshold_*`; PaymentForm-test |
| Stegen godkänns i ordning och betalningen slutförs först vid sista steget | Unit | Grön | `ApprovalServiceTest.approve_shouldNotCompletePaymentWhileAnotherStepIsStillPending`, `approve_concurrentApproval_shouldThrowAndRollback` |
| Saldo dras vid slutligt godkännande (BUG-009, R-02) | Unit | Delvis | `ApprovalServiceTest.finalApproval_*` |
| Attestkorgen visar vilket konto som belastas och vem som skapade betalningen, bara inom attestantens företag (UX-feedback) | Unit + Komp | Grön | `ApprovalServicePendingApprovalsTest` · 4; `AttestGrid.test.tsx` (`shows which account is drawn from and who created the payment`) |
| Väntande betalningar reserverar saldot: en ny betalning får bara använda saldo minus det som väntar på attest, och reservationen släpps vid godkännande eller avvisning | Unit + Int | Grön | `PaymentServiceTest.amountOverWhatIsLeftAfterReservations_*`, `amountWithinWhatIsLeftAfterReservations_*`; `AccountServiceTest.getAccounts_reservesPendingPayments*` (H2); `PaymentForm.test.tsx` (tillgängligt saldo) |
| Attestkorgen har en Granska-knapp per betalning som öppnar den delade bekräftelsedialogen med sammanfattning (belopp, mottagare, konto, saldo efter, skapare, referens). Beslutet fattas i dialogen, Godkänn-knappen visar beloppet, godkännandet anropas först efter bekräftelse, dialogen stängs efter beslut, ett fel visas i dialogen och den kan stängas utan beslut (UX-feedback) | Komp + E2E | Grön | `AttestGrid.test.tsx` (granska) · 5; `frontend/e2e/mvp-flow.spec.ts` |
| Betalningsinitiativtagaren måste bekräfta uppgifterna innan betalningen skapas; bekräftelsen kan avbrytas utan att skapa betalning | Komp | Grön | `PaymentForm.test.tsx` (`submits the normalized data and shows the confirmation on success`, `does not create a payment when the initiator cancels the confirmation`) |
| Avvisa i granskningsrutan med valfri anledning; anledningen sparas, syns i audit och i Mina betalningar, längden kontrolleras (255) och kontrolltecken ger 400 | Unit + Int + Komp | Grön | `ApprovalServiceRejectCommentTest` · 10; `ApprovalApiControllerOwnershipTest.reject_withAControlCharacter_*`; `AttestGrid.test.tsx` (avvisa) · 4; `formatters.test.ts` (`formatRejectionReason`) · 2 |
| Meddelandet vid avvisning sparas atomärt med beslutet (#228): efter commit står det på steget och på auditraden, och vid rollback försvinner det tillsammans med resten. Triggern i testet kontrollerar före felet att meddelandet står på båda raderna | Int (Postgres 12) + Unit | Grön | `ApprovalServiceTransactionIntegrationTest` · 4 (`commits_rejection_message_*`, `rollback_removes_the_rejection_message_*`); `AuditServiceSigningTest.record_withAComment_*` |
| V10 lägger till `audit_entries.comment` och för över meddelandet från avvisningar som gjordes före V10, bara inom auditradens företag (#228, R-04) | Int | Grön | `V10AuditCommentMigrationTest` · 7 (riktiga migrationer på H2); de två testerna för företagsgränsen föll när tenant-villkoret togs bort i ett mutationsprov 2026-11-02. Överföringen kördes också mot Postgres 12 med data 2026-11-02, men det är inte ett incheckat test |
| Ett andra beslut på samma betalning (t.ex. attestanten i två flikar) ger 409 med begripligt meddelande, ändrar ingenting och attestkorgen hämtas om | Int + Unit | Grön | `ApprovalApiControllerOwnershipTest.approve_whenThePaymentWasAlreadyRejected_*`, `reject_whenThePaymentWasAlreadyApproved_*`; `useApprovals.test.tsx` · 3 |
| Samtidiga godkännanden ger inte dubbel debitering (R-01) | Int | Grön | `ApprovalConcurrencyIntegrationTest` · 3 scenarier × 20 körningar mot Postgres 12: dubbelt godkännande, godkänn/avvisa och två betalningar från samma konto (#235) |
| `GET /api/accounts` visar bara det egna företagets konton, sorterade på id (#184, #185, #186, R-04) | Int | Grön | `AccountServiceTest` · 5 (H2); `AccountControllerSecurityTest` · 5 (401 utan/ogiltig token, 200 för INITIATOR, ATTESTANT och ADMIN enligt nuvarande testkod) |
| Saldot i `/api/accounts` stämmer efter en attesterad betalning (#186) | Int | Grön | `AccountBalanceAfterApprovalTest` · 2 (H2) |
| Referensgräns 140 och belopp som kan representeras exakt i ören | Int | Grön | `PaymentCreationIntegrationTest`: referenser 100/101/140 sparas i Postgres; 141 tecken, noll, negativt, tre decimaler och överstort belopp nekas utan sidoeffekter |
| Omförsök av direkt och väntande betalning, nyckelkonflikt, scope, rollback och samtidighet | Int + Unit | Grön | `PaymentCreationIntegrationTest` · 17 totalt för betalningsskapande; `features/payment/api.test.ts` verifierar även återupptagning efter omladdning |

**Avgränsningar och kvar att testa**

- Granskningsrutan bygger på `<dialog>` (`components/ui/overlay/Modal.tsx`). Fokus som stannar i rutan, Esc och klick utanför är webbläsarens beteende. jsdom saknar `showModal`, så Vitest kör med en enkel ersättning i `src/test/setup.ts` och verifierar inte det beteendet. Rutan, avvisningssteget, Esc och mobilbredd kontrollerades i Chromium via Playwright 2026-10-28, men den kontrollen är inte incheckad. `Modal` har inget eget test och testas genom `AttestGrid.test.tsx`.
- Godkännandet har ingen separat "Är du säker?"-fråga. Den extra bekräftelsen är granskningsrutan: det krävs två klick med sammanfattningen emellan, och knappen visar beloppet som godkänns.
- Backend nekar belopp som inte är positiva, inte kan representeras exakt i ören eller inte ryms i `DECIMAL(15,2)`. Referensgränsen är 140 i frontend, backend och databasen efter V14. `PaymentCreationIntegrationTest` kör gränsfallen mot Postgres utan mockar. Gränsfallen vid exakt attesttröskel är testade och frontend hämtar trösklarna från backend.
- `approve_concurrentApproval_shouldThrowAndRollback` testar att ett tidigare steg fortfarande väntar. Namnet antyder samtidighet, men det är inte vad testet verifierar.
- Saldo, status och audit sätts i samma `@Transactional`-metod. `ApprovalServiceTransactionIntegrationTest` (Postgres 12, Testcontainers) verifierar båda utfallen: `commits_balance_and_audit_on_success` och `rollback_on_failure`, där en trigger får commit att misslyckas och saldo, status, steg och audit står kvar orörda. Avvisning och meddelandet vid avvisning testas på samma sätt (#228): `commits_rejection_message_*` och `rollback_removes_the_rejection_message_*`, där triggern också kontrollerar att meddelandet står på både steget och auditraden innan den tvingar fram felet.
- `ApprovalConcurrencyIntegrationTest` (#235) verifierar både betalningslåset (`PaymentRepository.findByApprovalStepIdForUpdate`) och kontolåset (`AccountRepository.findByIdAndTenantIdForUpdate`, #191) mot Postgres 12 med Flyway och JDBC-testdata. Två separata transaktioner startas med `CountDownLatch`; olika databasanslutningar och transaktions-id kontrolleras, och båda måste samtidigt vänta på databaslås innan testets radspärr släpps. Alla väntetider är begränsade och testdata städas efter varje körning. Saldo, betalning, atteststeg, balanshistorik och exakt en auditpost per vinnande beslut kontrolleras.
- Mutationsprovet 2026-11-01 kördes i isolerade kopior under `target`: när enbart `@Lock(PESSIMISTIC_WRITE)` togs bort från betalningsfrågan föll scenario 1 och 2 i samtliga 20 körningar vardera eftersom båda besluten lyckades. När enbart kontolåset togs bort föll scenario 3 i samtliga 20 körningar: saldot blev 700 eller 800 i stället för 500. Kopiornas källfiler återställdes byte för byte; arbetskopians produktionskod ändrades aldrig. Därefter passerade hela `mvn package` med ordinarie låsning.
- Ägarkontrollen av från-kontot görs på två ställen: vid skapande i `PaymentService` via `AccountRepository.existsByIdAndTenantId` (#187) och vid godkännande, där pengarna flyttas, i `ApprovalService` via `findByIdAndTenantIdForUpdate` (#191). Servicetesterna mockar repositoryt; `AccountRepositoryTenantTest` kör båda frågorna mot H2 med konton för två företag och misslyckas om tenant-villkoret eller låset tas bort. Kontolåsningen körs även mot Postgres i `ApprovalConcurrencyIntegrationTest` (#235). Okänt konto, ogiltigt id och annat företags konto ger samma 400-svar, så att konto-id hos andra kunder inte kan kartläggas.

### IBAN och BIC

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| IBAN kontrolleras med MOD97 (BUG-003, R-09) | Unit | Grön | `IbanValidatorServiceTest`; `payment/schema.test.ts` (`ibanSchema`) |
| BIC-format enligt ISO 9362 | Unit | Delvis | `IbanValidatorServiceTest.testValidBic/testInvalidBic` |
| Native C-validering via JNA | Unit | Delvis | `native/iban/iban_validator_test.c` · 36 Check-tester |
| Felaktigt IBAN stoppas i formuläret | Komp | Delvis | `PaymentForm.test.tsx` · test för ogiltigt IBAN |

**Avgränsningar och kvar att testa**

- IBAN-fallen omfattar giltiga svenska och tyska nummer, mellanslag och fel kontrollsiffra.
- Seed-datans IBAN:er klarar inte MOD97, utom `SE4550000000058398257466`. Ett E2E-test som betalar till någon av de andra stoppas därför av valideringen, i formuläret eller med 400 från API:t.
- BIC har bara två fall i regex-fallbacken. Native-modulen är byggd, och Check-testerna körs nu i Maven-CI före Java-sviten. CI bygger även biblioteken och skickar deras sökväg till Java-testerna. `normalize()` saknar bland annat tester för NUL och längd över 34 tecken.
- Formulärvalideringen är testad, men fokus och tabbordning är inte det.

### Badge och notifiering

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Badge visar väntande steg för inloggad attestant och döljs vid noll (BUG-007) | Komp | Grön | `components/ui/navigation/Badge.test.tsx` · 2 |
| Bara den aktuella attestantens steg räknas | Unit + Int | Grön | `ApprovalServiceTest.countPendingByAttestant_shouldReturnNumberOfPendingStepsForUser`; `ApprovalApiControllerCountTest` · 4 (`/api/approvals/count`, delad betalning, noll väntande, admin inom sitt företag) |

**Avgränsningar och kvar att testa**

- `Badge.test.tsx` renderar `SideBar`, där regeln "visa bara vid fler än noll" ligger, med mockade hooks. Att badgen räknas ned efter ett godkännande verifieras i E2E-testet.
- Servicetestet frågar på rätt tenant och attestant, och `ApprovalApiControllerCountTest` testar `/api/approvals/count` på webbnivå.

### Audit och spårbarhet

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Tidslinje visar aktör, händelse och tid i ordning | Int | Delvis | `AuditControllerTest`; `RoleAuthorizationTest` |
| Skapande och godkännande skriver audit; nekad åtgärd gör det inte | Unit | Grön | `PaymentServiceTest.createPayment_shouldRecordAuditEntry`; `ApprovalServiceTest.finalApproval_*`; `ApprovalApiControllerOwnershipTest` |
| Beslutshändelser skrivs atomärt (BUG-008, R-02) | Int | Grön | `ApprovalServiceTransactionIntegrationTest` · 4: commit och rollback för både godkännande och avvisning med meddelande mot Postgres 12. Hela klassen passerade 2026-11-03, se avsnitt 1 |
| Tenant-filtrering hindrar läckage (R-04) | Int | Grön | `TenantIsolationIntegrationTest` · 13 (hela appen över HTTP mot Postgres 12: konton, auditlogg, tidslinje, mina betalningar, attestkorg och antal, godkänn, avvisa, skapa betalning, användarnamn); `AccountRepositoryTenantTest` · 6 och `AccountServiceTest` · 5 (konton, H2) |
| Ordningen är stabil vid samma tidsstämpel | Int | Ej testad | Inget sorteringstest |
| Signerad granskningslogg: kedjan upptäcker ändrad, borttagen, ombytt eller förfalskad rad | Unit | Grön | `native/audit/audit_signer_test.c` · 39 Check-tester; `AuditSignerNativeIntegrationTest` · 5 mot det riktiga biblioteket (hoppas över om `libauditsigner.so` inte är byggd) |
| Signerad rad skrivs först efter commit och aldrig vid rollback | Unit | Grön | `AuditServiceSigningTest` · 7 |
| Signeringsfel stoppar inte flödet, gränser och konfiguration | Unit | Grön | `AuditSignerServiceTest` · 18 |
| Hälsokontroll visar trasig kedja eller saknade rader; `/actuator/health` är öppen utan detaljer | Unit + Int | Grön | `AuditSignerHealthIndicatorTest` · 5; `AuditSignerWiringTest` · 3 (hela appen på H2) |
| Användarhändelser med samma id som en betalning får inga betalningsuppgifter, öppnar ingen tidslinje och behålls utan gruppering | Unit + Komp | Grön | `AuditServiceAuditEntriesTest.userEvent_neverBorrowsPaymentDetails_whenTheirIdsCollide`; `mappers.test.ts`; `AuditGrid.test.tsx` |
| Mina betalningar visar vilket konto betalningen dras från och vem som ska attestera, godkände eller avvisade (UX-feedback) | Unit + Int | Grön | `AuditServiceMyPaymentsTest` · 7; `AuditControllerMyPaymentsTest` (`fromAccountName` och `approvalSteps` i svaret); `features/audit/utils/formatters.test.ts` (`formatAttestant`) · 6 |
| Historiken visar attestantens meddelande (#228): `/api/audit` och `/api/payments/{id}/audit` returnerar det, schemat släpper igenom det och dropdownen visar det under beslutet. Admin ser det en gång: raden "Atteststeg avvisat" bär samma text i API:et men visar den inte | Unit + Komp | Grön | `AuditServiceAuditEntriesTest` (`decisionWithAMessage_*`, `eventWithoutAMessage_*`); `AuditServiceTimelineTest` · 2; `AuditControllerTest` (`comment` i JSON); `features/audit/schema.test.ts` · 4; `formatters.test.ts` (`formatTimelineComment`) · 3; `PaymentAuditStep.test.tsx` · 3; `PaymentAuditDetails.test.tsx` · 2 |

**Avgränsningar och kvar att testa**

- `AuditControllerTest` använder mockad service och `containsInAnyOrder`. Varken den faktiska sorteringen eller `AuditService.findPaymentEvents` verifieras.
- Audit vid lyckad avvisning testas i `ApprovalServiceRejectCommentTest` med mockade repositories och mot Postgres 12 i `ApprovalServiceTransactionIntegrationTest`, där rollback är testad för både godkännande och avvisning (#228). Nekade åtgärder kontrolleras i ägarskapstesterna.
- Attestantens meddelande (#228) kontrollerades för hand 2026-10-29 mot en riktig stack (Postgres 12 med V10, backend, byggd React-app och webbläsare): avvisning med å, ä, ö i attestvyn, Historik som attestant och som admin, ljust och mörkt läge och 390 px bredd. Det är inte ett incheckat E2E-test. Godkännande tar inget meddelande, enligt teamets beslut 2026-11-02.
- `TenantIsolationIntegrationTest` kör två företag mot varandra med riktiga JWT, utan mockar. Det har också rader som korsar företagsgränsen (en betalning hos B som skapats av A:s initiatör och väntar på A:s attestant, auditrader som pekar på det andra företagets betalning, och en auditrad med en användare från det andra företaget), så att det är tenant-villkoret som prövas och inte bara användar-id. Testet är kontrollerat med mutationsprov 2026-10-29: när vart och ett av tolv tenant-skydd togs bort föll minst ett test.
- Två läckor rättades 2026-10-29, och testerna föll mot koden före rättelsen:
  - Användarnamnen i auditloggen och tidslinjen slogs upp utan tenant-villkor. Nu namnges bara det egna företagets användare, och en användare från ett annat företag visas som "Systemet", på samma sätt som en okänd användare (`AuditService.userNamesInTenant`).
  - Godkänn och avvisa kontrollerade betalningens status före företaget, så ett annat företags steg gav 403 om det väntade och 409 om det var behandlat. Nu kontrolleras företaget först, och ett annat företags steg ger samma 400 som ett steg som inte finns (`ApprovalService.findPaymentOfStepForUpdate`).
- Mina betalningar: attestantnamnen slås upp en gång för hela listan, och namn på användare och konton från andra företag visas aldrig (`AuditServiceMyPaymentsTest.aUserFromAnotherCompany_isNeverNamed`, `anAccountOutsideTheCompany_isNeverNamed`). Repositoryt är mockat där; frågan `findByTenantIdAndCreatedByWithApprovalSteps` (betalningar och steg i en fråga) körs mot Postgres i `TenantIsolationIntegrationTest`.
- `findPaymentEvents` sorterar på `createdAt, id`, men lika tidsstämplar är inte testade. Även namnet `getMyPaymentStatuses_shouldReturnOnlyOwnPayments` beskriver inte tydligt vad testet verifierar.

### Servering av frontend (SPA-routing)

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Frontend-routes och `/` forwardas till `index.html` utan token (#160, #161) | Int | Grön | `SpaFallbackControllerTest.root_*`, `frontendRoute_*`, `nestedFrontendRoute_*`, `unknownFrontendRoute_*` |
| `/api/**` och filer forwardas inte; `/api` kräver fortfarande token | Int | Grön | `SpaFallbackControllerTest.unknownApiRoute_*`, `apiRoot_*`, `apiRoute_*`, `assetFile_*`, `rootFile_*`, `indexHtml_*` |
| Byggd app servar React på frontend-routes, filer som filer och `/api` som API (#162) | Rök | Grön | `scripts/smoke-spa.mjs` · 18 kontroller mot Docker 2026-10-28 |

**Avgränsningar och kvar att testa**

- MockMvc utför inte forwarden. Att `index.html` faktiskt serveras ur jar-filen verifieras av röktestet mot Docker, som nu ingår i frontend-CI.
- Mönstren täcker routes med en och två nivåer. `/a/b/c` ger 404, så en ny route med tre nivåer behöver ett mönster och ett test till.
- Direktladdning av `/attest`, `/payments/new`, `/my-payments` och `/audit` samt omladdning på `/attest` kontrollerades manuellt i webbläsare mot Docker 2026-09-29.
- Rök-testet mot stage återstår tills ändringen är mergad och deployad.

### Övriga frontendtester

| Kontroll | Typ | Status | Teststöd |
| --- | --- | --- | --- |
| Datum och tid visas i svensk tid och sorteras rätt | Unit | Grön | `lib/dateTime.test.ts` · 4 |
| Zod-fel kopplas till rätt formulärfält | Unit | Grön | `lib/zodErrors.test.ts` · 4 |

## 4. Funktioner utanför MVP

| Funktion | Status | Läget |
| --- | --- | --- |
| Avvisa betalning med kommentar | Grön | Byggt i #226: avvisa med valfri anledning, som sparas och syns i audit och Mina betalningar. Görs nu i granskningsrutan i attestkorgen. Utvidgat i #228: meddelandet (högst 255 tecken) sparas på steget och på auditraden i samma transaktion och visas i Historik. Godkännande tar inget meddelande, enligt teamets beslut 2026-11-02. Se "Betalning och godkännande". |
| Påminnelse via e-post med kö och retry | Ej testad | Inte byggt. |
| Användaradministration för admin (lista, skapa, byta roll, ta bort) | Delvis | Backend och API-kopplad frontend är byggda. Frontendens enhets- och komponenttester passerar 2026-11-03; E2E för skapa/rollbyte/borttagning återstår. Admins översikt och route-spärrar är däremot verifierade i E2E. Se "Användaradministration". |
| Dubbel attest över 50 000 kr | Grön | ADR 0016. Tröskeln, ordningen, spärren mot att samma person godkänner två gånger, avvisning och reservation körs mot Postgres i `DualApprovalIntegrationTest`. Gränsfallen ligger i `PaymentServiceTest`, och hela flödet i webbläsaren i `frontend/e2e/dual-approval.spec.ts`. |
| Batch/CSV enligt RFC 4180 med allt-eller-inget | Delvis | Native-parsern i `native/csv` har 37 Check-tester, som nu körs i Maven-CI. Endpoint, vy och allt-eller-inget är inte byggda. |

## 5. Nästa testinsatser

| Prioritet | Att göra | Varför |
| --- | --- | --- |
| 1 | Verifiera de nya GitHub-jobben efter push. | E2E, röktest och native-svit är inlagda i arbetsflödena och körda lokalt, men GitHub-körningen återstår. |
| 2 | Lägg till E2E för skapa, byta roll och ta bort användare. | Komponent- och API-tester finns; admin-E2E verifierar läsvyer och route-spärrar. |
| 3 | Testa e-postens unikhet och rolländring samtidigt som en betalning skapas mot Postgres. | Dubblettfångsten för användarskapande har fortfarande bara H2 och mockstöd för samtidighet. |
| 4 | Checka in samtidighetstester för refresh-token mot Postgres. | De manuella körningarna och enhetstesterna finns, men det saknas ett incheckat test av själva racet. |
| 5 | Rätta de två missvisande testnamnen och testa stabil auditsortering vid samma tidsstämpel. | De befintliga testernas avgränsningar beskrivs ovan. |

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

```bash
# E2E · Playwright · kräver att appen är igång (npm run dev eller Docker)
cd frontend
npx playwright install chromium   # första gången
npm run test:e2e
E2E_BASE_URL=http://localhost:8084 npm run test:e2e   # mot Docker-bygget
```

```bash
# Rök-test · kräver att appen är igång (se "Köra lokalt" i DRIFT.md)
# Med SMOKE_EMAIL och SMOKE_PASSWORD körs även kontrollerna som kräver token
node scripts/smoke-spa.mjs
node scripts/smoke-spa.mjs https://<stage-adress>
```

GitHub Actions körs vid push och PR mot `develop`: `maven.yml` kör native-tester och bygger biblioteken före `mvn package` med native-integration. `frontend.yml` kör lint, Vitest och build samt ett separat E2E-jobb som startar en isolerad Docker-stack och kör Playwright och SPA-röktestet. Själva GitHub-körningen av de nya stegen är ännu inte verifierad.

Täckningsgrad hjälper till att hitta luckor. Det viktiga är att varje AC har ett test som faktiskt verifierar beteendet.
