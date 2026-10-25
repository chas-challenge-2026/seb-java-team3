# Sprint v8 – Review & Retro

**Datum:** 2026-10-12  
## Sprintmål – hur gick det?

Målet med sprinten var att få hela MVP-flödet att fungera och kunna visa att det faktiskt fungerar. Vi skulle också bli klara med de sista riskerna (R-04 och R-02), ta bort gammal v1-kod och få med några förbättringar från tidigare feedback.

**Resultat: Delvis uppnått.**

Vi fick klart flera viktiga delar, bland annat dokumentation och annat som behövs inför leverans. Däremot hann vi inte med testerna för R-04 och R-02. De behöver därför göras i nästa sprint.

## Review – vad blev gjort?

|Uppgift|Status|Kommentar|Ansvarig|
|---|---|---|---|
|Konto-API|DOD|Behövdes för E2E-testerna| Adnan |
|Konto-frontend|DOD|Behövdes för E2E-tester|Marcus |
|E2E-test mot körande app|DOD|Beroende av konto-API| Pontus |
|Refresh-token|DOD|Bland annat JWT_SECRET| Jonathan |
|Uppdaterad teststatus|DOD|E2E står som delvis klart|Pontus|
|Gammal v1-kod borttagen|DOD|Bland annat DashboardController| Pontus|
|Dashboard|DOD|Gör en minimal dashboard|Marcus|
## Vad blev inte klart?

|Uppgift|Varför?|Nästa steg|
|---|---|---|
|R-04 cross-tenant-test|?|Görs i v9, ska vara grönt före demo|
|R-02 atomicitet mot riktig databas|?|Görs i v9, ska vara grönt före demo|
|README.md|Samuel fick andra uppgifter med CSV-Batch|Görs i v9|

## Nådde vi sprintmålet?

Vi nådde sprintmålet delvis. Mycket av det som behövs för demon och leveransen är klart, men vi saknar fortfarande två viktiga tester.

Eftersom R-04 och R-02 är saker som kunden kommer att titta på behöver vi prioritera dem direkt i v9, så att vi inte sitter med dem precis innan demon.

## Retro

### Vad fungerade bra?

- Många uppgifter som blev klara, många buggar lösta. Bra docs uppdatering.
### Vad fungerade mindre bra?

- R-04 och R-02 blev uppskjutna ännu en gång. Vi har haft liknande problem tidigare, där större backend-tester inte blivit klara i tid.
- Vi behöver prata om varför det blir så. Handlar det om att vi har för lite tid för backend, att Testcontainers inte kommit igång eller att det varit oklart vem som ansvarar för uppgifterna?
- Det är viktigt att vi kommer fram till vad som faktiskt varit problemet, så att samma sak inte händer nästa sprint.
### Vad ska vi göra annorlunda i v9?

- Börja med R-04 och R-02 direkt i sprinten. Bestäm vem som ansvarar och se till att Testcontainers är uppsatt första dagen.
- Vara hårdare med närvaro. Blir väldigt mycket för de som är här att planera.
