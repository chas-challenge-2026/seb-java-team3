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

Sprintplanering sker på måndag. Där vi sätter sprintmål för veckan, fixar kanban.
Tisdag:


## Beslut → beslutslogg

Tar vi ett vägval av betydelse (ramverk, arkitektur, avgränsning, tröskelvärde)? Skriv en kort ADR i `docs/decisions/`.

## Riskhantering

Vi kör riskanalysen enligt RMR-arbetsboken: Identify → Assess → Prioritise → Mitigate → Monitor. Poäng = Sannolikhet × Konsekvens (1–25). Svar väljs bland de fyra T:na: Treat, Tolerate, Transfer, Terminate.

Vi markerar inte en risk som åtgärdad för att vi tror att den är löst. Vi ska kunna peka på koden och ett test som visar det.

- Innan vi ändrar status på en risk som går att kontrollera i kod tittar vi på koden som faktiskt finns.
- Om åtgärden innebär ett vägval, till exempel kring arkitektur, tröskelvärden eller tokenlagring, dokumenterar vi beslutet i `docs/decisions/`. Det gäller också när vi ändrar ett tidigare beslut.
- Om en nyare lösning redan täcker samma funktion tar vi bort den sårbara gamla koden i stället för att laga den.
- Vi uppdaterar status i riskregistret (`Open`, `In progress`, `Mitigated`, `Accepted`) när arbetet går framåt, inte bara vid uppstarten.
- Varje bugg behöver inte en egen rad i riskregistret. Det behövs först när buggen kan få allvarliga följder för systemet och vi måste ta ställning till om risken ska åtgärdas, accepteras eller flyttas. Annars hör buggen hemma i backloggen.


### Den här veckan (v6)
- Gick igenom samtliga kod-verifierbara risker mot faktisk kod, fil för fil, i stället för att lita på registrets tidigare status.
- Skrev nya tester som bevisar mitigeringen i stället för att bara påstå den: `ApprovalApiControllerOwnershipTest` (ägarskap på atteststeg), gränsfallstester på betalningströskeln plus en utförandegrind (`ApprovalServiceTest`), `AuthServiceInjectionTest` mot en riktig SQL-motor (inloggning).
- Tog bort gammal, sårbar v1-kod (`PaymentController`, `BatchController`) i stället för att patcha den, eftersom v2 redan hade ett motsvarande, säkrare flöde.
- Låste tröskelbeslutet (strikt `>`, inte `>=`) i både kod och tester, i stället för att låta det vara underförstått.
- Skrev en ny ADR när vi upptäckte att koden avvikit från ett tidigare beslut (byte från HttpOnly-cookie till JWT i `localStorage`) utan att avvikelsen var dokumenterad, och lade in den som en egen, medvetet accepterad risk i registret.
- Valde vilka risker som skulle lyftas i CTO-filmen utifrån två kriterier tillsammans: allvarlighetsgrad OCH att åtgärden går att bevisa med ett körbart test, inte bara beskriva i ord.
