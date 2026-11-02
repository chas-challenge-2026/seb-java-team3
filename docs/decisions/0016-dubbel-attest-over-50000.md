# ADR 0016 — Dubbel attest över 50 000 kr

- **Status:** Accepterad
- **Datum:** 2026-11-02
- **Deltagare:** Team-seb-3 drivande: Pontus Ingenius
- **Berör:** Betalningar och attestflöde
- **Relaterade issues:** R-03, R-05
- **Relaterat:** [ADR 0003](0003-mvp-scope-och-avgransningar.md), [ADR 0013](0013-avgransning-av-UX-feedback-infor-MVP.md), [ADR 0015](0015-strikt-storre-an-for-attesttroskeln.md), `docs/backlog.md`, `docs/accessmatris.md`

## Kontext

ADR 0003 och ADR 0013 sköt upp dubbel attest till efter MVP, så även mycket stora belopp hade bara ett atteststeg. Backloggen formulerar behovet så här: riktigt stora belopp ska kräva två attestanter, "så att ingen enskild person kan släppa igenom dem".

Datamodellen och godkännandet stödde redan flera steg, men fyra saker saknades för att regeln skulle hålla:

- En admin får besluta om alla företagets steg. Stegen sparade inte vem som beslutade, så en admin hade kunnat godkänna båda stegen ensam.
- Attestkorgen visade alla väntande steg. Attestant 2 hade sett ett steg som inte gick att godkänna.
- Bara det sista godkännandet loggades, så det första hade inte synts i audit.
- Tilldelningen tog den attestant som databasen råkade ge först, utan sortering.

## Beslut

- En betalning strikt över `payment.approval.two-attestant-threshold` (50 000 kr) får två atteststeg med olika attestanter. Exakt 50 000 kr räcker med en attestant, enligt samma regel som i ADR 0015.
- Stegen går i tur och ordning. Steg 2 syns inte i attestkorgen eller i badgen, och går inte att godkänna eller avvisa, förrän steg 1 är godkänt.
- Betalningen genomförs och saldot dras först när båda stegen är godkända. Reservationen av pengarna gäller hela vägen. Om något av stegen avvisas stoppas hela betalningen.
- De två måste vara oberoende av varandra: den som har godkänt ett av stegen eller skapat betalningen får inte godkänna fler steg. Det ger 403. Kontrollen använder den nya kolumnen `approval_steps.decided_by`, som sparar den som faktiskt beslutade.
- Ett företag med färre än två attestanter kan inte skapa betalningar över tröskeln (409). Admin räknas inte som attestant vid tilldelningen.
- Varje godkännande loggas. Det som inte genomför betalningen får händelsen `APPROVE_STEP`, och det sista får `APPROVE_PAYMENT` som tidigare.
- Granskningsrutan visar vem som redan har godkänt, så att nästa attestant vet vem som granskat betalningen före. Historiken är fortsatt gemensam för företagets attestanter.
- Tilldelningen tar de första attestanterna i id-ordning, samma MVP-regel som tidigare men nu förutsägbar.

## Alternativ vi övervägde

- **Två attestanter i valfri ordning** - enklare för attestanterna, eftersom ingen behöver vänta. Valdes bort eftersom backloggen kräver rätt attestant i rätt ordning (R-03) och godkännandet redan bygger på ordnade steg.
- **Räkna bort bara dubbletter, inte skaparen** - en admin som skapat betalningen hade kunnat vara en av de två. Valdes bort, eftersom bara en person då granskar betalningen utöver den som vill få iväg den.
- **Lägga till steg 2 på betalningar som redan väntar** - valdes bort, eftersom godkännanden som redan har gjorts och pågående flöden hade ändrats i efterhand. Regeln gäller när betalningen skapas.

## Konsekvenser

**Positiva**
- En ensam person kan inte släppa igenom ett belopp över 50 000 kr, inte heller en admin.
- Hela kedjan går att följa i audit, och den som faktiskt beslutade står på steget.
- Attestkorgen och badgen visar bara det som användaren kan göra något åt.

**Negativa / risker**
- Betalningar som väntar på attest när ändringen driftsätts behåller sitt enda steg, även över 50 000 kr. → Accepterat. Gå igenom väntande betalningar före driftsättning om det inte är godtagbart.
- Ett företag måste ha minst två attestanter för betalningar över tröskeln, och det finns ännu ingen backend för att skapa användare. → Seed-företaget får en andra attestant i V13. Andra företag behöver läggas upp i databasen tills användaradministrationen är byggd.
- Steg 1 och 2 går alltid till samma två attestanter. Är attestant 2 borta fastnar betalningen, men pengarna förblir reserverade. → En admin kan besluta om steget. Riktig urvalslogik är fortfarande avgränsad.
- En admin som har skapat en betalning med ett steg kan fortfarande godkänna den själv (R-03). → Utanför det här beslutet, se `docs/accessmatris.md`.

## Uppföljning

- Teamet behöver acceptera beslutet, och kunden bekräfta tröskelvärdet 50 000 kr.
- Ta ställning till om R-03 ska stängas helt, alltså om skaparen ska nekas även vid ett steg.
- Ta ställning till om ett särskilt VD-steg behövs (`docs/kravtolkning.md`).
