# Backlog — SEB Företagsbetalningar v2

- Tekniska garantier ligger som AC (acceptanskriterier) under storyn
 - MVP: alla rader med MVP utgör kärnflödet. 
 - Stories utan tagg = breddning. Byggs när kärnan står.

> Logga in -> skapa betalning över tröskeln -> approval-kedjan triggas -> attestant får badge -> godkänner -> betalning utförd + saldo dras atomärt -> varje steg loggas och hela kedjan kan följas per betalning vem, vad, när.
---

## Epic: Auth/behörighet
**Kundproblem:** Reglerat område, får aldrig hända att fel person kommer åt eller att uppgifter läcker.

- **[MVP]** Som användare vill jag logga in säkert så att bara jag kommer åt mitt konto och mina betalningar.
  - AC: ,     ingen SQL-injektion (parameteriserat / JPA). Fixar BUG-001.
- **[MVP]** Som användare vill jag att mitt lösenord lagras oknäckbart så att det inte kan användas även om databasen läcker.
  - AC: BCrypt, inte MD5. Fixar BUG-002.
- **[MVP]** Som attestant vill jag bara kunna hantera mina egna atteststeg så att ingen kan godkänna i mitt namn.
  - AC: stegets attestant-id jämförs mot inloggad användare. Fixar BUG-011 (IDOR).
- **[MVP]** Som administratör vill jag att varje roll bara kommer åt det behörigheten tillåter så att ingen kan agera utanför sin roll.
  - AC: rollstyrning via authorities / `@PreAuthorize`, inte strängjämförelse.
- Som admin vill jag ha admin-vyer för användare och roller så att jag kan hantera behörigheter.
- Som användare vill jag att min inloggning förnyas i bakgrunden utan att jag loggas ut i onödan, samtidigt som en stulen token snabbt blir oanvändbar, så att jag kan arbeta ostört utan att säkerheten tummas på.
  - AC: access-token med kort livslängd. Refresh-token med rotation (ny token vid varje förnyelse, den gamla ogiltigförklaras). En session ska gå att återkalla.
- Som användare vill jag logga in med ett andra steg utöver lösenordet så att mitt konto är skyddat även om lösenordet läcker.
  - AC: andra faktor krävs efter korrekt lösenord reservväg om andra faktorn tappas.
## Epic: Betalning/godkännandekedja
**Kundproblem:** Godkännanden via mejl, betalningar godkänns av fel person eller i fel ordning.

- **[MVP]** Som initiator vill jag skapa en betalning med belopp och mottagaruppgifter så att den kan gå vidare för godkännande och betalas ut.
- **[MVP]** Som attestant vill jag att betalningar över tröskelbeloppet styrs till rätt attestkedja så att stora belopp aldrig betalas ut oattesterade eller godkänns i fel ordning.
  - AC: en enda tröskeldefinition (fixar BUG-006); > tröskel → atteststeg skapas; genomförs inte förrän rätt attestant godkänt.
- **[MVP]** Som attestant vill jag godkänna en väntande betalning direkt i systemet så att den genomförs och saldot dras korrekt.
  - AC: status + saldo uppdateras atomärt i samma `@Transactional` (fixar BUG-009). `@Version` mot dubbelgodkännande.
- Som attestant vill jag avvisa en betalning med en kommentar så att initiatorn vet varför den stoppades.
- Som admin vill jag att riktigt stora belopp kräver två attestanter (attestant 1 + 2) så att ingen enskild person kan släppa igenom dem.
   - Breddning: andra kedjesteget (VD = attestant 2)

## Epic: Kontonummerkontroll (IBAN/BIC)
**Kundproblem:** Felaktiga kontonummer upptäcks först när betalningen kommer i retur.

- **[MVP]** Som initiator vill jag att ett felaktigt IBAN fångas vid inmatning så att jag kan rätta det på plats och betalningen inte kommer i retur.
  - AC: MOD97-kontrollsiffra (ISO 13616), inte bara format (fixar BUG-003); delad kod, inte duplicerad; BIC-format (ISO 9362) valideras.
  - Breddning: samma validering via native C-modul med Java-fallback.

## Epic: Notifiering/Påminnelse
**Kundproblem:** Den som ska godkänna får ingen signal, betalningar fastnar och blir sena.

- **[MVP]** Som attestant vill jag se en badge när betalningar väntar på mitt godkännande så att jag inte behöver hålla reda på det själv.
  - AC: badge räknar väntande atteststeg för inloggad attestant. Ersätter tyst sväljda mail (BUG-007). Se ADR 0008.
- Som attestant vill jag få en påminnelse om betalningar jag inte hunnit godkänna så att inget blir liggande och försenat.
  - Kräver e-post (mockad SMTP), kö + retry med backoff, Notification-tabell.

## Epic: Audit/spårbarhet
**Kundproblem:** Går inte att lita på loggen, kan inte visa vem som godkände vad.
- **[MVP]** Som admin vill jag följa hela händelsekedjan för en enskild betalning. Varje godkännandesteg, av vem och när den är utförd, så att jag svart på vitt kan visa att rätt person godkänt i rätt ordning
  - AC: kronologisk vy per betalning som visar aktör, händelse, tidsstämpel och ordning. Täcker skapa/godkänn/avvisa.
- **[MVP]** Som attestant vill jag se en logg över alla beslutshändelser så att jag i efterhand kan visa vem som godkände vad och när.
  - AC: alla händelser till en plats (DB), i samma transaktion som händelsen (fixar BUG-008); strukturerad loggning.
- **[MVP]** Som admin vill jag att loggen bara visar min organisations händelser så att uppgifter inte blandas mellan kunder.
  - AC: tenant-filtrerad vy i API:t.
- Som attestant vill jag att loggen är manipuleringssäker (signering) så att jag kan lita på att inget ändrats i efterhand.
  - Breddning: append-only + HMAC-SHA256-kedja via native-modul.
- Som attestant vill jag exportera loggen för månadsbokslut så att jag kan redovisa godkännanden.

## Epic: Batch/CSV
**Kundproblem:** Vill ladda upp en hel fil t.ex. löner i stället för rad för rad.

- Som initiator vill jag ladda upp en fil med många betalningar så att jag slipper mata in rad för rad.
  - AC: RFC 4180-parsning (fixar BUG-004); insert i transaktion,  allt eller inget (BUG-005); filstorleksgräns (BUG-012).
  - Breddning: prestandakritisk native CSV-parser (500 rader < 5 ms).

---

**Utanför nuvarande scope:** valutaomvandling (allt hanteras i SEK).
