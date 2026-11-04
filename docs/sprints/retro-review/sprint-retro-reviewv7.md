# Sprint v7 – Review & Retro

**Datum:** 2026-10-05

## Sprintmål – hur gick det?

Primärt mål var att lämna in DM-filmen och UX-filmen senast torsdag 1/10. Sekundärt mål var att kunna visa flödet live: React-appen serveras i Docker-bygget och ett E2E-test i Playwright täcker MVP-flödet.

**Resultat: Uppnått.** Båda filmerna är inlämnade, React-appen serveras från Docker och E2E-testet finns.

## Review – vad blev gjort?

| Uppgift | Status | Kommentar | Ansvarig |
| --- | --- | --- | --- |
| DM-film | Klar | Inlämnad 1/10 | Adnan, Markus, Mohammed, Samuel |
| UX-film | Klar | Inlämnad 1/10 | Marcus, Pontus |
| Serva React-appen i Docker + fallback-routing | Klar | #177, #180 | Jonathan, Adnan |
| E2E-test för MVP-flödet (Playwright) | Klar | #182 | Pontus |
| Frontend-CI (lint, test, bygge) | Klar | #152, kom till under sprinten | Pontus |
| Gamla filer borttagna | Klar | #153 | Jonathan |
| UX-fixar | Klar | #178, #179 | Marcus |
| CTO-feedforward incheckad | Klar | `docs/cto/Risk-feedforward.md` | Pontus |

## Vad blev inte klart?

| Uppgift | Varför? | Nästa steg |
| --- | --- | --- |
| README för version 2 | Bara delen om hur frontend serveras hann bli klar | v8 |
| Checka in riskregistret | Filmerna gick först | Senare sprint |
| R-04 cross-tenant-test (stretch) | Stretch, filmerna gick först | v8 |
| R-02 rollback-test mot riktig databas (stretch) | Stretch, filmerna gick först | v8 |
| Prod-config: JWT_SECRET m.m. (stretch) | Stretch | v8 |

## Retro

### Vad fungerade bra?

- Vi delade upp oss i två grupper, en för DM-filmen och en för UX-filmen, som vi bestämde i v6-retron. Båda filmerna blev klara till samma deadline.
- Demon tog ett stort kliv: frontend i Docker, E2E-test och CI för frontend kom på plats samma vecka.

### Vad fungerade mindre bra?

- Stretch-målen (R-04, R-02) hann vi inte med alls. De följer med till v8.

### Vad ska vi göra annorlunda i v8?

- Lägga in R-04 och R-02 som huvudmål, inte som stretch.
- Fylla i vad vi prioriterar av CTO-feedforwarden efter PL-mötet.
