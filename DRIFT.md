# Drift och deploy - läs detta först

Denna guide riktar sig särskilt till teamets driftansvariga, men alla i teamet bör känna till innehållet.

## Köra lokalt

Krav: Docker Desktop (Windows/Mac) eller Docker Engine (Linux). På Windows behöver Docker Desktop WSL2, se vanliga fel nedan.

```bash
cd infra
docker compose up --build
```

Appen svarar sedan på http://localhost:PORT. Vilken port som gäller för ert case står i `infra/docker-compose.override.yml`. Inloggningsuppgifter till seed-datan står i README.

- Stoppa: Ctrl+C, eller `docker compose down`
- Börja om med tom databas: `docker compose down -v` och sedan `up --build` igen

## Vanliga fel lokalt

- **"WSL 2 installation is incomplete" (Windows):** kör `wsl --install` i PowerShell som administratör, starta om datorn, starta Docker Desktop igen.
- **"port is already allocated":** någon annan process använder porten. Stäng den, eller ändra porten i `infra/docker-compose.override.yml` (den filen är er att ändra).
- **Kodändringar syns inte:** ni har glömt `--build`.
- **Databasen i konstigt läge:** `docker compose down -v` och börja om. Volymen är bara lokal, inget försvinner i driftmiljön.

## Så funkar deploy

- Push till `develop` bygger om er stage-miljö, push till `main` bygger om prod. Era adresser: stage https://seb-java-team3-dev.team.chas-challenge.comerit.se, prod https://seb-java-team3.team.chas-challenge.comerit.se
- Grön bock eller rött X på committen i GitHub visar hur deployen gick. Vid rött X: klicka på markeringen och läs byggloggen.
- **Ett misslyckat bygge sänker inte er miljö.** Senast fungerande version fortsätter köra tills ett nytt bygge går igenom.
- Bygget tar några minuter. Vid deadline pushar alla team samtidigt och kön blir längre: pusha i god tid.
- Arbetsflöde: testa alltid på `develop` innan ni mergar till `main`.

## Plattformskontraktet - fyra regler

Er deploy-miljö kräver att:

1. `infra/docker-compose.yml` ligger kvar på sin plats
2. webbtjänsten heter `app`
3. `expose` används i `infra/docker-compose.yml`, aldrig `ports` (lokala portar hör hemma i `docker-compose.override.yml`)
4. appens faktiska lyssningsport matchar expose-värdet

En automatisk kontroll körs på varje push och ger rött X med förklaring om regel 1-3 bryts. Regel 4 kan inte kontrolleras automatiskt: byter ni port appen lyssnar på, uppdatera expose-värdet samtidigt.

Allt annat är fritt fram: uppgradera språkversion, ramverk, basimages i Dockerfile, lägga till tjänster i composen och så vidare.

## Byta Postgres-version (v2-kravet)

Databasvolymen i er driftmiljö innehåller datafiler från nuvarande Postgres-version. Byter ni bara image-version startar databasen inte. Gör så här:

1. Testa lokalt först (`docker compose down -v` ger er en färsk lokal volym).
2. Skicka ett techsupport-ärende (kategori "Deploy & CI") INNAN ni pushar versionsbytet till `develop`/`main`, och skriv att ni behöver databas-reset för Postgres-versionsbyte.
3. Vi nollställer volymen i driftmiljön i samband med er deploy.

## Nollställa databasen i driftmiljön

Ni kan inte själ va nollställa databasen i stage/prod. Skicka ett techsupport-ärende: https://chas-challenge.comerit.se/support/

## Frontend i v2

Bygg frontenden i samma Dockerfile som backend (eget byggsteg som kopierar in byggresultatet i backend-imagen). En separat frontend-container får ingen egen publik adress.

### Så har vi byggt det (team 3)

**Bygget.** Allt byggs av `backend/SebPortal/Dockerfile` i fyra steg. Build context är repo-roten (`context: ..` i `infra/docker-compose.yml`).

| Steg | Image | Gör |
|---|---|---|
| `native-build` | ubuntu:22.04 | Bygger IBAN-validatorn `libiban.so` med `make lib` |
| `frontend-build` | node:22-alpine | `npm ci` + `npm run build` → `/frontend/dist` |
| `build` | maven:3.9-eclipse-temurin-21 | Kopierar `dist/` till `src/main/resources/static/`, sedan `mvn package` |
| runtime | eclipse-temurin:21-jre-jammy | Kör jar-filen, med `libiban.so` i `/app/native` |

React-appen hamnar alltså inuti jar-filen (`BOOT-INF/classes/static/`) och serveras av Spring Boot på samma port som API:t. Den färdiga imagen innehåller ingen Node och ingen källkod.

`package.json` och `package-lock.json` kopieras före resten av frontend-koden, så `npm ci` körs bara om när beroendena ändras. Ändrar man bara en komponent byggs frontenden om på några sekunder.

`.dockerignore` i repo-roten utesluter `node_modules`, `dist`, `target`, `native/build`, `.git`, IDE-filer och `.env`-filer från bygget.

**Routing.** `SpaFallbackController` avgör vad som svarar på en sökväg:

| Sökväg | Svarar |
|---|---|
| `/api` och `/api/**` | Spring svarar som API (JSON, 401, 404), aldrig med React |
| Filer med punkt i namnet (`/assets/index-abc.js`, `/favicon.svg`) | Statiska filer från jar-filen |
| `/` samt sökvägar med en eller två nivåer (`/attest`, `/payments/new`) | Forwardas internt till `index.html`, och TanStack visar rätt vy |
| Sökvägar med tre nivåer eller fler | 404 från Spring |

Mer specifika mappningar i andra controllers vinner alltid över fallbacken.

Okända sökvägar med en eller två nivåer (t.ex. `/finns-inte`) når alltså React, som visar sin egen "Not Found". Frontendens routes finns i `frontend/src/router.tsx`.

**Lägger ni till en frontend-route med tre nivåer** (t.ex. `/payments/123/edit`) måste ett nytt mönster läggas till i `SpaFallbackController`. Annars ger omladdning på den sidan 404.

**Utveckla frontend lokalt utan att bygga om Docker.** Starta backend med `docker compose up --build` i `infra/` och kör sedan i `frontend/`:

```bash
npm install
npm run dev
```

Öppna http://localhost:3000. Vite skickar anrop till `/api` vidare till backend på port 8084 (`proxy` i `frontend/vite.config.ts`). Det gäller bara dev-läget. I Docker behövs ingen proxy eftersom allt körs från samma server.

**Vanliga fel med frontenden**

- **Frontend-ändringar syns inte i Docker:** ni har glömt `--build`.
- **Vit sida:** öppna webbläsarens konsol (F12). Om `assets/...js` ger 404 har frontend-bygget inte kommit med, så kontrollera byggloggen för steget `frontend-build`.
- **404 vid omladdning av en sida:** routen har troligen tre nivåer, se ovan.
- **`COPY failed: file not found` i bygget:** kontrollera att `.dockerignore` inte utesluter filen.
- **`npm run build` misslyckas i Docker men inte lokalt:** oftast TypeScript-fel. Kör `npm run build` lokalt i `frontend/` för att se felet.