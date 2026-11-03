# Sprint v7
Datum: 2026-09-28   Team Lead denna sprint:  Jonathan   Backlog-ägare:  Pontus
Närvarande: Jonathan, Marcus, Pontus, Samuel, Mohammed, Markus

## Sprintmål
-  Primärt: DM-film och UX-film (Deadline. Torsdag 1/10-26)
-  Sekundärt: flödet går att visa live end-to-end. React-appen serveras i Docker-bygget och ett Playwright-E2E täcker MVP-tråden (inloggning -> skapa -> badge -> godkänn -> saldo -> audit)
## Kapacitet
Vem är tillgänglig och ungefär hur mycket denna vecka?
- team-seb  4 dagar 8 timmar - ev. frånvaro
## Sprint backlog ( kopplat till målet)
| Uppgift                                                      | Ägare | Est. | Kopplar till målet?        |
| ------------------------------------------------------------ | ----- | ---- | -------------------------- |
| DM-film                                                      |Adnan, Markus, Mohammed, Samuel| M    | Ja, primärt                |
| UX-film (Emma), visa prototyp/flöden                         |Marcus, Pontus| M    | Ja, primärt                |
| Serva React-appen i Dockerfile + fallback-routing (Kvar p.1) |Jonathan, Adnan| L    | Ja, live-demo              |
| Ett E2E-test för MVP-tråden, Playwright                      | Pontus | M    | Ja, live-demo              |
| README för Verision 2                                        |       | S    | Ja, examinator läser först |
| Checka in riskregistret i repot                              |Pontus| S    | Ja, spårbart               |
## Stretch (tas om tid finns, annars v8)
| Uppgift                                                               | Ägare | Est. |     |
| --------------------------------------------------------------------- | ----- | ---- | --- |
| R-04 negativt cross-tenant-test                                       |       | M    |     |
| R-02 atomicitet: rollback-test mot riktig DB                          |       | M    |     |
| Prod-config: JWT_SECRET, iban.native-required, rensa v1-inställningar |       | s    |     |

## Nedbrytning i tasks (lager: FE / API / Service / DB / Test)

### DM- & UX-filmer (primärmål, deadline torsdag(1/10-26))

| Task                                                     | Lager                   | Ägare          | Est. (S/M/L) |
| -------------------------------------------------------- | ----------------------- | -------------- | ------------ |
| #156 Serva React i Docker + fallback (demo-blockerare 1) | Build, api, test & docs | Jonathan       | L            |
| #164 E2E-test för MVP-tråden(Playwirght)                 | Test                    | Pontus         | M            |
| Välj innehåll DM-film                                    | Film                    |Adnan, Markus, Mohammed, Samuel           | S            |
| Välj innehåll UX-film (UX-val, antaganden, flöden)       | Film                    | Marcus, Pontus | S            |
| Spela in DM-filmen (Loom, max 5 min)                     | Film                    |Adnan, Markus, Mohammed, Samuel| S            |
| Spela in UX-filmen (Loom, max 5 min)                     | Film                    |Marcus, Pontus | S            |
| Lämna in UX-film                                         | Film                    | Marcus,Pontus  | S            |
| Lämna in DM-film                                         | Film                    |Adnan, Markus, Mohammed, Samuel | S            |

## Risker och beroenden (F3) 
| Risk / beroende                       | Sannolikhet | Påverkan | Vad gör vi åt det                                                           |
| ------------------------------------- | ----------- | -------- | --------------------------------------------------------------------------- |
| Hård deadline torsdag för TVÅ filmer  | Hög         | Hög      | Välj innehåll mån/tis                                                       |
| Frontend-deploy större än estimerat   | Medel       | Hög      | Tidboxa; hinns den inte, kör E2E mot lokal miljö så demon ändå går att visa |
| R-04 tenant-läcka fortfarande otestad | Medel       | Hög      | Parkerat men får ej glömmas; står som Ej testad i test-status.md            |
| PL-mötet imorgon omprioriterar        | Säkert      | Låg      | Målet satt idag, backloggen bekräftas efter mötet                           |
## Beslut denna planering - 
- Vägval (Kvar p.4): dubbelgodkännande använder pessimistiskt lås, men AC säger @Version. Beslut behövs: (a) behåll pessimistiskt lås + skriv ADR som ändrar kriteriet, eller (b) inför @Version + migration V8. Pessimistiskt lås hindrar också dubbelgodkännande, så alt. (a) är billigast och fullt försvarbart. Ta med PL/team, skriv ADR i docs/decisions/. 
- Efter PL-möte: fyll i vad ni prioriterar av CTO-feedforwarden.

## Att bevaka
- [ ] Båda filmlänkarna öppnas utan behörighet (Google Sheet, DM- och UX-raden) 
- [ ] Riskregistret incheckat i repot
- [ ] Fredagens F8-dialoger inbokade 
- [x] Alla v7-tasks har ägare + estimat innan Ready 
- [ ] Efter PL-möte: backlog justerad enligt CTO-feedforward