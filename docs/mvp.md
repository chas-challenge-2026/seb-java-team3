# MVP - SEB Företagsbetalningar v2

Det här dokumentet definierar vad som ingår i MVP, vad som medvetet skjuts upp, och varför. Beslutet bakom prioriteringen ligger i ADR 0003; rollmodellen i ADR 0004.

## Kundproblemet

Betalningar godkänns idag via mejl, av fel person, i fel ordning, utan spårbarhet, och fel kontonummer upptäcks först när pengarna kommer i retur. Kunden vill ha ett system där rätt person godkänner rätt betalning i rätt ordning, och där det går att visa svart på vitt vad som hänt.

## MVP-flödet

Den tunna tråden genom hela systemet som ska stå stabil till slutet av v5:

> Logga in -> skapa betalning över tröskeln -> approval-kedjan triggas -> attestant får badge -> godkänner -> betalning utförd + saldo dras atomärt -> varje steg loggas och hela kedjan kan följas per betalning vem, vad, när.

Det MVP ska bevisa:en användare kan logga in säkert, en betalning kan skapas, godkännas av rätt person, genomföras korrekt, och följas i efterhand.

## Roller

Tre inloggningsroller: initiator, attestant, admin (finns redan i systemet). VD modelleras som en attestant i ett andra kedjesteg, inte som en egen roll. Se ADR 0004.

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

Inte oviktigt, utan för att en stabil kärna slår fem halvfärdiga flöden. Byggs efter MVP:

- Avvisa betalning med kommentar
- Påminnelser via e-post (kö, retry)
- Dubbel attest / attestant 2 (VD) och tröskeltrappa
- Batch/CSV-uppladdning
- Native C/C++-moduler (CSV-parser, IBAN/BIC, audit-signering)
- Kontoöversikt/dashboard som egen vy (v1 har den redan, låg prioritet)
- Compliance-export, refresh-token-rotation, 2FA, valutaomvandling

## Utanför scope helt

- Valutaomvandling (allt hanteras i SEK)

## Så vet vi att MVP är klart

Hela MVP-flödet går att demonstrera live, end-to-end, och motsvarande rader i `test-status.md` är gröna.
