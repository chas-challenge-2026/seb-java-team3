# Kravtolkning: från kundfilm till backlog

Det här dokumentet spårar SEB-kundens uttalade behov (från kundfilmen med Johan Claesson,
cash management) till våra stories, tekniska garantier (BUG-fixar), beslut (ADR) och
avgränsningar. Syftet är att visa att prioriteringen är en tolkning av kunden och inte
en gissning, och att våra avgränsningar är medvetna och motiverade.


## Spårbarhet: behov till lösning

| # | Kundens behov (ur filmen) | Vår tolkning | Var i backloggen | MVP? |
|---|---------------------------|--------------|------------------|------|
| 1 | Betalningar godkänns idag via mail/lapp, av fel person, i fel ordning. "Rätt person i rätt ordning." | Approval-kedja som triggas av tröskel, ordnade steg, genomförs inte förrän rätt attestant godkänt. | Epic Betalning/godkännandekedja: skapa betalning, tröskel ger kedja (#83, BUG-006), godkänn. | Ja |
| 2 | "En eller flera personer... större belopp, attestansvarig och kanske VD." | Kedjan byggs som en ordnad lista av steg. MVP fyller ett steg. VD blir attestant i andra steget, inte egen roll. | ADR 0004. Story "två attestanter (VD)". | Delvis: kärna nu, VD-steg är breddning |
| 3 | Den som ska godkänna får ingen signal, håller reda själv, mail sväljs tyst. | Badge som räknar inloggad attestants väntande steg och gör mailen onödiga. | Epic Notifiering: badge (BUG-007, ADR 0008). | Ja (e-post är breddning) |
| 4 | Fel kontonummer ger betalning i retur. "Kontrolleras redan när de matas in." | Validera IBAN vid inmatning: MOD97-kontrollsiffra (ISO 13616), inte bara format. BIC (ISO 9362). Delad kod. | Epic Kontonummerkontroll: IBAN-validering (BUG-003). | Ja (native C-modul är breddning) |
| 5 | "Ladda upp en hel fil... lönekörning i stället för rad för rad." Filer 10-500 rader. | Filuppladdning med RFC 4180-parsning, allt-eller-inget-insert, filstorleksgräns. | Epic Batch/CSV (BUG-004/005/012). | Nej, efter MVP (se avvikelse A) |
| 6 | "Visa vem som godkände vad och när... svart på vitt... från ax till limpa." | Kronologisk vy per betalning (aktör, händelse, tid, ordning) plus logg av alla beslutshändelser, skrivna atomärt. | Epic Audit/spårbarhet (BUG-008). Sluttentans-kriteriet. | Ja |
| 7 | "Reglerat område... känsliga uppgifter... säkra och motståndskraftiga system." | Säker inloggning (ingen SQLi, BCrypt), rollstyrning, egna atteststeg, tenant-isolering. | Epic Auth/behörighet (BUG-001, 002, 011), tenant-filtrerad audit. | Ja (2FA, refresh-rotation är breddning) |
| 8 | "Flera personer kan arbeta med det här utan att det blir fel." | Samtidighetsskydd: atomär saldo-dragning, ingen dubbelgodkänning. | Godkänn-story: `@Transactional` (BUG-009), `@Version`. | Ja |
| 9 | "Motståndskraftiga när det blir fel." | Atomicitet: betalning, saldo och audit lyckas eller rullas tillbaka tillsammans. Batch allt-eller-inget. | BUG-005/008/009. | Ja i kärnan (batch-delen är breddning) |
| 10 | "Inte att det ska gå jättefort, det viktigaste är att den är pålitlig." Kvalitet viktigast. | Prioriteringsprincip: en stabil kärna slår fem halvfärdiga flöden. | `mvp.md`, ADR 0003, DoD i `ways-of-working.md`. | Styr allt |
| 11 | "Inte uppfinna från noll, modernisera befintligt." Teknisk skuld. | Bygger v2 ovanpå befintligt v1. BUG-referenserna är den tekniska skuld som rättas. | Alla BUG-001 till 012 i backloggen. | - |
| 12 | (Filmen nämner inte valuta.) | Allt hanteras i SEK. | `mvp.md`, utanför scope. | Utanför scope |

## A. Medvetna avvikelser mot källan (och varför)

Här avviker vår prioritering från något kunden uttryckligen nämnde. Det är tillåtet, men
det ska vara ett val och inte en glömska.

**Batch/CSV skjuts till efter MVP.** Kunden tar upp filuppladdning som en av två saker
kunderna "återkommer till gång på gång", och nämner den i vad som utmärker en bra
leverans ("klarar av att ta emot en stor fil"). Vi har ändå lagt den utanför MVP.
Motivering: kunden rangordnar själv korrekt godkännande, spårbarhet och pålitlighet
högst ("det får absolut inte gå fel", "det viktigaste är att den är pålitlig"). En stabil
kärna före ett halvfärdigt filflöde är därför en trogen läsning av hans egen prioritering
(ADR 0003). Vi behöll däremot IBAN-valideringen i MVP, som också är en namngiven smärta,
eftersom den är billig och central för att fånga fel tidigt.
Risk: kunden kan efterfråga filuppladdning i demon. Åtgärd: ha motiveringen redo, och
överväg en enkel (icke-native) CSV-import till slutleveransen så att ett uttalat kriterium
inte helt saknas. Native prestanda-parsern förblir breddning.

**Övrig breddning** (byggs när kärnan står, se `mvp.md`): dubbel attest och VD-steg med
tröskeltrappa, påminnelser via e-post (kö, retry), native C/C++-moduler, manipuleringssäker
logg (HMAC), compliance-export, refresh-token-rotation, 2FA. Alla är motiverade av kunden
men underordnade kärnans "det får inte gå fel".

## B. Öppna tolkningsfrågor att bekräfta med kund/PL

Filmen ger inget entydigt svar på dessa. Vi antar ett rimligt default och bekräftar,
hellre det än att bygga på en tyst gissning.

**Ser en initiator historik?** Filmen nämner bara spårbarhet ur bankens och attestantens
perspektiv, aldrig initiatorns. Default: initiator ser sina egna betalningars status,
men inte audit-loggen och inte andras betalningar. Spärras i backend (403), inte bara i UI.
Bekräftas med PL.

**Tröskel: strikt `>` eller `>=`?** Vad som händer på exakt tröskelbeloppet avgör om en
betalning kan slinka igenom oattesterad. Kräver ADR och gränsfall-test.

**Tröskeltrappa:** "en eller flera" godkännare, vilken beloppsnivå kräver hur många
attestanter (attestant 1 plus VD)? Bekräftas innan breddningen byggs.

## Hur detta används

- Vid demo och slutpresentation: visar spårbarheten från kundens ord till levererad funktion.
- Vid omprioritering (CTO v7, UX/DM v8): avvikelserna i avsnitt A är utgångspunkten.
- Uppdateras när en öppen fråga i avsnitt B får svar (skriv svaret och var det beslutades).
