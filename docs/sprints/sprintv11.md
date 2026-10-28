# Sprint v11

**Datum:** 2026-10-28  
**Team Lead denna sprint:** ____  
**Backlog-ägare:** ____  
**Närvarande:** Jonathan, Adnan, Markus, Pontus, Marcus, Mohammed

## Sprintmål

- Få klart de viktigaste ändringarna från kundens feedback. Allt ska vara byggt, testat och redo att visas upp. Framför allt ska det vara tydligt hur man godkänner och avvisar betalningar, och initiatorn ska kunna se vad som hänt med sina betalningar.
- Se till att dokumentationen stämmer med det vi faktiskt har gjort. `test-status.md` och `README` ska vara uppdaterade inför slutleveransen så att inget saknas eller står som klart när det inte är det.

## Kapacitet

- Team-seb, 6 personer med i sprintplaneringen. Det här är sista sprinten innan slutleveransen den 4/11.
- Fokus ligger på att få klart kundens feedback och uppdatera dokumentationen. Vi tar inte in några nya större funktioner nu.

## Sprint backlog (kopplat till målet)

### Backend

| Task | Ägare | Est. | Not |
|------|-------|------|-----|
| Lägga till meddelande vid godkänn/avvisa, som sparas tillsammans med beslutet och visas i historiken | Jonathan | M | |
| Skapa endpoint som räknar initiatorns betalningar med ändrad status (badge) | Adnan | M | Behövs innan frontend-badgen kan göras |
| Lägga till "sedd"-status för badgen | Markus | M | Behöver stämmas av med Adnan |

### Frontend

| Task | Ägare | Est. | Not |
|------|-------|------|-----|
| Göra vyn där attestanten kan godkänna/avvisa en betalning, skriva meddelande och bekräfta | Pontus | M | |
| Lägga till bekräftelse när initiatorn skapar en betalning | Marcus | S | Använd en gemensam bekräftelse-komponent |
| Göra en dropdown där initiatorn kan se mer info om en avvisad betalning under Mina betalningar | Mohammed | M | |
| Lägga till badge på "Mina betalningar" och genväg till Attestera | Marcus | S | Väntar på endpointen från Adnan |

### Dokumentation (primärmål 2)

| Task | Ägare | Est. | Not |
|------|-------|------|-----|
| Uppdatera `test-status.md` så den stämmer med koden (rollback, refresh-token och antal tester) | Pontus | S | Vi har gjort mer än vad som står där just nu |
| Uppdatera `README` så den stämmer med det som är byggt (refresh-token och avvisa-UI) | team-seb-3| S | Viktigt inför granskningen |
| Fixa seed-IBAN så att fler än ett konto fungerar med MOD97 | team-seb-3 | S | Annars kan det bli problem under demon |

## Beroenden

- Marcus behöver vänta på att Adnan blir klar med räkne-endpointen innan badgen på "Mina betalningar" kan kopplas in. Lägg in "Blocked by" i GitHub så det syns.
- Markus och Adnan behöver stämma av hur "sedd"-statusen och räkne-endpointen ska fungera tillsammans.

## Risker och beroenden (F3)

| Risk / beroende | Sannolikhet | Påverkan | Vad gör vi åt det |
|-----------------|-------------|----------|-------------------|
| Frontend-badgen blir försenad om API:t inte är klart | Medel | Medel | Adnan försöker få klart endpointen tidigt |
| Vi hinner inte uppdatera dokumentationen innan 4/11 | Medel | Hög | Vi tar dokumentationen parallellt med resten av arbetet |
| Vi börjar lägga till för mycket nytt sista veckan | Låg | Medel | Håller oss till det som redan finns i sprint backloggen |
| Demon fungerar bara med ett giltigt konto | Medel | Medel | Fixa seed-IBAN så tidigt som möjligt |

## Beslut denna planering

- `feature/ux-feedback-audit` är mergad och klar.
- Om vi behöver ta några fler större beslut under sprinten skriver vi en ADR och länkar den.

## Att bevaka

- [ ] `test-status.md` och `README` ska vara uppdaterade innan slutleveransen.
- [ ] Räkne-endpointen behöver vara klar innan frontend-badgen kan göras.
- [ ] Seed-IBAN ska fixas så vi kan använda flera konton under demon.
- [ ] Boka in fredagens F8-dialoger.
- [ ] Alla tasks ska ha en ägare och ett estimat innan de flyttas till Ready.