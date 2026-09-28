# AI-logg

Här dokumenterar vi var AI påverkat beslut, kod eller analys. 

## Så fyller du i

- Ett block per gång AI påverkat något som spelar roll (kod som mergats, ett designbeslut, en analys). Trivial autocomplete behöver inte loggas.
- **Skriv i förstaperson och namnge dig**bedömningen är individuell, så det måste synas vem som granskat.
- Fältet **"Hur jag granskade/ändrade"** är det viktigaste. Där visar du din egen förståelse. "Kopierade rakt av" gäller inte.
- Länka till commit/PR/issue så det går att härleda.
- **Nyast överst**klistra in nya block direkt under `## Logg`, så slipper du scrolla.

## Mall — kopiera och fyll i

Kopiera raderna mellan strecken, klistra in högst upp i loggen, fyll i. Radera fält som inte är relevanta.

---
### ÅÅÅÅ-MM-DD [kort vad] [ditt namn]
- **Verktyg:**
- **Använde AI till:**
- **Genererades:**
- **Hur jag granskade/ändrade:**
- **Valde bort (om något):**
- **Spår:** PR #, issue #
---

## Logg

### 2026-09-24 — Ta bort v1-kod (Thymeleaf-UI + sessionsinloggning) i backend Pontus Ingenius
- **Verktyg:** Claude Code
- **Använde AI till:** Att gå igenom backend och kartlägga vilka filer som är ärvda från v1 och vilka vi byggt i v2, med hjälp av git-historiken (vad som fanns i första commiten `d201ecc` och vad som lagts till sedan). Att kontrollera vad som faktiskt använder de gamla filerna innan något togs bort, och sedan genomföra borttagningen och verifiera att appen fortfarande fungerar.
- **Genererades:**
  - Borttagning av `DashboardController`, `SecurityConfig`, `SessionAuthenticationFilter`, `SessionUserContext`, `CurrentUserRoles`, Thymeleaf-mallarna och `style.css`.
  - Ändringar i `AuthController` och `AuditController`: de gamla sidorna togs bort, och båda är nu `@RestController`. Dessutom ändringar i `Role`, `RoleAccessDeniedHandler`, `JwtSecurityConfig`, `pom.xml` (thymeleaf/jdbc/mail borttagna) och `application.properties`.
  - Uppdateringar i 4 testklasser, commit-meddelande och PR-beskrivning.
- **Hur jag granskade/ändrade:** Jag bad först AI:n lista vad som var gammalt och vad som var nytt innan något togs bort, och lät den återställa en första version för att bestämma mig för om det skulle göras nu eller läggas som task till backend-gruppen. Jag kontrollerade att borttagningen stämmer med v2-kraven (Thymeleaf ska ersättas med REST + React), med vår regel i `ways-of-working.md` om att ta bort sårbar gammal kod i stället för att laga den, och med ADR 0002 (v1-dokumentationen bevaras i `docs/v1/` och v1-koden finns kvar i git-historiken). Den viktigaste risken var att `@EnableMethodSecurity` låg i `SecurityConfig`. Den flyttades till `JwtSecurityConfig`, annars hade alla `@PreAuthorize`-kontroller slutat gälla. Det bekräftas av `RoleAuthorizationTest`. Verifiering:
  - Före ändringen var 67/67 tester gröna, efter ändringen 66/66. Det enda borttagna testet gällde den gamla sessionen.
  - Hela MVP-flödet kördes mot appen i Docker med riktig Postgres: inloggning, skapa betalning över tröskeln, badge-räknare, godkänn, saldot dras och tidslinjen visar händelserna.
  - Fel roll ger 403, ingen token ger 401 och `/dashboard` samt `/login` ger nu 404.
- **Valde bort (om något):**
  - Att bygga in frontend i Dockerfile nu. Det behövs för att demo:n ska fungera på stage och tas som egen task före live-demon.
  - Att fixa att ett dubbelgodkännande ger 500 i stället för 409/400. Buggen fanns redan innan och blockeringen fungerar, bara felkoden är fel. Den blir en egen task.
- **Spår:** Commit `52b6a2c`, branch `cleanup-old-files`, PR #

### 2026-09-21 ADR 0009 Pontus Ingenius

* **Verktyg:** Claude
* **Använde AI till:** Att renskriva och förbättra formuleringarna i ADR 0009 för att göra innehållet tydligare och mer strukturerat.
* **Genererades:** En omformulerad version av texten med tydligare språk och struktur.
* **Hur jag granskade/ändrade:** Jag läste igenom den genererade texten och kontrollerade att innehållet stämde överens med det beslut och den information som skulle dokumenteras.
* **Valde bort (om något):** Inget.

### 2026-09-15 — Tröskelbelopp backend: order-grind + API-status (#118) AdnanZasella
- **Verktyg:** Claude
- **Använde AI till:** Vägledning genom #118 – gick igenom befintlig kod (PaymentService, ApprovalService, Payment/ApprovalStep-entiteterna) tillsammans med AI för att först kartlägga vad som redan var löst från tidigare issues (#43/#45/#46/#50) mot checklistan i #118, innan jag skrev någon kod. Använde AI pedagogiskt för att förstå skillnaden mellan "kö av olika betalningar hos en attestant" och "ordning av flera godkännandesteg på samma betalning" innan jag var redo att skriva villkoret själv. Fick även hjälp att felsöka två egna syntaxfel (saknat kommatecken i PaymentResponse-recordet, samt att jag glömde koppla in de uträknade värdena i return-satsen) genom att läsa kompilatorfelen tillsammans med AI istället för att bara få rättad kod utan förklaring.
- **Genererades:** Förslag på villkoret i `ApprovalService.approve()` som blockerar godkännande av ett steg om ett tidigare steg (lägre stepNumber) fortfarande är PENDING. Förslag på två nya fält i `PaymentResponse` (`currentStepNumber`, `remainingApprovals`) och `from(...)`-logiken som räknar ut dem. Förslag på testmetoder i `ApprovalServiceTest` och `PaymentServiceTest` som täcker order-grinden respektive gränsfallet vid exakt tröskelbelopp. Förslag på PR-titel/beskrivning.
- **Hur jag granskade/ändrade:** Körde `mvn compile` och `mvn test` efter varje ändring innan jag gick vidare, inte bara i slutet – alla 8 tester gröna. Verifierade dessutom end-to-end manuellt i Postman mot lokal Docker-miljö: skapade en betalning över tröskeln som Lisa, bekräftade att `currentStepNumber`/`remainingApprovals` faktiskt kom med i svaret, loggade in som Johan (attestant) och godkände steget, och bekräftade att betalningen försvann från hans väntelista efteråt. Förstod och kunde själv förklara varför `twoAttestantThreshold` fortfarande inte används (createPayment skapar bara ett steg oavsett belopp) innan jag bedömde att gränsfallstestet (exakt 5000 kr) fortfarande var relevant att skriva. Bytte testnamn till att följa filens befintliga mönster (`metod_shouldResultat_whenVillkor`) istället för mitt första förslag som bröt konventionen.
- **Valde bort:** Att bygga en separat `ApprovalChain`-entitet – bedömde att `Payment.approvalSteps` (redan sorterad, redan kopplad) räcker för MVP, ingen anledning till extra indirektion. Att fixa avsaknaden av `GET /api/payments/{id}` – upptäckt under manuell testning men utanför scope för #118, flaggat i PR-beskrivningen istället för att lösa i förbifarten.
- **Spår:** PR #130 · issue #118

### 2026-09-10 — POST /api/payments endpoint + felhantering (#41) AdnanZasella
- **Verktyg:** Claude
- **Använde AI till:** Vägledning genom #41 – design av ny `NewPaymentController` (REST, separat fil från den gamla MVC-baserade `PaymentController`), design av `GlobalExceptionHandler` för strukturerad felhantering, samt felsökning av två separata runtime-buggar som dök upp vid manuell Postman-testning: en NullPointerException i `PaymentService.createPayment` och att `Payment.createdAt` returnerades som null i API-svaret.
- **Genererades:** Förslag på `NewPaymentController.java` (POST-endpoint, DTO-mappning mot befintlig `CreatePaymentRequest`/`PaymentResponse`), `GlobalExceptionHandler.java` (`@RestControllerAdvice` med handlers för `IllegalArgumentException` → 400 och `NoAttestantAvailableException` → 409), samt vägledning i felsökningen av de två buggarna nedan.
- **Hur jag granskade/ändrade:** Testade endpointen manuellt i Postman för både happy path (201) och felfall (400) innan commit, i stället för att bara lita på att koden kompilerade. Vid 500-fel läste jag själv stacktracen tillsammans med AI och identifierade att `ApprovalThresholds.getNoAttestantThreshold()` returnerade null – spårade det till att tröskelvärdena i `application.properties` av misstag var skrivna i YAML-syntax i en properties-fil, och fixade genom att skriva om till korrekt `nyckel=värde`-format. Verifierade fixen genom att köra om samma Postman-request och se att statusen ändrades från 500 till 201. Undersökte separat varför `createdAt` blev null i svaret och spårade det till `insertable=false` på fältet i `Payment`-entityn kombinerat med att entityn aldrig läses om efter save – skapade en egen bug-issue för det i stället för att fixa det inom #41:s scope. Körde `mvn test` och `docker compose up --build` samt kollade `git status` innan commit för att säkerställa att inga `target/`-filer eller den kvarglömda lokala `seed.sql` (borttagen från git i #46) råkade checkas in.
- **Valde bort:** Att lösa auth "på riktigt" nu – `tenantId`/`createdBy` skickas tillfälligt i request-bodyn eftersom JWT-lösningen (#28, #33, #37) inte är klar än; markerat med TODO-kommentar i koden som pekar på #28. Att bygga om den gamla `PaymentController`/`ApprovalController` (v1 MVC-spaghetti) som en del av #41 – bedömde att det är ett separat scope-beslut för epic #31, inte något #41 ska lösa i förbifarten; skapade i stället en ny fil (`NewPaymentController`) bredvid den gamla. Att fixa `Payment.createdAt`-buggen direkt – utanför #41:s scope (rör entity-lagret, inte endpointen), dokumenterad som egen bug-issue i stället.
- **Spår:** PR #100 · issue #41

### 2026-09-07 — Audit-post CREATE_PAYMENT via AuditService.record (#44) AdnanZasella
- **Verktyg:** Claude
- **Använde AI till:** Vägledning genom #44 – bygga audit-lagret (AuditEntry/AuditRepository/AuditService) och koppla in det i PaymentService.createPayment, med egen gransking av förslaget och lite ändringar som kom jag fram till de filerna jag har, samt felsökning av build-fel och Docker/Maven-kommandon körda från fel mapp.
- **Genererades:** Förslag på AuditEntry.java, AuditRepository.java, AuditService.record(...), kopplingen i PaymentService, samt ett nytt Mockito-test som verifierar att record(...) anropas med rätt värden.
- **Hur jag granskade/ändrade:** Jämförde AI:s förslag mot den faktiska V4-migrationen och upptäckte att tenant_id saknades i audit_entries – en känd bugg enligt AuditControllers egna kommentarer. Valde att fixa det nu med en ny migration (V6) istället för att skjuta upp. Körde mvn test och docker compose up --build för att verifiera innan commit, och kollade git status för att undvika att checka in target/-byggfiler.
- **Valde bort:** Att bygga om granskningsloggens visningssida – utanför scope för #44. Att göra action/entityType till enum – onödig komplexitet för en enda action i MVP.
- **Spår:** PR 68, issue #44

### 2026-09-07 — PaymentService.createPayment (#43) AdnanZasella
- **Verktyg:** Claude
- **Använde AI till:** Vägledning genom #43 – ide design av tröskellogik för attestant-krav (0/1/2-attestant-trappa), hur `User`-entity/`Role`-enum/`RoleConverter` skulle byggas ovanpå befintlig `users`-tabell, varför tröskelvärden bör ligga i config (`ApprovalThresholds`) istället för hårdkodade i koden, samt felsökning av tre separata build-fel: saknad `spring-boot-starter-test`-dependency i `pom.xml`, testfil felaktigt placerad i `src/main/java` istället för `src/test/java`, och en saknad `java.util.List`-import.
- **Genererades:** Ide bollninig och förslag på `Role.java`, `RoleConverter.java`, `User.java`, `CreatePaymentRequest`/`PaymentResponse` (DTO:er), `ApprovalThresholds.java`, `PaymentService.createPayment` samt tre unit-tester i `PaymentServiceTest`.
- **Hur jag granskade/ändrade:** Byggde och kompilerade (`mvn compile`) efter varje enskild fil istället för allt på en gång, samma metod som i #45, för att isolera fel direkt. Verifierade `UserRepository.findByTenantIdAndRole` mot den faktiska seed-datan (tenant 1, attestant Johan Berg id 2) istället för att bara lita på att signaturen såg rätt ut. Körde `mvn test` och läste igenom stack traces själv för att förstå varje kompileringsfel (t.ex. att `Cannot find symbol: class Test` berodde på fel filplacering, inte fel kod) innan jag åtgärdade – flyttade testfilen till `src/test/java` och markerade den som Test Sources Root i IntelliJ. Gick igenom samtliga IDE-varningar (databasmappning, config-properties, CVE:er från Mend.io) och bedömde själv vilka som var reella problem kontra kosmetiska IDE-begränsningar.
- **Valde bort:** Att bygga hela 0/1/2-attestant-trappan nu – begränsade #43 till 0/1-attestant-fallet, dokumenterat som TODO i koden, eftersom kundcaset själv listar "dubbel attest" som post-MVP. Att mappa `password_md5`-fältet i `User`-entityn – #43 hanterar inte autentisering, så fältet utelämnades medvetet istället för att mappas "för säkerhets skull". Att bygga en riktig fördelningsalgoritm för attestant-urval vid flera attestanter – valde enklaste möjliga regel (första träffade) och dokumenterade det som avgränsning snarare än att gissa mig till en mer komplex lösning som inte efterfrågats. Att åtgärda CVE-varningarna i `pom.xml` nu – bedömde att de ligger i transitiva Spring Boot-beroenden utanför vår kontroll, dokumenterade som känt problem istället för att lägga tid på en versionsuppgradering utanför scope.
- **Spår:** PR #67 · issue #43

### 2026-09-04 — Payment/ApprovalStep JPA-entities (#45) AdnanZasella
- **Verktyg:** Claude
- **Använde AI till:** Vägledning genom #45 – felsöka en git-historikfråga när feature/45 skulle byggas ovanpå feature/46 innan PR #59 var godkänd, felsöka Maven-installation/PATH lokalt, samt formulera ADR 0007 och denna logg-post.
- **Genererades:** Förslag på `Payment.java` och `PaymentStatus`/`PaymentStatusConverter` utifrån vårt befintliga `V3__create_payments_and_approval_steps.sql`. Resten (`ApprovalStep.java`, `ApprovalStepStatus`/`ApprovalStepStatusConverter`, samt `PaymentRepository`/`ApprovalStepRepository`) skrev jag själv utifrån samma mönster/logik som de genererade klasserna.
- **Hur jag granskade/ändrade:** Kompilerade (`mvn compile`) efter varje enskild fil istället för allt på en gång, för att isolera fel till exakt en fil om något gick sönder. Körde fullständig `docker-compose up --build` mot riktig Postgres från tom volym som slutgiltig verifiering – bekräftade i loggen att alla 5 Flyway-migrationer applicerades och att Hibernates `EntityManagerFactory` initierades utan mappningsfel mot det verkliga schemat. Verifierade själv git-historikfrågan med `git merge-base --is-ancestor` istället för att bara lita på AI:s första förklaring, vilket visade att en tidigare slutsats (att en merge behövdes) faktiskt var fel.
- **Valde bort:** `@Enumerated(EnumType.STRING)` som enklare alternativ till egna converters – hade skrivit enum-namnet i uppercase till databasen och inte matchat lowercase-värdena i schema/seed-data. Att bygga `Tenant`/`Account`/`User` som riktiga entiteter nu – utanför scope för #45, dokumenterat som avgränsning i ADR 0007 istället. Att lägga till `@Version`/optimistisk låsning redan nu – hör till Epic 3 (Java-2:s PaymentService-arbete) och kräver en migration som inte finns än.
- **Spår:** PR #60 · issue #45

### 2026-09-03 — Flyway-migration + Java 21/Spring Boot 3.3.4-uppgradering (#46) AdnanZasella
- **Verktyg:** Claude
- **Använde AI till:** Vägledning genom hela #46 – förstå Flyway-koncept, planera uppdelning av migrationsfiler (V1–V5), granska säkerhetsvarningar (CVE:er) som IntelliJ flaggade i pom.xml, felsöka kompileringsfel och runtime-fel vid uppgradering till Java 21/Spring Boot 3.3.4, formulera commit-meddelande och PR-beskrivning.
- **Genererades:** Innehåll i V1–V5 migrationsfilerna (baserat direkt på befintlig seed.sql, ingen ny datamodell), förslag på Dockerfile-ändringar (JDK-version i båda build-stegen), förklaring av varje CVE och bedömning av om den var relevant för projektets scope.
- **Hur jag granskade/ändrade:** Jämförde varje migrationsfils SQL manuellt mot ursprungliga seed.sql för att säkerställa att inget ändrades i strukturen. Verifierade praktiskt att lösningen fungerade genom docker-compose up --build och läste igenom hela Flyway-loggen för att bekräfta att alla 5 migrationer applicerades korrekt och i rätt ordning. Förstod och kunde själv motivera varför javax→jakarta-bytet behövdes (Spring Boot 3.x namespace-byte) innan jag gjorde ändringen i de 6 controller-filerna. Tog själv beslutet att göra en samlad commit istället för flera, efter att ha förstått att mellanliggande tillstånd inte skulle kompilera.
- **Valde bort:** Föreslagna CHECK-constraints på payments.status/amount i migrationen – bedömde att valideringslogik hör hemma i applikationslagret (#43), inte i databasschemat, för att hålla #46 avgränsad till sitt syfte. Föreslagen omskrivning av audit_entries.user_id till att inkludera FK mot users – valde att behålla utan FK för att inte riskera att audit-loggning någonsin blockeras.
- **Spår:** PR #59 · issue #46

### 2026-08-27 Mall för Backlog Pontus.I
- **Verktyg:** Claude
- **Använde AI till:** Att snygga till våran backlog.md struktur och stavfel
- **Genererades:**
- **Hur jag granskade/ändrade:** Granskning av storys så att inget ändrades ifrån orginal tankar
- **Valde bort (om något):**
- **Spår:** PR #, issue #

### 2026-09-08 — Testvektorer för MOD97-sviten  [namn] (C-1)
- **Verktyg:** Claude
- **Använde AI till:** Ta fram testvektorer för MOD97-sviten.
- **Genererades:** 50 IBAN-nummer med förväntat giltigt/ogiltigt-resultat.
- **Hur jag granskade/ändrade:** Verifierade 10 st manuellt mot officiell MOD97-beräkning, hittade och rättade 2 felaktiga förväntningar, la till 5 svenska IBAN som saknades.
- **Valde bort:** 
- **Spår:** PR #42 · issue #16

---
### 2026-09-9 — Migrering till JPA för Inloggning(Backend) + Endpoint fix — Jonathan Isaksson
- **Verktyg:** Claude
- **Använde AI till:** Att få översikt över koden och se till att testa kod flödet samt ge förslag där det behövdes.
- **Genererades:** Förslag på förbättringar i koden.
- **Hur jag granskade/ändrade:** Fick översikt med hjälp av AI, gick sedan igenom bitar av koden och började ändra. Sedan fick AI testa flödet och ge mig feedback på förslag av ändringar.
- **Valde bort (om något):** -
- **Spår:** PR #95, issue #33
---

---
### 2026-09-15 — Migrering och Implementering av JWT — Jonathan Isaksson
- **Verktyg:** Claude
- **Använde AI till:** Gav riktning och testade flödet samt hjälpte till med integrationstester.
- **Genererades:** Tester som prövar flödet, och ändrade i redan etablerade filer.
- **Hur jag granskade/ändrade:** Testade varje del och ändring att det som ändrades, såg till att inget går sönder och att ändringen ger det resultatet som är förväntat. Om inte görs det om på annat sätt.
- **Valde bort (om något):** -
- **Spår:**
---
### 2026-08-17 Förståelse för TanStack Query Pontus Ingenius

* **Verktyg:** ChatGPT
* **Använde AI till:** För att få hjälp att förstå hur TanStack Query fungerar, framför allt hur queries används för att hämta och hantera data från backend samt hur caching, loading states och error states fungerar.
* **Genererades:** Förklaringar och exempel på hur `useQuery` används, hur `queryKey` och `queryFn` fungerar samt hur TanStack Query hanterar caching och uppdatering av data.
* **Hur jag granskade/ändrade:** Jag jämförde AI:s förklaringar och kodexempel med vår befintliga kod och testade koncepten i projektet för att säkerställa att jag förstod hur de fungerade i praktiken. Jag anpassade exemplen till projektets struktur istället för att kopiera dem direkt.
* **Valde bort (om något):** Jag valde bort delar av exemplen som inte var relevanta för projektet och använde främst AI för förståelse snarare än för att generera färdig kod.
* **Spår:** PR #, issue #

## Tips

- Loggar du löpande  t.ex. samtidigt som du skriver PR-beskrivningen blir det aldrig ett berg att beta av i v12.
- Föreslog AI något du **valde att inte** använda? Skriv ett block om det ändå. Att avstå med motivering visar omdöme, och det är precis den självständighet examinatorn letar efter.
- Vill någon se alla sina egna poster? Sök på ditt namn (Ctrl+F) — därför står namnet i varje rubrik.
