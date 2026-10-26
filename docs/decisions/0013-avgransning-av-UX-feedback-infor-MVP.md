# ADR 0013 — Avgränsning av UX-feedback inför MVP

- **Status:** Föreslagen
- **Datum:** 2026-10-15
- **Deltagare:** Pontus (drivande)
- **Berör:** UX / MVP / Betalningar och attestflöde
- **Relaterade issues:** Ej specificerade

## Kontext

Under genomgången av UX-feedback identifierades flera funktioner och förbättringar kopplade till attestflödet, betalningar och notifieringar.

Med begränsad utvecklingstid behöver vi prioritera de funktioner som är nödvändiga för MVP och avgränsa sådant som innebär ytterligare komplexitet eller utökad funktionalitet.

De identifierade områdena är:
- Visning av atteststatus, exempelvis "Väntar attest 1 av 2" eller "1 av 1".
- Förfallodatum på betalningar.
- E-postnotifieringar.

## Beslut

Vi väljer att avgränsa MVP enligt följande:

1. **Attestflöde:** Vi implementerar inte stöd för flera attestanter eller visning av exempelvis "Väntar attest 1 av 2". Stöd för två attestanter betraktas som en utökning utanför MVP.
2. **Förfallodatum:** Vi inkluderar inte förfallodatum på betalningar i MVP på grund av tidsbegränsningar.
3. **E-postnotifieringar:** Vi inkluderar inte e-postnotifieringar i MVP på grund av tidsbegränsningar.

## Alternativ vi övervägde

- **Alternativ A – Implementera samtliga identifierade UX-förbättringar:** Valdes bort eftersom det skulle utöka omfattningen och riskera att försena MVP.
- **Alternativ B – Avgränsa funktionaliteten till MVP:s grundbehov:** Föreslaget alternativ. Vi prioriterar ett enklare attestflöde och skjuter upp förfallodatum och e-postnotifieringar.

## Konsekvenser

**Positiva**
- Minskad utvecklingstid och komplexitet.
- Tydligare avgränsning av MVP.
- Större fokus på kärnfunktionalitet.
- Lägre risk för förseningar.

**Negativa / risker**
- Begränsat stöd för mer komplexa attestflöden → Kan utvärderas efter MVP.
- Betalningar saknar förfallodatum → Kan påverka användarens möjlighet att prioritera betalningar.
- Användare får inga e-postnotifieringar → Kräver att användaren själv kontrollerar status i systemet.

## Uppföljning

- Utvärdera UX-feedback efter MVP och identifiera vilka avgränsade funktioner som bör prioriteras.
- Följa upp om avsaknaden av flera attestanter, förfallodatum eller e-postnotifieringar skapar problem för användarna.
- Omvärdera beslutet vid planering av nästa utvecklingsfas utifrån användarbehov, feedback och tillgänglig utvecklingstid.