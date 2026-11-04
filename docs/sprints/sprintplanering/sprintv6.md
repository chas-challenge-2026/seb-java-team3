# Sprint v6
Datum: 2026-09-21   Team Lead denna sprint: Jonathan   Backlog-ägare: Pontus
Närvarande: Jonathan, Marcus, Pontus, Adnan, Samuel, Mohammed

## Sprintmål
- CTO-filmen inlämnad i tid (torsdag 24/9 kl 16), byggd kring 2-3 riskhistorier där vi
  visar risk -> beslut -> hur vi verifierar.
- Sekundärt: de risker vi lyfter i filmen är bevisade i koden med gröna tester, inte
  bara påstådda (residualen är verklig, inte ett mål).

## Kapacitet
Vem är tillgänglig och ungefär hur mycket denna vecka?
- team-seb 4 dagar á 8 timmar ev. frånvaro noteras löpande
- OBS: kort vecka, deadline redan torsdag. Reell kapacitet ~3,5 dagar.

## Sprint backlog (draget från product backlog, kopplat till målet)
| Issue | Ägare (story) | Est.       | Kopplar till målet? |
| ----- | ------------- | ---------- | ------------------- |
| #145  | Team-seb      | torsdag v6 | Ja                  |

## Nedbrytning i tasks
CTO-film är inte kod, så FE/API/Service/DB-lagren gäller inte här. Lager = filmens steg.

| Task | Vad | Ägare | Est. (S/M/L) |
| ---- | --- | ----- | ------------ |
| #146 | Välj 2-3 riskhistorier, bekräfta att de är kod-verifierade | ____ | S |
| #147 | Förbered skärmen: register (Summary), kod, gröna tester framme | ____ | M |
| #148 | Spela in i Loom, max 5 min, delad skärm | ____ | M |
| #149 | Review + skapa länk (öppnas utan behörighet) + lämna in i Google Sheet | ____ | S |

## Risker och beroenden (F3)
| Risk / beroende | Sannolikhet | Påverkan | Vad gör vi åt det |
|-----------------|-------------|----------|-------------------|
| Deadline redan torsdag, kort vecka | Hög | Hög | Spela in senast torsdag kl 10 |
| #148/#149 landar på en person | Hög | Medel | 4 spelar in, 1 risk var. |
| Sekundärmålet (tester gröna) hinns inte med | Medel | Medel | Filmen prioriteras; visa bara de risker som faktiskt är verifierade |
| #146 klar sent → #147/#148 kan inte börja | Medel | Hög | #146 klar senast måndag/tisdag (beroende för resten) |

## Beslut denna planering
- (Vägval av betydelse? → skriv ADR i docs/decisions/ och länka här)

## Att bevaka
- [ ] Alla sprint-tasks (#146–#149) har namngiven ägare + estimat innan Ready