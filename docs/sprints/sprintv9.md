# Sprint v9

**Datum:** 2026-10-12
**Backlog-ägare:** team-seb-3
**Närvarande:** Pontus, Jonathan, Marcus 

## Sprintmål

**Huvudmål:**  
- Primärt: kvalplanen klar, bollad med PL och inlämnad i Canvas senast torsdag 15/10. - Primärt: demo-redo inför kvaldemon 22/10. Kärnflödet går live, de två sista trygghetsbevisen är gröna (tenant-isolering R-04, atomicitet R-02 mot riktig DB), och Plan B (inspelning) finns.
- Sekundärt: demon genrepat minst en gång inom 10 min, med roller fördelade.

## Fokus denna sprint

Den här sprint är fokus på stabilisering och eventuella sista justeringar.
Samt att vi ska vara klara med vår demo-plan.

## Viktigt beroende
Uppgifter som släpar ifrån förra veckan behöver vara klara senast idag. 2026-10-12
Uppgifterna är: 
- Tenant-isolering (R-04)
- Atomicitet mot riktig databas (R-02)
- Gammal v1-kod ska vara borttagen

## Sprint backlog

### Prioriterade uppgifter denna sprint

| Uppgift                                                                                                            | Ägare       | Est. | Kommentar                                                |     |
| ------------------------------------------------------------------------------------------------------------------ | ----------- | ---- | -------------------------------------------------------- | --- |
| Färdigställ basplan (5 punkter: manus, kärnflöde, Plan B, avgränsning, kundfrågor)                                 | Hela team   | M    | Finns redan i utkast                                     |     |
| R-02: testa atomicitet mot riktig databas med Testcontainers                                                       | Mohammed. H | M    | Verifieras mot riktig databas istället för enbart mockar |     |
| Lämna in i Canvas senast torsdag 15/10 kl 17                                                                       | Team        | S    |                                                          |     |
| R-04: negativt cross-tenant-test. Tenant A ska inte kunna se tenant B audit, tidslinje, betalningar eller attester | Markus      | M    | Viktigt säkerhetstest                                    |     |
### Demo-förberedelse 
| Uppgift                                                               | Ägare | Est. | Not                          |
| --------------------------------------------------------------------- | ----- | ---- | ---------------------------- |
| Spela in Plan B: Loom av hela kärnflödet + skärmdumpar av nyckelvyer  | Team  | S    | Kundkriterium: trygghet      |
| Genrep av demon end-to-end inom 10 min, fördela vem som gör/säger vad | Team  | M    | Flera röster, inte en person |
## Beroenden -
- Plan B-inspelning och genrep förutsätter att livemiljön fungerar. Verifiera den först.
- Kvalplanens slutversion förutsätter PL-mötet (tisdag).

## Risker och beroenden (F3)

| Risk / beroende                  | Sannolikhet | Påverkan | Vad gör vi åt det                                        |
| -------------------------------- | ----------- | -------- | -------------------------------------------------------- |
| Livemiljön strular på demodagen  | Medel       | Hög      | Plan B-inspelning klar i god tid, lokal miljö som reserv |
| Kvalplan krockar med testarbetet | Låg         | Medel    | Kvalplanen är liten, egen ägare, klar till torsdag       |
|                                  |             |          |                                                          |
## Att bevaka
- [ ] Kvalplan inlämnad i Canvas senast torsdag 15/10 kl 17 
- [ ] R-04 och R-02 gröna (de två som inte får bli luckor) 
- [ ] Plan B-inspelning klar i god tid, inte kvällen före 
- [ ] Livemiljön verifierad nåbar
- [ ] Demon genrepad inom 10 min, roller fördelade 
- [ ] Alla tasks har ägare + estimat innan Ready