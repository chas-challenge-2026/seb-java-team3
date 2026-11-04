# Frontend – SEB Företagsbetalningar v2

React-appen för att skapa, attestera och följa företagsbetalningar. Den använder TypeScript, Vite, TanStack Router för navigering, TanStack Query för serverdata och Zod för validering av formulär och API-svar. Utseendet byggs med CSS Modules och gemensamma UI-komponenter.

## Kom igång

Kräver Node.js 22.12 eller senare i 22-serien och npm, samt en backend med databas. Docker-bygget och CI använder Node 22.

Starta först backend och databas från repo-roten i en egen terminal:

```bash
cd infra
docker compose up --build
```

Starta sedan frontend från repo-roten i en annan terminal:

```bash
cd frontend
npm ci
npm run dev
```

Öppna [http://localhost:3000](http://localhost:3000). Vite skickar `/api` vidare till backend på `http://localhost:8084`, enligt [vite.config.ts](vite.config.ts). Frontend har ingen egen databas och behöver inte byggas om i Docker under utvecklingen.

Vill du köra Java direkt med Maven finns instruktioner i [projektets README](../README.md#utveckla-utan-att-bygga-om-imagen). Drift och SPA-routing beskrivs i [DRIFT.md](../DRIFT.md).

### Testanvändare

Flyway skapar följande användare i samma företag. Alla har lösenordet `password123` i utvecklingsmiljön.

| Roll | E-post |
|---|---|
| Initiator | lisa@malmobygg.se |
| Attestant | johan@malmobygg.se |
| Attestant 2 | eva@malmobygg.se |
| Admin | sara@malmobygg.se |

Använd exempelvis `DE89370400440532013000` som mottagar-IBAN. Flera IBAN i seed-datan har fel kontrollsiffra och stoppas av valideringen. Eva tillkommer i migration V13 och behövs för att prova dubbel attest.

## Sidor och roller

Alla sidor utom `/login` kräver inloggning. Backend kontrollerar behörigheten och frontend anpassar navigation och åtgärder efter rollen.

| Sida | Sökväg | Behörighet |
|---|---|---|
| Inloggning | `/login` | Alla |
| Översikt med konton och rollanpassat innehåll | `/` | Alla inloggade |
| Ny betalning | `/payments/new` | Initiator |
| Mina betalningar och deras status | `/my-payments` | Initiator |
| Attestkorg | `/attest` | Attestant: egna steg; admin: företagets steg, bara läsa |
| Historik och betalningens tidslinje | `/audit` | Attestant och admin |
| Lista, skapa och ta bort användare, ändra roll | `/admin/users` | Admin |

Admin får inte skapa, godkänna eller avvisa betalningar, enligt [ADR 0017](../docs/decisions/0017-admin-ar-en-administrativ-roll.md). Backend kontrollerar roller, företag och ägarskap; spärrarna i [src/router.tsx](src/router.tsx) styr navigeringen.

Admin ser konton, företagets skrivskyddade attestöversikt, historik och användaradministration. Ny betalning och Mina betalningar är bara tillgängliga för initiatorn. Se [accessmatrisen](../docs/accessmatris.md) och [kända brister och avgränsningar](../docs/kanda-brister-och-avgransningar.md).

### Betalningsflödet

Initiatorn väljer konto, fyller i mottagare och belopp, granskar uppgifterna och bekräftar innan betalningen skickas. Därefter visas ett kvitto. Väntande betalningar reserverar tillgängligt saldo.

| Belopp i SEK med standardkonfigurationen | Kräver |
|---|---|
| Högst 5 000 kr | Ingen attest; genomförs direkt |
| Över 5 000 kr till och med 50 000 kr | En attestant |
| Över 50 000 kr | Två olika attestanter i tur och ordning |

Frontend hämtar trösklarna från `/api/payments/config`. Attestanten granskar betalningen i en dialog och godkänner eller avvisar med en valfri anledning. Saldo dras först när betalningen genomförs. Se [MVP och avgränsningar](../docs/mvp.md).

Referensen får vara högst 140 tecken. Backend och databas stödjer samma gräns efter migration V14. Belopp måste kunna representeras exakt i ören och rymmas i databasens `DECIMAL(15,2)`.

## API och session

API-anrop går genom [src/lib/api.ts](src/lib/api.ts), som skickar Bearer-token, hanterar felsvar och kan validera svaret med Zod. API-funktioner och scheman för respektive område finns under `src/features/`.

Access-token hålls i minnet och gäller normalt i 15 minuter. En roterande refresh-token i en HttpOnly-cookie återställer sessionen efter omladdning och förnyar den vid 401. Förnyelse och utloggning finns i [src/lib/authSession.ts](src/lib/authSession.ts). Token sparas inte i localStorage; temavalet sparas där. Se [ADR 0012](../docs/decisions/0012-refresh-token-i-httponly-cookie-med-rotation.md).

Betalningsanrop skickar `Idempotency-Key`. [paymentAttempt.ts](src/features/payment/paymentAttempt.ts) sparar en slumpad nyckel och en hash av uppgifterna i sessionStorage per användare. Om svaret försvinner återanvänds nyckeln vid ett försök med samma uppgifter, även efter omladdning i samma flik. Ett bekräftat svar avslutar försöket. Backend returnerar samma betalning utan ny debitering; ändrade uppgifter med samma nyckel ger 409. Om sessionStorage är blockerad behålls försöket bara i minnet tills sidan laddas om. Andra flikar eller en ny webbläsarsession är separata försök.

`VITE_API_URL` är valfri och läses vid start/bygge. Lämna den normalt osatt så att API:t går via samma origin som appen: Vite-proxyn i utveckling och Spring i Docker. Sessionsanrop använder `credentials: "same-origin"`, så en separat API-origin fungerar inte för cookieflödet med nuvarande konfiguration. En `VITE_`-variabel blir synlig i frontend-bygget och ska inte innehålla hemligheter.

## Kodstruktur

```text
frontend/
├── src/
│   ├── features/       # accounts, attest, audit, auth, confirmation, dashboard, payment, users
│   ├── components/     # Delade UI-komponenter, layout och navigering
│   ├── lib/            # API-klient, session, route-spärrar, QueryClient och hjälpfunktioner
│   ├── pages/          # Bland annat Login och AttestPage
│   ├── css/            # Gemensamma färger och layout
│   ├── assets/         # Bilder och logotyper
│   ├── test/setup.ts   # Testmiljö för Vitest och Testing Library
│   ├── router.tsx      # Routes och behörighetskrav
│   └── main.tsx        # Appstart och providers
├── public/             # Statiska filer
├── e2e/                # Playwright mot en redan startad app
├── vite.config.ts      # Dev-port och API-proxy
└── vitest.config.ts    # jsdom och täckningsrapport
```

Tester ligger intill den kod de testar (`*.test.ts` och `*.test.tsx`).

## Kommandon och tester

Kör kommandona i `frontend/` efter `npm ci`:

| Kommando | Gör |
|---|---|
| `npm run dev` | Startar Vite på port 3000 |
| `npm run build` | Kör TypeScript-kontroll och bygger till `dist/` |
| `npm run preview` | Förhandsvisar `dist/`; Vites dev-proxy ingår inte |
| `npm run lint` | Kör ESLint |
| `npm test` | Kör Vitest i bevakningsläge |
| `npm run test:run` | Kör enhets- och komponenttester en gång |
| `npm run test:coverage` | Skriver V8-täckning i terminalen och till `coverage/` |
| `npm run test:e2e` | Kör Playwright i Chromium |
| `npm run test:e2e:ui` | Öppnar Playwrights testgränssnitt |

Vitest kräver ingen backend. Playwright kräver en redan startad frontend och backend med seed-användare och tillräckligt saldo; testerna skapar betalningar och ändrar databasens saldo. Använd en lokal testmiljö.

```bash
npx playwright install chromium  # Första gången
npm run test:e2e                 # http://localhost:3000

# Alternativt mot den lokala Docker-appen:
E2E_BASE_URL=http://localhost:8084 npm run test:e2e
```

Frontend-CI kör lint, Vitest och build vid push och PR mot `develop`. Ett separat jobb startar en isolerad Docker-stack och kör Playwright samt SPA-röktestet. E2E körs med en worker eftersom testerna delar seed-konton och jämför saldon och badger. Aktuella körresultat och begränsningar finns i [test-status.md](../docs/test-status.md).

## Produktion och felsökning

Dockerfilen bygger `dist/`, kopierar resultatet till Springs statiska resurser och paketerar det i backendens jar-fil. Frontend och API nås då på samma port, lokalt [http://localhost:8084](http://localhost:8084). `npm run preview` saknar den API-proxy som finns i `npm run dev`.

- **API:t går inte att nå:** kontrollera att backend svarar på port 8084 och att Vite-proxyn pekar dit.
- **403:** kontrollera att användarens roll tillåter åtgärden enligt accessmatrisen.
- **Sessionen försvinner efter omladdning:** kontrollera refresh-cookien och `/api/auth/refresh`. Använd localhost eller HTTPS; backend sätter normalt `Secure` på cookien.
- **Ändringar syns inte i Docker:** bygg om med `docker compose up --build` i `infra/`.
- **404 vid direktladdning av en ny route:** Springs SPA-fallback stöder en eller två sökvägsnivåer. Djupare routes kräver en ändring i backend, se [DRIFT.md](../DRIFT.md#så-har-vi-byggt-det-team-3).
- **En referens på 101–140 tecken misslyckas efter uppdatering:** kontrollera att Flyway har kört migration V14.
