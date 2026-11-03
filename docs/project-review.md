# Projektgenomgång och åtgärder 2026-11-03

De fem prioriterade kodfynden från genomgången är åtgärdade. Här står vad som ändrats, hur det verifieras och vilka produktutökningar som återstår. Detta är ingen verifiering av stage/prod.

## 1. Betalningsvalidering

Referensen tillåts nu vara högst 140 tecken i frontend, backend och databas. [Migration V14](../backend/SebPortal/src/main/resources/db/migration/V14__payment_validation_and_idempotency.sql) utökar kolumnen. Backend avvisar längre referenser och NUL med 400 före lagring.

Backend avvisar noll, negativa belopp, belopp som inte kan representeras exakt i ören och belopp över `9999999999999.99`. Avslutande nollor, exempelvis `100.000`, ändrar inte värdet och tillåts. Frontend har även en maxbeloppskontroll.

**Verifiering:** [PaymentCreationIntegrationTest](../backend/SebPortal/src/test/java/se/comerit/seb/controller/PaymentCreationIntegrationTest.java) kör riktig JWT, controller, service, Flyway och Postgres. Referenser med 100/101/140 tecken sparas; 141 tecken och ogiltiga belopp ger 400 utan ändrat saldo, betalning eller audit.

## 2. Admins gränssnitt

Ny betalning och Mina betalningar är spärrade för admin i routern och visas inte i navigationen. Dashboarden hämtar konton och företagets attestkorg, utan initiatorns API-anrop. Admins attestöversikt visar betalningsuppgifter och tidigare beslut, men inga beslutsknappar. Backendens behörighetskontroller gäller fortfarande enligt [ADR 0017](decisions/0017-admin-ar-en-administrativ-roll.md).

**Verifiering:** [admin.spec.ts](../frontend/e2e/admin.spec.ts) kontrollerar riktiga vyer mot Docker, omdirigering från förbjudna routes, att initiatorns API-anrop uteblir och att beslutsknappar saknas. `Badge.test.tsx` och `AttestGrid.test.tsx` verifierar navigation respektive skrivskyddad granskning. `RoleAuthorizationTest` har kompletterats för nekad attestant på betalningskonfigurationen och nekad initiator på betalningstidslinjen.

## 3. Användarhändelser i audit

Backend berikar bara `PAYMENT`-händelser med betalningsuppgifter. Ett användar-id som är lika med ett betalnings-id blandas därför inte ihop med betalningen.

Frontend ger bara betalningshändelser ett `paymentId` och en expanderbar rad. Användarhändelser får svenska etiketter och visar sin beskrivning. Alla användarhändelser behålls i listan; endast betalningar grupperas till senaste händelsen. En användares flera rollbyten döljs därför inte genom grupperingen.

**Verifiering:** `AuditServiceAuditEntriesTest.userEvent_neverBorrowsPaymentDetails_whenTheirIdsCollide`, `mappers.test.ts` och `AuditGrid.test.tsx` prövar id-krockar, flera användarhändelser och att användarrader inte kan öppna en betalningstidslinje.

## 4. MVP-test och CI

MVP-testet stänger kvittodialogen innan det klickar i menyn. Playwright använder en worker eftersom testerna delar seed-användare och jämför saldo och badger före och efter beslut.

[Frontend-CI](../.github/workflows/frontend.yml) har ett separat jobb som installerar Chromium, startar en isolerad Docker-stack med egna volymer, väntar på hälsokontrollen och kör E2E och SPA-röktestet. Rapporter sparas och teststacken tas bort även vid fel. [Maven-CI](../.github/workflows/maven.yml) bygger och kör native-sviten före Java-testerna, så även den riktiga audit-signeringen testas. Testet för okonfigurerad signering har gjorts oberoende av om native-bibliotek finns installerade.

**Verifiering:** nio Playwright-tester, alla 18 SPA-rökkontroller och 112 native-tester passerade lokalt mot den byggda appen respektive en gcc-container. GitHub-jobben kan verifieras på GitHub först efter push.

## 5. Omförsök vid betalningsskapande

Frontend skickar `Idempotency-Key`. En hash av uppgifterna och nyckeln sparas i sessionStorage per användare tills ett validerat svar tagits emot. Ett obekräftat försök kan därför återupptas med samma nyckel efter omladdning i samma flik. Ett bekräftat svar avslutar försöket, så en ny avsiktlig betalning kan få en ny nyckel.

Backend låser skaparraden före uppslagningen. Samma nyckel inom samma användare och företag returnerar samma betalning med dess aktuella status och HTTP 201, utan ny debitering, reservation, atteststeg eller audithändelse. Återanvändning med ändrat konto, IBAN, belopp eller referens ger 409. En unik databasregel ger ytterligare skydd. Nyckeln sparas i samma transaktion som betalningen, så rollback förbrukar den inte.

**API-kontrakt:** headern får innehålla 1–128 bokstäver, siffror, bindestreck eller understreck. Den är valfri för äldre klienter; klienter måste skicka den för att få skydd mot dubbla omförsök. Andra användare och företag har separata nycklar. Andra flikar och nya webbläsarsessioner är separata försök. Om sessionStorage blockeras finns bara minneskopian kvar tills sidan laddas om.

**Verifiering:** `PaymentCreationIntegrationTest` kontrollerar omförsök av både direkt och väntande betalning, konflikter, användar- och företagsgränser, rollback efter ett påtvingat auditfel och samtidiga anrop mot Postgres. Samtidighetstesterna håller skaparraden i en tredje anslutning och kräver att båda anropen väntar på ett riktigt databaslås innan de släpps. Frontendens `api.test.ts` kontrollerar att nyckeln överlever omladdning och skiljs åt för ändrade uppgifter, andra användare och bekräftade försök.

## Kvarstående produktutökningar och driftkontroller

Följande behöver egna funktioner eller åtkomst till driftmiljön och ingår inte i rättningen av de fem kodfynden:

- Omtilldelning av väntande atteststeg när en attestant är frånvarande. Reservationen ligger annars kvar tills tilldelad attestant kan agera.
- Inaktivering av användare med historik och lösenordsbyte. Borttagning finns för oanvända konton.
- Sidindelning eller sökning i audit. API-listan är fortfarande begränsad till 200 händelser.
- Kontroll av riktiga miljövärden i stage/prod. Compose har dev-uppgifter för databasen och audit-signering är inte obligatorisk som standard. Den isolerade teststacken använder endast utvecklingsdata.

Slutlig verifiering: 550 backendtester, 259 frontendtester, 9 E2E-tester, 112 native-tester och 18 SPA-rökkontroller passerade. Lint, frontendbygge och `mvn package` passerade också. Se [test-status.md](test-status.md) för fullständiga körbesked. Migration V14 körs automatiskt av Flyway vid nästa start; befintliga migrationer har inte ändrats. Ändringarna är lokala och har inte deployats.
