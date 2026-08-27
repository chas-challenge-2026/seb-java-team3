# ADR 0003 — MVP-scope och avgränsningar (djup före bredd)

- **Status:** Accepterad
- **Datum:** 2026-08-26
- **Deltagare:** Hela teamet · drivande: Team
- **Berör:** Hela leveransen / prioritering
- **Relaterade issues:** #

## Kontext

Caset innehåller många features (multi-account-dashboard, batch-CSV, dubbel attest, compliance-export, native-moduler, 2FA m.m.). Vi kan inte bygga allt stabilt på tolv veckor. Kunden väger kvalitet före snabbhet och tävlingen bedömer kundvärde: en sak som funkar hela vägen slår fem som funkar halvvägs.

## Beslut

Vi bygger kärnflödet end-to-end först (MVP, mål slutet av v5) och breddar först därefter. MVP-flödet:

> Logga in -> skapa betalning över tröskeln -> approval-kedjan triggas -> attestant får badge -> godkänner -> betalning utförd + saldo dras atomärt -> varje steg loggas och hela kedjan kan följas per betalning vem, vad, när.

Följande skjuts medvetet till efter MVP och dokumenteras som avgränsning: 
- batch-CSV-upload 
- dubbel attest / attestant 2 
- admin-vyer 
- multi-account-dashboard i full bredd 
- compliance-export 
- native audit-signering och native CSV (byggs fristående parallellt, integreras när kärnan står)
- refresh-token-rotation, 2FA, valutaomvandling.

## Alternativ vi övervägde

- **Bredd-först:** 
Många features parallellt: valdes bort pga hög risk för många halvfärdiga flöden och instabil demo.
- **Djup-först:** Kärnflödet stabilt, sedan breddning: valdes pga ger en demonstrerbar kärna tidigt och matchar kundens kvalitetskrav.

## Konsekvenser

**Positiva**
- Något demonstrerbart och stabilt tidigt -> trygghet inför kvaldemo.
- Tydliga avgränsningar är i sig betygsgrundande (kravtolkning, avgränsning).

**Negativa / risker**
- Native-modulen får inte glömmas bort trots att integration är "sen" -> Åtgärd: byggs testbar fristående parallellt

## Uppföljning

- Checkpoint slutet av v5: går hela MVP-flödet att demonstrera stabilt? Om nej — skär mer, bredda inte.
