# AI-logg

Här dokumenterar vi var AI påverkat beslut, kod eller analys. 

## Så fyller du i

- Ett block per gång AI påverkat något som spelar roll (kod som mergats, ett designbeslut, en analys). Trivial autocomplete behöver inte loggas.
- **Skriv i förstaperson och namnge dig**bedömningen är individuell, så det måste synas vem som granskat.
- Fältet **"Hur jag granskade/ändrade"** är det viktigaste. Där visar du din egen förståelse. "Kopierade rakt av" gäller inte.
- Länka till commit/PR/issue så det går att härleda.
- **Nyast överst**klistra in nya block direkt under `## Logg`, så slipper du scrolla.

## Mall — kopiera och fyll i

Kopiera raderna mellan strecken, klistra in högst upp i loggen, fyll i. Radera fält som inte är relevanta.

---
### ÅÅÅÅ-MM-DD [kort vad] [ditt namn]
- **Verktyg:**
- **Använde AI till:**
- **Genererades:**
- **Hur jag granskade/ändrade:**
- **Valde bort (om något):**
- **Spår:** PR #, issue #
---

## Logg

<!-- Nyast överst: klistra in ditt block direkt under den här raden. -->

### 2026-09-03 — Flyway-migration + Java 21/Spring Boot 3.3.4-uppgradering (#46) AdnanZasella
- **Verktyg:** Claude
- **Använde AI till:** Vägledning genom hela #46 – förstå Flyway-koncept, planera uppdelning av migrationsfiler (V1–V5), granska säkerhetsvarningar (CVE:er) som IntelliJ flaggade i pom.xml, felsöka kompileringsfel och runtime-fel vid uppgradering till Java 21/Spring Boot 3.3.4, formulera commit-meddelande och PR-beskrivning.
- **Genererades:** Innehåll i V1–V5 migrationsfilerna (baserat direkt på befintlig seed.sql, ingen ny datamodell), förslag på Dockerfile-ändringar (JDK-version i båda build-stegen), förklaring av varje CVE och bedömning av om den var relevant för projektets scope.
- **Hur jag granskade/ändrade:** Jämförde varje migrationsfils SQL manuellt mot ursprungliga seed.sql för att säkerställa att inget ändrades i strukturen. Verifierade praktiskt att lösningen fungerade genom docker-compose up --build och läste igenom hela Flyway-loggen för att bekräfta att alla 5 migrationer applicerades korrekt och i rätt ordning. Förstod och kunde själv motivera varför javax→jakarta-bytet behövdes (Spring Boot 3.x namespace-byte) innan jag gjorde ändringen i de 6 controller-filerna. Tog själv beslutet att göra en samlad commit istället för flera, efter att ha förstått att mellanliggande tillstånd inte skulle kompilera.
- **Valde bort:** Föreslagna CHECK-constraints på payments.status/amount i migrationen – bedömde att valideringslogik hör hemma i applikationslagret (#43), inte i databasschemat, för att hålla #46 avgränsad till sitt syfte. Föreslagen omskrivning av audit_entries.user_id till att inkludera FK mot users – valde att behålla utan FK för att inte riskera att audit-loggning någonsin blockeras.
- **Spår:** PR #46 · issue #46

### 2026-08-27 Mall för Backlog Pontus.I
- **Verktyg:** Claude
- **Använde AI till:** Att snygga till våran backlog.md struktur och stavfel
- **Genererades:**
- **Hur jag granskade/ändrade:** Granskning av storys så att inget ändrades ifrån orginal tankar
- **Valde bort (om något):**
- **Spår:** PR #, issue #

### 2026-09-08 — Testvektorer för MOD97-sviten  [namn] (C-1)
- **Verktyg:** Claude
- **Använde AI till:** Ta fram testvektorer för MOD97-sviten.
- **Genererades:** 50 IBAN-nummer med förväntat giltigt/ogiltigt-resultat.
- **Hur jag granskade/ändrade:** Verifierade 10 st manuellt mot officiell MOD97-beräkning, hittade och rättade 2 felaktiga förväntningar, la till 5 svenska IBAN som saknades.
- **Valde bort:** 
- **Spår:** PR #42 · issue #16

## Tips

- Loggar du löpande  t.ex. samtidigt som du skriver PR-beskrivningen blir det aldrig ett berg att beta av i v12.
- Föreslog AI något du **valde att inte** använda? Skriv ett block om det ändå. Att avstå med motivering visar omdöme, och det är precis den självständighet examinatorn letar efter.
- Vill någon se alla sina egna poster? Sök på ditt namn (Ctrl+F) — därför står namnet i varje rubrik.
