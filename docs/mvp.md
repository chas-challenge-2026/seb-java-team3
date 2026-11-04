# MVP - SEB Företagsbetalningar v2

Det här dokumentet definierar vad som ingår i MVP, vad som medvetet skjuts upp, och varför. Beslutet bakom prioriteringen ligger i ADR 0003; rollmodellen i ADR 0004 och ADR 0017. Ursprungligt MVP-scope och senare levererade utökningar hålls isär nedan.

## Kundproblemet

Betalningar godkänns idag via mejl, av fel person, i fel ordning, utan spårbarhet, och fel kontonummer upptäcks först när pengarna kommer i retur. Kunden vill ha ett system där rätt person godkänner rätt betalning i rätt ordning, och där det går att visa svart på vitt vad som hänt.

## MVP-flödet

Den tunna tråden genom hela systemet som ska stå stabil till slutet av v5:

> Logga in -> skapa betalning över tröskeln -> approval-kedjan triggas -> attestant får badge -> godkänner -> betalning utförd + saldo dras atomärt -> varje steg loggas och hela kedjan kan följas per betalning vem, vad, när.

Det MVP ska bevisa: en användare kan logga in säkert, en betalning kan skapas, godkännas av rätt person, genomföras korrekt, och följas i efterhand.

## Roller

Tre inloggningsroller: initiator, attestant, admin. Initiator skapar betalningar, attestant beslutar om sina tilldelade steg och admin hanterar användare samt läser företagets översikter och historik. Admin skapar eller attesterar inga betalningar (ADR 0017). VD är ingen egen roll; dubbel attest använder två olika attestanter (ADR 0016). Se även [accessmatrisen](accessmatris.md).

## Vad som ingår i MVP

Kärnan, byggd i tre sprintar (v3-v5):

- **Säker inloggning:** Riktig autentisering, BCrypt, jwt, ingen SQL-injektion
- **Roller & behörighet:** Rätt roll kommer åt rätt sak och har rätt behörighet.
- **Skapa betalning:** Med tröskel som triggar en attestkedja (byggd som lista av steg)
- **Godkänn betalning:** Genomförs och saldo dras atomärt, inga dubbelgodkännanden
- **IBAN-validering:** MOD97, fångar fel kontonummer vid inmatning
- **Notifiering (badge):** Attestanten ser vad som behöver attesteras
- **Audit:** Alla beslutshändelser loggas, och hela betalnings kedjan kan följas i en logg.

## Avgränsningar

Följande låg utanför ursprungligt MVP. Flera delar har sedan byggts; listan anger nuläget:

- Avvisa betalning med kommentar (byggt efter MVP i #226 och #228: avvisningen tar emot ett valfritt meddelande, som sparas atomärt med beslutet och visas i Historik; godkännande tar inget meddelande)
- Påminnelser via e-post (kö, retry)
- Dubbel attest / attestant 2 (VD) och tröskeltrappa (dubbel attest över 50 000 kr byggt efter MVP, se ADR 0016; ett särskilt VD-steg och fler trösklar är inte byggda)
- Batch/CSV-uppladdning (byggd efter MVP i #255: en CSV-fil med högst 500 betalningar importeras allt-eller-inget via `POST /api/payments/import`)
- Native C-moduler: IBAN-validering, audit-signering och CSV-parsern är byggda och kopplade till backend
- Kontoöversikt/dashboard som egen vy (byggd med konton från API:t och rollanpassning; admin har en skrivskyddad attestöversikt)
- Användaradministration (byggd i backend och frontend: lista, skapa, byta roll och ta bort användare utan historik; admin får inte tilldelas via API:t)
- Compliance-export, 2FA, valutaomvandling

Refresh-token-rotation låg tidigare på den här listan men är nu byggd (ADR 0012).

## Utanför scope helt

- Valutaomvandling (allt hanteras i SEK)

## Så vet vi att MVP är klart

Hela MVP-flödet går att demonstrera live, end-to-end, och motsvarande rader i `test-status.md` är gröna.

MVP-testets hantering av kvittodialogen är rättad och hela E2E-sviten med nio tester passerade mot en isolerad Docker-stack 2026-11-03. Sviten omfattar även dubbel attest och admins gränssnitt. Se [teststatus](test-status.md) för övrig verifiering och [kända brister och avgränsningar](kanda-brister-och-avgransningar.md) för kvarstående produktbegränsningar.
