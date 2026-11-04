# Sprint v12

**Datum:** 2026-11-02
**Backlog-ägare:** team-seb-3
**Närvarande:** team-seb-3

## Sprintmål

- Slutleveransen inlämnad senast onsdag 4/11 kl 15.
- Allt putsat, dokumentationen stämmer med koden, `develop` mergad till `main` och prod uppe på den nya versionen.

## Fokus denna sprint

Sista sprinten. Vi bygger inga nya funktioner, bara polish, buggfixar och dokumentation.

## Sprint backlog

| Uppgift | Ägare | Est. | Status |
| --- | --- | --- | --- |
| Polish och buggfixar i Mina betalningar, attestvyn och adminvyerna | Team | M | Klar (#253, #252) |
| Inställningsmeny med val av toppmeny eller sidomeny | Team | S | Klar (#256), feedback från kvaldemon |
| Batch-uppladdning av betalningar via CSV, med den native CSV-parsern | Team | M | Klar (#255) |
| Uppladdningsvyn: konto-id per konto, tydligare instruktioner och bekräftelseruta som visar betalningarna innan de skapas | Team | S | Klar |
| Rätta seed-IBAN så att alla konton klarar MOD97 (migrering V15) och exempelfilen för batch | Team | S | Klar |
| Uppdatera all dokumentation: README, `test-status.md`, kravtolkning, riskregister, CTO- och UX-feedforward, sprintar och retros | Team | M | Klar |
| Merga `develop` till `main` (release v2) | Team | S | Klar (#254, 3/11) |
| Nollställa prod-databasen så att v2 kan köras i prod | Max (techsupport) | S | Klar |
| Lämna in slutleveransen | Team | S | |

## Risker

| Risk | Vad gör vi åt det |
| --- | --- |
| Prod startar inte, eftersom den gamla databasen inte passar v2 | Max nollställde prod-databasen |
| En sen ändring förstör `main` strax före deadline | Efter releasen tar vi bara in små, testade fixar |

## Att bevaka

- [x] `develop` mergad till `main`
- [x] Prod-databasen nollställd
- [x] Dokumentationen uppdaterad
- [ ] Slutleveransen inlämnad 4/11 kl 15
