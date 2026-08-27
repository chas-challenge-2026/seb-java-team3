# Så jobbar vi — arbetssätt
1. Förstå kundens behov
2. Definiera MVP + avgränsningar
3. Bryt ner MVP i backlog: Epics → User Stories → Tasks
4. Sätt arbetssättet: tavla, DoR, DoD, roller, rytm
5. Bygg kärnflödet iterativt (v3–v5, MVP klar 18 sep)
6. Bearbeta feedforward → omprioritera (CTO v7, UX/DM v8)
7. Stabilisera → kvaldemo → slutleverans (v9–v12)
## ScrumBan i korthet

Vi kombinerar Scrums veckorytm, tydliga sprintmål med Kanban. Det passar kursens veckovisa PL-checkpoints och ger oss en flexibel tavla i stället för hårda sprint-åtaganden.
## Nedbrytning av stories
Nedbrytning av stories: Vi bryter ner varje story till tasks med hjälp av olika lager. Vi bryter bara ner stories som är aktuella för den sprinten. 
- Frontend
- API 
- DB/Domän 
- Service 
- Test 

## Tavlan (GitHub Projects)

Från vänster till höger:

`User Story Parent -> Backlog -> Ready -> In Progress -> Review -> Done`
- Slutför före du börjar nytt. Fem halvfärdiga saker är värre än en klar.
- En sub-issue(task) flyttas till `Ready` först när det uppfyller DoR, och till `Done` först när det uppfyller DoD.

## Definition of Ready (DoR) — får vi börja?

Ett kort är redo att plockas när:
- [ ] Det har en tydlig beskrivning och acceptanskriterier
- [ ] Det är litet nog att bli klart inom några dagar
- [ ] Beroenden är kända (väntar det på något annat?)

## Definition of Done (DoD) — är det klart?

Ett kort är klart när:
- [ ] Koden är byggd och kör lokalt
- [ ] Testad (unit och/eller integration, relevant för uppgiften)
- [ ] Granskad av minst en teammedlem (PR-review)
- [ ] Dokumentation uppdaterad om relevant (README / beslutslogg / teststatus)
- [ ] Mergad till develop och grön i bygget

## Roller

- Alla = utvecklare. Bygger, testar, dokumenterar, presenterar.
- Team Lead (roterande):
- C/C++-ansvarig: äger native-modulen (byggbar, testbar, förklarbar, även utan full integration).
- Frontend/backend/fullstack:

## Veckorytm

Bestäms på måndag. 30/8


## Beslut → beslutslogg

Tar vi ett vägval av betydelse (ramverk, arkitektur, avgränsning, tröskelvärde)? Skriv en kort ADR i `docs/decisions/`.
