# Sprint v8

**Datum:** 2026-10-05  
**Backlog-ägare:** team-seb-3
**Närvarande:** Pontus, Jonathan, Mohammed.H, Adnan, Samuel, Marcus 

## Sprintmål

**Huvudmål:**  
Hela MVP-flödet ska kunna visas live från början till slut och vara verifierat med relevanta tester.

De viktigaste delarna att få på plats denna sprint är:

- Tenant-isolering (R-04)
- Atomicitet mot riktig databas (R-02)
- Gammal v1-kod ska vara borttagen
- E2E-flödet ska kunna köras och verifieras

**Sekundärt mål:**  
Vi tar in de 2–3 viktigaste punkterna från CTO/DM/UX-feedbacken. Övriga punkter dokumenteras och prioriteras vid ett senare tillfälle.

Vilka punkter som tas med bestäms efter tisdagens PL-möte.

## Fokus denna sprint

Sprint v8 fokuserar framför allt på att färdigställa och verifiera det som redan finns.

Prioriteringen är därför:
1. Lösa beroenden som blockerar tester
2. Verifiera de viktigaste riskerna
3. Säkerställa att MVP-flödet fungerar end-to-end
4. Få dokumentation och konfiguration i rätt skick inför demo och examination

Sprint v9 är tänkt att fokusera på stabilisering och eventuella sista justeringar.

## Viktigt beroende

**Konto-API behöver färdigställas.**
Det behövs ett API för att hämta konton. Det används av E2E-flödet och behövs även för delar av den övriga testningen.

Det är därför en av sprintens tidiga prioriteringar.

**Ägare:** Adnan Zasella

## Sprint backlog

### Mindre uppgifter som kan göras tidigt

| Uppgift                                                                                  | Ägare  | Est. | Kommentar                                      |
| ---------------------------------------------------------------------------------------- | ------ | ---- | ---------------------------------------------- |
| Checka in det aktuella riskregistret i repot                                             | Pontus | S    | Säkerställ att det är enkelt att hitta         |
| Uppdatera README för v2: hur man kör, testar, loggar in och vilka kända luckor som finns | Samuel | S    | Gör projektet enklare att förstå och köra      |
| Uppdatera teststatus: E2E ska stå som **Delvis** tills hela flödet är verifierat (p.12)  | Pontus | S    | Statusen ska spegla vad som faktiskt är testat |

### Prioriterade uppgifter denna sprint

| Uppgift                                                                                                            | Lager       | Ägare       | Est. | Kommentar                                                |
| ------------------------------------------------------------------------------------------------------------------ | ----------- | ----------- | ---- | -------------------------------------------------------- |
| Konto-API: endpoint för att hämta konton                                                                           | API/Service | Adnan       | M    | Behövs för E2E och relaterade tester                     |
| R-04: negativt cross-tenant-test. Tenant A ska inte kunna se tenant B audit, tidslinje, betalningar eller attester | Test        | Markus      | M    | Viktigt säkerhetstest                                    |
| R-02: testa atomicitet mot riktig databas med Testcontainers                                                       | Test/DB     | Mohammed. H | M    | Verifieras mot riktig databas istället för enbart mockar |
| Kör E2E-testet när konto-API finns                                                                                 | Test        | Pontus      | S    | Beroende av konto-API                                    |
| Testa ordningen i tidslinjen, inklusive fall med samma tidsstämpel                                                 | Test        | ____        | S    |                                                          |
| Badge: `Badge.test.tsx` + `/api/approvals/count` + `RoleAuthorizationTest`                                         | FE/Test     | Pontus      | S    |                                                          |
| Skyddade routes: `requireAuth` + `requireRole`                                                                     | FE/Test     | Pontus      | S    |                                                          |
| BCrypt: neka MD5-hash och verifiera att gamla MD5-hashar inte finns kvar                                           | Test        | Samuel     | S    |                                                          |
| Lägg till CodeQL-workflow enligt ADR 0004                                                                          | Build       | ____        | S    |                                                          |
| Dashboard frontend. Lägg till översikt                                                                             | FE          | Marcus      | L    |                                                          |

### Kontroll av gammal v1-kod

Om detta inte redan är färdigt:

| Uppgift                                                                                                        | Ägare | Est. |
| -------------------------------------------------------------------------------------------------------------- | ----- | ---- |
| Kontrollera att `DashboardController`, Thymeleaf och `seb123` är borttagna och att grep inte hittar något kvar | ____  | S    |

## Risker och beroenden (F3)

|Risk / beroende|Sannolikhet|Påverkan|Åtgärd|
|---|---|---|---|
|Konto-API behövs för E2E och flera tester|Hög|Hög|Prioriteras tidigt i sprinten|
|Testcontainers kräver viss setup för R-02|Medel|Medel|Sätt upp testmiljön tidigt|
|Tester kan ta längre tid än planerat|Medel|Medel|Prioritera de tester som verifierar de viktigaste riskerna först|
|För många uppgifter prioriteras samtidigt|Medel|Medel|Håll fokus på MVP och de viktigaste verifieringarna|
|Feedback efter PL-mötet kan påverka sekundära prioriteringar|Medel|Låg|Huvudmålet ligger fast, sekundära punkter bestäms efter mötet|

## Beslut under sprintplaneringen

### Efter PL-mötet

De 2–3 viktigaste punkterna från DM/UX-feedbacken väljs ut och läggs till i sprintens sekundära mål.

## Att hålla koll på under veckan

- [ ] Konto-API klart
- [ ] E2E-flödet går att köra och verifiera
- [ ] R-04 och R-02 är verifierade
- [ ] `test-status.md` visar aktuell och korrekt status
- [ ] Riskregistret är incheckat i repot
- [ ] Fredagens F8-dialoger är inbokade
- [ ] Sekundärmål och prioriterad feedback fylls i efter PL-mötet
- [ ] Alla tasks går igenom DOR innan Ready