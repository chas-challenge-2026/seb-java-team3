# Kända brister och avgränsningar

Här samlas allt vi vet att v2 inte gör, eller inte gör fullt ut. Varje rad hänvisar till var beslutet eller risken beskrivs i detalj.

- **Avgränsning:** något vi medvetet har valt att inte bygga, med en motivering.
- **Känd brist:** något som finns men har en svaghet, eller som saknas utan att vi har tagit ett beslut om det.

Kontrollerat mot koden på `develop` 2026-11-04.

## Avgränsningar

### Funktioner

| Vad vi inte har byggt | Varför | Läs mer |
| --- | --- | --- |
| Påminnelser och notifieringar via e-post | Kräver e-posttjänst, kö och nya försök vid fel. Kunden beskriver mejl som en del av problemet, så vi valde badges i portalen. | [ADR 0013](decisions/0013-avgransning-av-UX-feedback-infor-MVP.md), [UX-feedforward](ux/UX-feedforward.md) |
| Förfallodatum på betalningar | Påverkar formulär, datamodell och när en betalning får genomföras. | [ADR 0013](decisions/0013-avgransning-av-UX-feedback-infor-MVP.md) |
| Påminnelse, eskalering och omtilldelning när en betalning fastnar hos en attestant | Kräver schemalagda jobb och regler för vem som tar över. Initiatören ser vem betalningen väntar på, och admin ser alla väntande betalningar. | [UX-feedforward](ux/UX-feedforward.md) |
| Särskilt VD-steg och fler trösklar än dubbel attest över 50 000 kr | Datamodellen stöder flera steg, men bara två nivåer är byggda. | [ADR 0016](decisions/0016-dubbel-attest-over-50000.md), [mvp.md](mvp.md) |
| Export av auditloggen (CSV, compliance-export) | Spårbarheten finns i portalen. En export behöver samma behörighet och tenant-filtrering, och tester för det. | [kravtolkning.md](kravtolkning.md), [UX-feedforward](ux/UX-feedforward.md) |
| Tvåfaktorsinloggning (2FA) | Breddning utanför MVP. | [ADR 0003](decisions/0003-mvp-scope-och-avgransningar.md), [mvp.md](mvp.md) |
| Valutaomvandling | Allt hanteras i SEK. | [mvp.md](mvp.md) |
| Kontonummer i CSV-filen | Filen anger avsändarkontot med konto-id. Uppladdningsvyn visar id:t för varje konto, så användaren behöver inte leta. Kontonummer i filen hade krävt ändringar i den native parsern för sent i projektet. | [R-16](cto/Risk-register.md) |

### Säkerhet och teknik

| Vad vi inte har byggt | Varför | Läs mer |
| --- | --- | --- |
| Publika UUID i stället för löpande id:n i API:t | Roll- och tenant-kontrollerna hindrar redan åtkomst med gissade id:n. Bytet berör hela kodbasen. | [Risk-feedforward, punkt 3](cto/Risk-feedforward.md), [R-16](cto/Risk-register.md) |
| `npm audit` som CI-steg | Produktionsberoendena har 0 sårbarheter (kontrollerat för hand 2026-11-04). De 15 medelallvarliga finns bara i utvecklingsverktyg. | [Risk-feedforward, punkt 7](cto/Risk-feedforward.md), [R-19](cto/Risk-register.md) |
| Asymmetrisk JWT-nyckel | Tillför något först när flera tjänster ska verifiera token. Hos oss signerar och verifierar samma backend. | [ADR 0011](decisions/0011-jwt-signeringsnyckeln-slumpas-vid-start.md) |

## Kända brister

### Säkerhet och drift

| Brist | Konsekvens | Läs mer |
| --- | --- | --- |
| Inloggningen begränsar inte antal försök | Lösenord kan gissas utan hinder. | [R-15](cto/Risk-register.md) |
| Content-Security-Policy saknas | Vid XSS kan skadlig kod agera i en öppen session. React escapar output, så risken är låg. | [R-22](cto/Risk-register.md) |
| Postgres 12 och Spring Boot 3.3 får inga säkerhetsuppdateringar | Nya sårbarheter åtgärdas inte. Ett byte av Postgres kräver nollställd databas via techsupport. | [R-23](cto/Risk-register.md) |
| Seed-användare med lösenordet `password123` i stage och prod | Accepterat för demon. Måste bort före verklig drift. | [R-20](cto/Risk-register.md) |
| JWT-nyckeln slumpas per instans | Fungerar bara med en instans. Fler instanser kräver en delad nyckel. | [R-17](cto/Risk-register.md) |
| Texten i `IllegalArgumentException` visas för klienten | Vår egen kod är genomgången, men ett sådant undantag från ett bibliotek skulle också visas. | [R-18](cto/Risk-register.md) |

### Produkt

| Brist | Konsekvens |
| --- | --- |
| Auditlistan visar de senaste 200 händelserna och saknar sidindelning | Äldre händelser syns inte i listan. Varje betalnings egen tidslinje är komplett. |
| Användare kan inte inaktiveras och lösenord kan inte bytas i portalen | Admin kan bara ta bort användare som saknar historik. |
| Betalningar som väntade på attest när dubbel attest infördes behåller sitt enda steg | Gäller bara gamla betalningar över 50 000 kr ([ADR 0016](decisions/0016-dubbel-attest-over-50000.md)). |
| Flera betalningar strax under attestgränsen stoppas inte | En stor summa kan delas upp i flera betalningar utan attest ([ADR 0015](decisions/0015-strikt-storre-an-for-attesttroskeln.md)). |
| Betalningen har inget BIC-fält | BIC-valideringen finns men används inte i flödet ([R-09](cto/Risk-register.md)). |
| Felmeddelandet för ett ogiltigt IBAN i en CSV-fil är på engelska och saknar radnummer | Användaren ser att filen stoppades men inte vilken rad som var fel. Ingen betalning skapas. |

### Tester

Detaljerna står i [test-status.md](test-status.md).

| Saknas | Läge |
| --- | --- |
| Incheckat rollback-test mot riktig databas för CSV-importen och för direktbetalningar | Båda går genom samma transaktion. CSV-importens allt-eller-inget är kontrollerat för hand mot Postgres 2026-11-04. |
| E2E för CSV-importen | Komponent- och servicetester finns. |
| E2E för att skapa, byta roll på och ta bort användare | Komponent- och API-tester finns. Admins läsvyer och route-spärrar har E2E. |
| Samtidighetstest för refresh-token och för unik e-post mot Postgres | Enhetstester och H2-tester finns. |
| Stabil sortering i auditloggen vid samma tidsstämpel | Inget test. |
