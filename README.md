# SEB Företagsbetalningar (Java) — v2

Ett betalningsportal-system för SEB:s SME-kunder. Initiator skapar betalningar, attestanter godkänner, och varje steg loggas så att hela kedjan går att följa per betalning: vem, vad, när.

Det här är v2, en ombyggnad av det avsiktligt trasiga v1-systemet. Backend är ett REST API med Spring Boot, frontend är en React-app, och de säkerhetskritiska delarna finns som native C-moduler. v1-dokumentationen finns kvar som referens i [docs/v1/](docs/v1/) (se ADR 0002).

**Läs först:** [docs/README.md](docs/README.md) är kartan över all dokumentation, [docs/mvp.md](docs/mvp.md) beskriver vad som ingår i MVP och vad som medvetet skjutits upp, och [DRIFT.md](DRIFT.md) beskriver deploy.

Frontendens installation, kodstruktur, session och testkommandon finns i [frontend/README.md](frontend/README.md). Kända brister och avgränsningar finns samlade i [docs/kanda-brister-och-avgransningar.md](docs/kanda-brister-och-avgransningar.md).

---

## MVP-flödet

> Logga in → skapa betalning över tröskeln → attestkedjan triggas → attestanten får en badge → godkänner → betalningen genomförs och saldot dras atomärt → varje steg loggas och går att följa per betalning.

| Funktion | Roll | Sida |
|----------|------|------|
| Säker inloggning (JWT, BCrypt) | alla | `/login` |
| Översikt över konton och väntande attester | alla | `/` |
| Skapa betalning, IBAN kontrolleras med MOD97 | initiator | `/payments/new` |
| Ladda upp flera betalningar från en CSV-fil och bekräfta dem innan de skapas | initiator | `/payments/new`, fliken Ladda upp flera |
| Följa status på egna betalningar | initiator | `/my-payments` |
| Godkänna eller avvisa betalning, badge visar antal väntande | attestant | `/attest` |
| Granskningslogg och tidslinje per betalning | attestant, admin | `/audit` |
| Lista, skapa och ta bort användare, byta roll | admin | `/admin/users` (API: `/api/admin/users`) |
| Välja ljust eller mörkt läge och sidomeny eller toppmeny | alla | Inställningar i menyn |

Betalningar över tröskeln (`payment.approval.no-attestant-threshold`, 5000 kr) kräver attest, och betalningar över `payment.approval.two-attestant-threshold` (50 000 kr) kräver två olika attestanter i tur och ordning (ADR 0016). Betalningar från ett konto som tillhör ett annat företag nekas. En attestant kan bara hantera sina egna steg, och den som skapat en betalning kan aldrig själv godkänna eller avvisa den. Admin är en administrativ roll som inte skapar, godkänner eller avvisar betalningar (ADR 0017).

---

## Snabbstart

Kräver Docker Desktop (Windows/Mac) eller Docker Engine (Linux).

```bash
git clone https://github.com/chas-challenge-2026/seb-java-team3.git
cd seb-java-team3

cd infra
docker compose up --build
```

Öppna [http://localhost:8084](http://localhost:8084). Imagen bygger React-appen och de native biblioteken och paketerar in dem i Spring-jaren, så ingen separat frontend-container behövs. Port, stoppa, nollställa databasen och vanliga fel står i [DRIFT.md](DRIFT.md).

Seed-användare (skapas av Flyway, alla tillhör samma företag):

| Roll | E-post | Lösenord |
|------|--------|---------|
| Initiator | lisa@malmobygg.se | password123 |
| Attestant | johan@malmobygg.se | password123 |
| Attestant 2 | eva@malmobygg.se | password123 |
| Admin | sara@malmobygg.se | password123 |

Alla seed-konton har giltiga IBAN (MOD97). Lönekontot och Projektkontot fick rättade kontrollsiffror i migrering V15. Som mottagare kan du till exempel använda `SE3550000000054910000003` eller `DE89370400440532013000`. Ett exempel för batch-uppladdning finns i `shared/example-batch.csv`.

### Utveckla utan att bygga om imagen

Kör varje steg i en egen terminal, från projektets rot:

```bash
# 1. Databasen (port 5434 på värden, se infra/docker-compose.override.yml)
cd infra && docker compose up db
```

```bash
# 2. Backend på port 8084
cd backend/SebPortal
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/seb \
SPRING_DATASOURCE_USERNAME=seb SPRING_DATASOURCE_PASSWORD=seb123 \
mvn spring-boot:run
```

```bash
# 3. Frontend med hot reload på port 3000 (proxar /api till 8084)
cd frontend
npm ci
npm run dev
```

Utan de native biblioteken kör backend en Java-implementation av IBAN-valideringen. Bygg dem med `cd native && make lib` och starta backend med `-Djna.library.path=native/build/lib` om du vill testa den native varianten. Med `iban.native-required=true` vägrar appen starta utan biblioteket.

### Hemligheter

Dev-värdena i `application.properties` får aldrig användas i stage eller produktion. Dessa miljövariabler styr hemligheterna där:

| Variabel | Används till |
|----------|--------------|
| `JWT_SECRET` | Valfri fast nyckel för JWT-signering. Utan värde slumpas nyckeln vid start (ADR 0011). `infra/docker-compose.yml` skickar inte vidare variabeln i dag |
| `AUDIT_SIGNING_KEY` | HMAC-nyckel för den signerade granskningsloggen (native audit-signering) |
| `AUDIT_LOG_PATH` | Loggfilens sökväg. I Docker ligger den på volymen `auditlog` (`/app/audit`) så att den överlever omstart |
| `AUDIT_SIGNING_REQUIRED` | Sätt `true` i stage och produktion. Appen vägrar då starta utan native-biblioteket och med dev-nyckeln |

---

## Arkitektur

Lagerdelad backend (ADR 0004): `controller` tar emot anrop och returnerar DTO:er, `service` håller affärslogiken och transaktionerna, `repository` är Spring Data JPA mot PostgreSQL. Databasschemat hanteras av Flyway (migrationerna V1–V15). Inloggning ger en JWT som frontend håller i minnet och skickar i `Authorization`-headern, och en refresh-token i HttpOnly-cookie förnyar den (ADR 0009, ADR 0012). Rollerna kontrolleras med `@PreAuthorize`.

Frontend skickar `Idempotency-Key` vid betalningsskapande. Samma nyckel och uppgifter inom samma användare och företag returnerar samma betalning utan ny debitering eller audithändelse. Ändrade uppgifter med samma nyckel ger 409. Headern är valfri för kompatibilitet med äldre API-klienter; de måste skicka den för att få skyddet vid omförsök. Referenser stöds upp till 140 tecken och backend avvisar belopp som inte kan representeras exakt i ören.

Frontend (React, TanStack Router och Query) byggs i Dockerfilen och serveras av Spring från jar-filen. `SpaFallbackController` skickar frontend-routes till `index.html`, medan `/api/**` fortsätter vara API.

Native C-moduler anropas från Java via JNA. Se [native/README.md](native/README.md).
React-appen och API:t serveras från samma port. Frontenden byggs i Docker och paketeras in i backend-jarfilen, så ingen separat frontend-server behövs. Hur bygget och routingen fungerar, och hur man kör frontenden i dev-läge med `npm run dev`, står i [DRIFT.md](DRIFT.md#så-har-vi-byggt-det-team-3).

---

## Mappstruktur

```
seb-java-team3/
├── .dockerignore                 — Utesluter node_modules, target, .git m.m. från Docker-bygget
├── backend/
│   └── SebPortal/                — Spring Boot 3.3 (Java 21), REST API
│       ├── src/main/java/se/comerit/seb/
│       │   ├── config/           — Tröskelvärden, säkerhetskonfiguration
│       │   ├── controller/       — REST-endpoints (/api/**) och SPA-fallback
│       │   ├── service/          — Affärslogik: betalning, attest, audit, inloggning
│       │   ├── repository/       — Spring Data JPA
│       │   ├── domain/           — JPA-entiteter och enums
│       │   ├── dto/              — Request/response-objekt
│       │   ├── security/         — JWT-tjänst och -filter, rollhantering
│       │   ├── exception/        — Felhantering
│       │   └── infrastructure/   — JNA-wrappers mot native bibliotek (iban, csv, audit)
│       ├── src/main/resources/
│       │   ├── db/migration/     — Flyway-migrationer
│       │   ├── static/           — Statiska filer (React-bygget kopieras hit i Docker)
│       │   └── application.properties
│       ├── src/test/             — JUnit-tester
│       ├── Dockerfile            — Bygger native libs, frontend och jar i fyra steg
│       └── pom.xml
├── frontend/                     — React 19, TypeScript, Vite
│   ├── src/features/             — accounts, attest, audit, auth, confirmation, dashboard, payment, users
│   ├── src/components/, lib/     — Delade komponenter och API-klient
│   └── e2e/                      — Playwright-tester
├── native/                       — C-moduler med egna Makefiles och Check-tester
│   ├── iban/                     — IBAN (MOD97) och BIC (ISO 9362)
│   ├── csv/                      — CSV-parser (RFC 4180, OpenMP)
│   └── audit/                    — Signerad granskningslogg (HMAC-SHA256-kedja)
├── infra/
│   ├── docker-compose.yml        — Plattformsfil, se DRIFT.md
│   └── docker-compose.override.yml — Lokala portar
├── docs/                         — Dokumentation, se docs/README.md
│   ├── decisions/                — Beslutslogg (ADR:er)
│   ├── sprints/                  — Sprintmål och retros
│   └── v1/                       — v1-dokumentation som referens
├── scripts/
│   └── smoke-spa.mjs             — Rök-test mot en app som är igång
├── shared/
│   └── example-batch.csv         — Exempelfil för batchuppladdning
├── .github/workflows/            — CI: Maven, frontend, infra-guard
└── DRIFT.md                      — Drift och deploy
```

---

## Testning

Teststatus per område, med fil och testnamn, finns i [docs/test-status.md](docs/test-status.md). Kör från projektets rot:

```bash
# Backend (JUnit)
cd backend/SebPortal && mvn test

# Frontend (Vitest)
cd frontend && npm run test:run

# Native C (kräver Check: sudo apt-get install check libssl-dev)
cd native && make test

# E2E (Playwright), kräver att appen är igång på port 3000 eller E2E_BASE_URL
cd frontend && npm run test:e2e

# Rök-test mot en app som är igång (standard http://localhost:8084)
node scripts/smoke-spa.mjs
```

GitHub Actions kör native-sviten och `mvn package` med native-integration, samt lint, Vitest och build för frontend, vid push och PR mot `develop`. Ett separat jobb startar en isolerad Docker-stack och kör E2E och SPA-röktestet.

---

## Native moduler

| Modul | Status | Kopplad till backend |
|-------|--------|----------------------|
| `native/iban` — IBAN MOD97 och BIC | Klar, 36 tester | Ja, via JNA med Java-fallback |
| `native/csv` — CSV-batchparser | Klar, 37 tester | Ja, via JNA. `CsvPaymentImportService` använder den för batch-uppladdningen (`POST /api/payments/import`) |
| `native/audit` — signerad granskningslogg | Klar, 39 tester | Ja, via JNA. `AuditService` skriver en signerad rad efter varje committad händelse, kedjan verifieras vid start och syns i `/actuator/health` |

`cd native && make lib` bygger alla tre till `native/build/lib/`. Detaljer, API och format står i [native/README.md](native/README.md).

---

## Status mot v1:s kända buggar

Alla 12 buggar från v1 står i [docs/v1/known-bugs.md](docs/v1/known-bugs.md). Så här ser det ut i v2:

| Bugg | Läge i v2 |
|------|-----------|
| BUG-001 SQL-injektion i inloggning | Åtgärdad. JPA med parametriserade frågor, testad i `AuthServiceInjectionTest` |
| BUG-002 MD5-lösenord | Åtgärdad. BCrypt, migration V7 |
| BUG-003 IBAN saknar kontrollsiffror | Åtgärdad. MOD97 i native modul och i Java-fallback |
| BUG-004 CSV-parser bryter på komma i fält | Åtgärdad. Native parser enligt RFC 4180, kopplad till batch-uppladdningen |
| BUG-005 Partiell batch-insert utan transaktion | Åtgärdad. Hela filen importeras i en transaktion: en felaktig rad stoppar alla betalningar i filen |
| BUG-006 Olika attesttrösklar | Åtgärdad. Ett konfigurerat värde som frontend hämtar från backend |
| BUG-007 Notifieringar sväljs tyst | Delvis. Attest-badge finns, e-postpåminnelser med kö och retry är inte byggda |
| BUG-008 Dubbel audit-logg | Åtgärdad. En audit-tabell som skrivs i samma transaktion som händelsen |
| BUG-009 Saldo dras inte | Åtgärdad vid sista godkännandet, atomärt |
| BUG-010 Hårdkodad uppkoppling | Åtgärdad. Konfiguration via `application.properties` och miljövariabler |
| BUG-011 IDOR i attestkorgen | Åtgärdad. Ägarkontroll av attestssteg |
| BUG-012 Ingen filstorleksgräns för batch | Åtgärdad. Högst 1 MB och 500 betalningar per fil |

---

## Avgränsningar och kända brister

Alla avgränsningar och kända brister, med motivering och hänvisning till beslut och risker, finns samlade i [docs/kanda-brister-och-avgransningar.md](docs/kanda-brister-och-avgransningar.md). De viktigaste avgränsningarna (ADR 0003, [docs/mvp.md](docs/mvp.md)):

- Påminnelser via e-post med kö och retry
- Särskilt VD-steg och fler trösklar än dubbel attest över 50 000 kr (datamodellen stöder flera steg)
- Förfallodatum på betalningar (ADR 0013)
- Compliance-export, 2FA och valutaomvandling (allt hanteras i SEK)

---

## Så har vi bearbetat specialisternas feedforward

Vi har vägt varje punkt mot vad som händer om risken inträffar, kundvärde, tid kvar till leveransen och om ett annat skydd redan finns. Det som skyddar betalningsflödet och kundens data gick först. Resten avgränsade vi med en motivering.

- **CTO (säkerhet):** BCrypt i stället för MD5, skydd mot SQL-injektion, atomära transaktioner, refresh-token i httpOnly-cookie med rotation, accessmatris och stängd eskalering för admin. GUID och `npm audit` i CI är avgränsade. [Detaljer](docs/cto/Risk-feedforward.md), [riskregistret](docs/cto/Risk-register.md).
- **UX:** vem betalningen väntar på och "attest 1 av 2", mer information inför attesten, filter och "Automatiskt godkänd" i auditloggen samt översikt per roll. E-post, påminnelser och förfallodatum är avgränsade (ADR 0013). [Detaljer](docs/ux/UX-feedforward.md).
- **DM:** alla kundbehov i [kravtolkningen](docs/kravtolkning.md) är byggda. Även batch/CSV, som först låg utanför MVP, kom med till slutleveransen.
- **Kvaldemon:** ljust läge som standard, toppmeny som val i inställningarna och en granskningsruta innan man godkänner ([retro v10](docs/sprints/retro-review/sprint-retro-reviewv10.md)).

---

## Fallback för demon

Om livedemon inte fungerar byter vi i den här ordningen:

1. **Den andra miljön.** Prod: https://seb-java-team3.team.chas-challenge.comerit.se. Stage: https://seb-java-team3-dev.team.chas-challenge.comerit.se. Båda har samma seed-användare, se [DRIFT.md](DRIFT.md).
2. **Lokal miljö.** Starta med `docker compose up --build` i `infra/`, se [Snabbstart](#snabbstart).
3. **Skärmdumpar och presentation.** [docs/demo-fallback/](docs/demo-fallback/) innehåller presentationen (`00-Presentation_Powerpoint.pptx`) och skärmdumpar av hela kärnflödet i demoordning, från inloggning (`01`) till auditloggen (`11`). Demoplanen och manuset finns i [Kval-demo-plan.md](docs/demo-fallback/Kval-demo-plan.md).

---

## Teknisk stack

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security med JWT (jjwt), Spring Data JPA, Flyway
- **Databas:** PostgreSQL 12
- **Frontend:** React 19, TypeScript, Vite, TanStack Router och Query, Zod
- **Native:** C99 (gcc), OpenSSL, OpenMP, anropas via JNA
- **Test:** JUnit, H2 och Testcontainers (Postgres), Vitest, Playwright, Check (C)
- **Drift:** Docker Compose, GitHub Actions
