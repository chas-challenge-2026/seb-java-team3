# UX- och DM-feedforward: prioritering och avgränsningar

Vi fick feedforward från UX och DM på betalnings- och attestflödet. Här står vad vi har prioriterat och genomfört, vad vi har avgränsat bort och varför.

Läget är kontrollerat mot koden på `develop` (commit `858a3c3`) 2026-11-04. En tidigare avgränsning av delar av samma feedback finns i [ADR 0013](../decisions/0013-avgransning-av-UX-feedback-infor-MVP.md).

## Så har vi prioriterat

Vi har vägt varje punkt mot fyra frågor:

1. **Kundvärde.** Kunden rangordnar själv korrekt godkännande, spårbarhet och pålitlighet högst ("det får absolut inte gå fel"), se [kravtolkning.md](../kravtolkning.md). Det som hjälper användaren att attestera rätt betalning och förstå vad som har hänt har gått först.
2. **Tid.** Slutleveransen är 2026-11-04. Punkter som kräver ny infrastruktur eller nya flöden har fått stå tillbaka för punkter som bygger på data vi redan har.
3. **Finns datan redan?** Mycket av feedbacken handlade om att visa information som backend redan hade, till exempel vem som skapade en betalning och vem som ska attestera. Sådant är billigt att bygga och ger mycket.
4. **Ny infrastruktur?** E-post, påminnelser och eskalering kräver e-posttjänst, kö, schemalagda jobb och nya regler. Det är ett eget bygge och inte en förbättring av det som finns.

## Prioriterat och genomfört

### Mina betalningar: vem väntar betalningen på?

- **Feedback:** Visa vem som ska attestera, så att man vet vem man ska påminna om en betalning fastnar. Visa "Väntar attest 1 av 2" eller "1 av 1".
- **Genomfört:**
  - Kolumnen **Attestant** visar den som ska attestera nu. För en avvisad betalning visas den som avvisade och för en genomförd de som godkände.
  - Vid dubbel attest står statusen som "Väntar attest 1 av 2" eller "2 av 2". Med ett enda atteststeg står bara "Väntar attest", eftersom "1 av 1" inte säger något mer.
  - En avvisad betalning kan fällas ut och visar då attestantens motivering.
  - Statusfilter: Alla, Väntar attest, Genomförda och Avvisade.
  - En badge i sidomenyn visar initiatören hur många betalningar som har ändrat status sedan hen senast tittade.
- **Varför prioriterat:** Kunden beskriver i dag att betalningar fastnar hos fel person utan att någon märker det. Att se vem man ska kontakta löser det utan ny infrastruktur, och datan fanns redan i backend.

### Attestera: mer information inför beslutet

- **Feedback:** Det saknas information om vem som skapade betalningen, vilket konto pengarna dras från, mottagarkontot och hur många attester som krävs.
- **Genomfört:**
  - Attestkorgen och granskningsrutan visar mottagarkonto, från vilket konto pengarna dras, saldo efter betalningen, belopp, datum, vem som skapade betalningen och referens.
  - Vid dubbel attest visas vilket steg det gäller ("Attest 1 av 2") och vem som har godkänt de tidigare stegen.
  - Efter ett godkännande i steg 1 får attestanten veta att betalningen genomförs först när nästa attest är klar.
  - En badge i sidomenyn visar attestanten hur många betalningar som väntar.
- **Varför prioriterat:** Attesten är kundens viktigaste kontroll. En attestant som inte ser varifrån pengarna tas eller vem som skapade betalningen kan inte göra en riktig kontroll, och då är attesten bara ett klick.

### Auditlogg: filter och automatiskt godkända betalningar

- **Feedback:** Filtrera på godkänt, avvisat och så vidare. Visa när en betalning godkändes automatiskt, i stället för bara "Betalning skapad".
- **Genomfört:**
  - Auditloggen har samma statusfilter som Mina betalningar.
  - En betalning under attestgränsen genomförs direkt när den skapas. Den händelsen visas nu som **"Automatiskt godkänd"**, eftersom skapandet då också är godkännandet.
- **Varför prioriterat:** Kunden vill se "vem som godkände vad och när, svart på vitt". En betalning som ser ut att sakna godkännande ger fel bild av spårbarheten, och ändringen var liten.

### Översikt: rätt innehåll per roll och länkar vidare

- **Feedback:** Var kritiska till vad som får ligga på översikten. Lägg till länkar till sidorna.
- **Genomfört:**
  - Översikten visar olika innehåll beroende på roll.
    - **Initiatör:** konton och senaste betalningar.
    - **Attestant:** konton och betalningar som väntar på attest.
    - **Admin:** samma attestkorg, skrivskyddad.
  - Varje del länkar vidare: till attestvyn, till Mina betalningar och till Ny betalning.
- **Varför prioriterat:** Översikten är det första användaren ser. Den ska visa det rollen behöver agera på och leda vidare dit, inte samla allt.

## Avgränsningar

Följande är inte byggt inom projektet. För varje punkt står varför.

### Betalningar som fastnar: påminnelse, eskalering och förfallodatum

- **Feedback:** Vad händer om en betalning blir liggande hos en attestant för länge? Påminnelse, eskalering, förfallodatum?
- **Läge:** En betalning väntar tills attestanten fattar beslut. Det finns ingen påminnelse, ingen eskalering och inget förfallodatum. Initiatören ser vem betalningen väntar på, och admin ser företagets alla väntande betalningar i attestöversikten.
- **Varför avgränsat:**
  - *Ny infrastruktur.* Påminnelser och eskalering kräver schemalagda jobb, regler för hur länge en betalning får ligga och vem den eskaleras till. Det kräver också ett sätt att flytta ett atteststeg till en annan attestant.
  - *Förfallodatum* påverkar både betalningsformuläret, datamodellen och när en betalning får genomföras. Det avgränsades redan i [ADR 0013](../decisions/0013-avgransning-av-UX-feedback-infor-MVP.md).
  - *Kundvärde.* Det viktigaste är att man ser var betalningen står och vem man ska kontakta. Det är byggt. Att automatisera påminnelsen kommer i andra hand.

### Notifieringar via e-post

- **Feedback:** Ta upp e-post, påminnelser och notifieringar på andra sätt.
- **Läge:** Notifieringar finns i portalen, som badges i sidomenyn för både attestant och initiatör. E-post skickas inte.
- **Varför avgränsat:**
  - *Ny infrastruktur.* E-post kräver en e-posttjänst, en kö och nya försök när utskick misslyckas, och ett beslut om vilka händelser som ska ge mejl.
  - *Kunden beskriver mejl som en del av problemet* ("mail sväljs tyst"). Därför valde vi badges i portalen som den första lösningen ([kravtolkning.md](../kravtolkning.md), behov 3).
  - Avgränsat i [ADR 0013](../decisions/0013-avgransning-av-UX-feedback-infor-MVP.md).

## DM-feedforward: täcker vi alla krav?

- **Feedback:** Kontrollera att vi har alla krav, så att vi inte avgränsar något som måste finnas med.
- **Genomfört:** Vi har gått igenom kundens behov i [kravtolkning.md](../kravtolkning.md) mot koden. Alla behov som är markerade som MVP finns med:
  - attestkedja som triggas av belopp, med dubbel attest över 50 000 kr
  - signal till den som ska attestera (badge)
  - kontroll av kontonummer vid inmatning (IBAN med kontrollsiffra)
  - spårbarhet per betalning och en auditlogg
  - säker inloggning, rollstyrning och isolering mellan företag
  - samtidighetsskydd och atomära transaktioner
- **Batch/CSV (behov 5):** uppladdning av en hel fil med betalningar var det enda uttalade kundkravet som låg utanför MVP, med motivering i [kravtolkning.md, avsnitt A](../kravtolkning.md#a-medvetna-avvikelser-mot-källan-och-varför): kunden rangordnar själv korrekt godkännande och pålitlighet högst, så kärnan gick först. När kärnan stod byggdes uppladdningen till slutleveransen (#255). En initiatör laddar upp en CSV-fil under Ny betalning, och hela filen importeras allt-eller-inget med den native CSV-parsern. Därmed är inget av kundens uttalade krav avgränsat bort.
