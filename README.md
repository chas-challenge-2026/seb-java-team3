# SEB Företagsbetalningar (Java) — v2

Ett betalningsportal-system för SEB:s SME-kunder. Initiator skapar betalningar, attestanter godkänner, och varje steg loggas så att hela kedjan går att följa per betalning: vem, vad, när.

Det här är v2, en ombyggnad av det avsiktligt trasiga v1-systemet. Backend är ett REST API med Spring Boot, frontend är en React-app, och de säkerhetskritiska delarna finns som native C-moduler. v1-dokumentationen finns kvar som referens i [docs/v1/](docs/v1/) (se ADR 0002).

**Läs först:** [docs/README.md](docs/README.md) är kartan över all dokumentation, [docs/mvp.md](docs/mvp.md) beskriver vad som ingår i MVP och vad som medvetet skjutits upp, och [DRIFT.md](DRIFT.md) beskriver deploy.

---

## MVP-flödet

> Logga in → skapa betalning över tröskeln → attestkedjan triggas → attestanten får en badge → godkänner → betalningen genomförs och saldot dras atomärt → varje steg loggas och går att följa per betalning.

| Funktion | Roll | Sida |
|----------|------|------|
| Säker inloggning (JWT, BCrypt) | alla | `/login` |
| Översikt över konton och väntande attester | alla | `/` |
| Skapa betalning, IBAN kontrolleras med MOD97 | initiator, admin | `/payments/new` |
| Följa status på egna betalningar | initiator, admin | `/my-payments` |
| Godkänna eller avvisa betalning, badge visar antal väntande | attestant, admin | `/attest` |
| Granskningslogg och tidslinje per betalning | attestant, admin | `/audit` |

Betalningar över tröskeln (`payment.approval.no-attestant-threshold`, 5000 kr) kräver attest. Betalningar från ett konto som tillhör ett annat företag nekas. En attestant kan bara hantera sina egna steg.

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
| Admin | sara@malmobygg.se | password123 |

Kom ihåg att seed-datans IBAN:er inte klarar MOD97, utom `SE4550000000058398257466`. Använd t.ex. `DE89370400440532013000` som mottagare när du provar.

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

Dev-värdena i `application.properties` får aldrig användas i stage eller produktion. Sätt dessa som miljövariabler där:

| Variabel | Används till |
|----------|--------------|
| `JWT_SECRET` | Signering av JWT, se ADR 0009 |
| `AUDIT_SIGNING_KEY` | HMAC-nyckel för den signerade granskningsloggen (native audit-signering) |
| `AUDIT_LOG_PATH` | Loggfilens sökväg. I Docker ligger den på volymen `auditlog` (`/app/audit`) så att den överlever omstart |

---

## Arkitektur

Lagerdelad backend (ADR 0004): `controller` tar emot anrop och returnerar DTO:er, `service` håller affärslogiken och transaktionerna, `repository` är Spring Data JPA mot PostgreSQL. Databasschemat hanteras av Flyway (migrationerna V1–V7). Inloggning ger en JWT som frontend skickar i `Authorization`-headern (ADR 0009), och rollerna kontrolleras med `@PreAuthorize`.

Frontend (React, TanStack Router och Query) byggs i Dockerfilen och serveras av Spring från jar-filen. `SpaFallbackController` skickar frontend-routes till `index.html`, medan `/api/**` fortsätter vara API.

Native C-moduler anropas från Java via JNA. Se [native/README.md](native/README.md).
React-appen och API:t serveras från samma port. Frontenden byggs i Docker och paketeras in i backend-jarfilen, så ingen separat frontend-server behövs. Hur bygget och routingen fungerar, och hur man kör frontenden i dev-läge med `npm run dev`, står i [DRIFT.md](DRIFT.md#så-har-vi-byggt-det-team-3).

---

## Mappstruktur

```
seb-java-team3/
ChasChallenge/
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
│       │   └── infrastructure/   — JNA-wrappers mot native bibliotek (iban)
│       ├── src/main/resources/
│       │   ├── db/migration/     — Flyway-migrationer
│       │   ├── templates/        — Thymeleaf-vyer
│       │   ├── static/           — Statiska filer (React-bygget kopieras hit i Docker)
│       │   └── application.properties
│       ├── src/test/             — JUnit-tester
│       ├── Dockerfile            — Bygger native libs, frontend och jar i fyra steg
│       └── pom.xml
├── frontend/                     — React 19, TypeScript, Vite
│   ├── src/features/             — attest, audit, auth, dashboard, payment
│   ├── src/components/, lib/     — Delade komponenter och API-klient
│   └── e2e/                      — Playwright-tester
├── native/                       — C-moduler med egna Makefiles och Check-tester
│   ├── iban/                     — IBAN (MOD97) och BIC (ISO 9362)
│   ├── csv/                      — CSV-parser (RFC 4180, OpenMP)
│   └── audit/                    — Signerad granskningslogg (HMAC-SHA256-kedja)
├── docs/
│   ├── architecture.md           — Hur v1 är byggd
│   ├── known-bugs.md             — Lista med 12 kända buggar (avsiktliga)
│   ├── README-pain-points.md     — Vad som fungerar vs. går sönder
│   └── v2-targets.md             — Vad ni ska bygga
├── frontend/                     — React + TypeScript (Vite), byggs in i backend-imagen
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

GitHub Actions kör `mvn package` (Maven) och lint, Vitest och build (frontend) vid push och PR mot `develop`. Native-sviten, E2E och rök-testet körs inte i CI ännu.

---

## Native moduler

| Modul | Status | Kopplad till backend |
|-------|--------|----------------------|
| `native/iban` — IBAN MOD97 och BIC | Klar, 36 tester | Ja, via JNA med Java-fallback |
| `native/csv` — CSV-batchparser | Klar, 37 tester | Nej, väntar på batch-uppladdningen |
| `native/audit` — signerad granskningslogg | Klar, 39 tester | Nej, JNA-wrapper och koppling mot `AuditService` återstår |

`cd native && make lib` bygger alla tre till `native/build/lib/`. Detaljer, API och format står i [native/README.md](native/README.md).

---

## Status mot v1:s kända buggar

Alla 12 buggar från v1 står i [docs/v1/known-bugs.md](docs/v1/known-bugs.md). Så här ser det ut i v2:

| Bugg | Läge i v2 |
|------|-----------|
| BUG-001 SQL-injektion i inloggning | Åtgärdad. JPA med parametriserade frågor, testad i `AuthServiceInjectionTest` |
| BUG-002 MD5-lösenord | Åtgärdad. BCrypt, migration V7 |
| BUG-003 IBAN saknar kontrollsiffror | Åtgärdad. MOD97 i native modul och i Java-fallback |
| BUG-004 CSV-parser bryter på komma i fält | Native parser klar (RFC 4180), men inte kopplad till backend än |
| BUG-005 Partiell batch-insert utan transaktion | Batch-uppladdning är inte byggd |
| BUG-006 Olika attesttrösklar | Åtgärdad. Ett konfigurerat värde som frontend hämtar från backend |
| BUG-007 Notifieringar sväljs tyst | Delvis. Attest-badge finns, e-postpåminnelser med kö och retry är inte byggda |
| BUG-008 Dubbel audit-logg | Åtgärdad. En audit-tabell som skrivs i samma transaktion som händelsen |
| BUG-009 Saldo dras inte | Åtgärdad vid sista godkännandet, atomärt |
| BUG-010 Hårdkodad uppkoppling | Åtgärdad. Konfiguration via `application.properties` och miljövariabler |
| BUG-011 IDOR i attestkorgen | Åtgärdad. Ägarkontroll av attestssteg |
| BUG-012 Ingen filstorleksgräns för batch | Batch-uppladdning är inte byggd |

---

## Avgränsningar och kända brister

Det här är medvetet inte byggt i MVP (ADR 0003, [docs/mvp.md](docs/mvp.md)):

- Batch/CSV-uppladdning med allt-eller-inget (parsern finns, endpointen och vyn saknas)
- Avvisa betalning med kommentar (backend har `/reject`, UI och sparad kommentar saknas)
- Påminnelser via e-post med kö och retry
- Dubbel attest, attestant 2 (VD) och tröskeltrappa (datamodellen stöder flera steg)
- Koppling av den signerade granskningsloggen till `AuditService`
- Compliance-export, refresh-token-rotation, 2FA och valutaomvandling (allt hanteras i SEK)

Kända brister i testerna, som samtidighet mot riktig databas och tenant-gränser, står i [docs/test-status.md](docs/test-status.md).

---

## Teknisk stack

- **Backend:** Java 21, Spring Boot 3.3.4, Spring Security med JWT (jjwt), Spring Data JPA, Flyway
- **Databas:** PostgreSQL 12
- **Frontend:** React 19, TypeScript, Vite, TanStack Router och Query, Zod
- **Native:** C99 (gcc), OpenSSL, OpenMP, anropas via JNA
- **Test:** JUnit och H2, Vitest, Playwright, Check (C)
- **Drift:** Docker Compose, GitHub Actions
