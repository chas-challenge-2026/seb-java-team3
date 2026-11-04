# Sprint v9 – Review & Retro

**Datum:** 2026-10-19

## Sprintmål – hur gick det?

Målet var att lämna in kvalplanen i Canvas senast 15/10 och vara demo-redo inför kvaldemon 22/10. Det betydde att R-04 och R-02 skulle vara gröna och att Plan B (inspelning) skulle finnas.

**Resultat: Delvis uppnått.** Kvalplanen är inlämnad och R-02 är grönt. R-04 blev inte klart, och inspelningen och genrepet flyttades till v10.

## Review – vad blev gjort?

| Uppgift | Status | Kommentar | Ansvarig |
| --- | --- | --- | --- |
| Kvalplan (manus, kärnflöde, Plan B, avgränsning, kundfrågor) | Klar | `docs/demo-fallback/Kval-demo-plan.md`, inlämnad i Canvas | Team |
| R-02: rollback-test mot riktig databas | Klar | #219, Testcontainers | Mohammed |
| Refresh-token i backend | Klar | #214, ADR 0012 | Jonathan |
| Frontend kopplad till nya API:er | Klar | #221 | Pontus |
| E2E kontrollerar saldot | Klar | #215 | Marcus |
| Saldo kopplat till frontend | Klar | #218 | |
| BUG-002 | Klar | #216 | Adnan |
| Native CSV-parser | Klar | #220, fristående i C, inte kopplad till portalen | Samuel |

## Vad blev inte klart?

| Uppgift | Varför? | Nästa steg |
| --- | --- | --- |
| R-04 cross-tenant-test | ? | Senare sprint |
| Plan B: inspelning av kärnflödet | Flyttad till veckan före demon | v10 |
| Genrep av demon inom 10 min | Flyttad till veckan före demon | v10 |

## Retro

### Vad fungerade bra?

- R-02 blev äntligen klart, efter att ha flyttats sedan v7.
- Mycket kom in i develop: refresh-token, ny koppling i frontend och saldokontroll i E2E.

### Vad fungerade mindre bra?

- R-04 flyttades för tredje sprinten i rad (v7, v8, v9).
- Fyra PR:er mergades samma dag (15/10), samma dag som kvalplanen skulle lämnas in. Det blir mycket att integrera och testa på en gång.

### Vad ska vi göra annorlunda i v10?

- Spela in Plan B och genrepa tidigt i veckan, inte dagen före demon.
- Merga mindre PR:er oftare i stället för många samtidigt.
