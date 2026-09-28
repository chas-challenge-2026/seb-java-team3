## SEB-Team 3 – Manus DM-film (Karl)

Karl tittar på hur det vi byggt hänger ihop till en sammanhållen leverans. Han vill se att vi förstår
kundens viktigaste krav, att vi är ärliga om nuläget och att vi har en plan till kvalitetsdemon i v10.
Vi visar alltså inte allt vi byggt, vi visar hur vi tänker och prioriterar.

Den röda tråden i filmen är samma mening hela vägen:
**"Rätt person godkänner rätt betalning i rätt ordning, och det går att visa i efterhand."**

Max 5 min. Manuset är ca 490 ord, alltså knappt 4 min i lugnt taltempo. Resten är marginal för klick i demon och byten mellan talare.
Läs inte ordagrant, prata fritt utifrån punkterna. Byt ut Person 1–4 mot namn.

| Tid | Del | Vem | Skärm |
| --- | --- | --- | ----- |
| 0:00–1:05 | Intro + kundens viktigaste krav | Person 1 | `docs/mvp.md` (Kundproblemet, MVP-flödet) |
| 1:05–2:20 | Nuläge mot krav (live-demo) | Person 2 | Appen lokalt: login → betalning → badge → godkänn → audit |
| 2:20–3:30 | Helhetsleveransen | Person 3 | Enkel arkitekturbild / mappstruktur, Dockerfile, CI i GitHub |
| 3:30–4:40 | Scope, prioritering + vägen till kvalitetsdemon | Person 4 | Lista "Måste till v10" (t.ex. i `docs/backlog.md` eller GitHub-board) |

---

### Person 1 – Intro + kundens viktigaste krav (0:00–1:05)

**Skärm:** `docs/mvp.md`, scrolla till *Kundproblemet* och *MVP-flödet*.

> Hej Karl! Vi är team 3 och bygger om SEB:s företagsbetalningar. Jag börjar med kraven,
> [Person 2] visar nuläget, [Person 3] hur delarna hänger ihop och [Person 4] vad vi prioriterar.
>
> Kravbilden innehöll mycket: batch-uppladdning, mejlpåminnelser, VD som andra attestant,
> kontoöversikt. Men kundens egentliga problem är att betalningar godkänns via mejl, av fel person,
> i fel ordning och utan spårbarhet.
>
> Det viktigaste leveransen ska lösa är alltså: rätt person godkänner rätt betalning i rätt ordning,
> och det går att visa i efterhand vem som gjorde vad och när.
>
> Därför bygger vi allt kring ett flöde: logga in, skapa en betalning över tröskeln, attestanten får
> en notis, godkänner, pengarna dras och allt loggas. Resten har vi medvetet lagt utanför MVP, och
> det beslutet finns dokumenterat i en ADR.

---

### Person 2 – Nuläge mot krav (1:05–2:20)

**Skärm:** Appen lokalt. Ha två flikar inloggade i förväg (initiator + attestant) så att demon går snabbt.

> Jag loggar in som initiator, med BCrypt och JWT i stället för den gamla sårbara inloggningen.
> Jag skapar en betalning på 75 000 kronor. Den ligger över tröskeln, så den väntar på attest.
> IBAN valideras både i formuläret och i backend, så fel kontonummer stoppas direkt.
>
> *(byt flik)* Attestanten ser en badge och godkänner. Betalningen genomförs och saldot dras i
> samma transaktion. Initiatorn kan inte godkänna sin egen betalning.
>
> *(öppna audit-vyn)* Här syns hela kedjan: vem som skapade, vem som godkände och när.
>
> Kärnflödet fungerar alltså hela vägen. Det som återstår: betalningar under tröskeln markeras som
> klara utan att saldot dras, vi kontrollerar inte att kontot tillhör rätt företag, och saldot går
> inte att se i gränssnittet än.

---

### Person 3 – Helhetsleveransen (2:20–3:30)

**Skärm:** En enkel bild eller mappstrukturen: `frontend/` → `backend/.../controller` → `service` →
`repository` → databas. Visa sedan `backend/SebPortal/Dockerfile` och Actions-fliken i GitHub.

> Så hänger delarna ihop: React-frontend pratar med ett REST-API som är uppdelat i controller,
> service och repository. Servicelagret äger reglerna, alltså tröskel, vem som får attestera och
> loggning, så vi kan testa reglerna utan gränssnitt. IBAN-valideringen är en egen C-modul.
>
> Databasen byggs med Flyway, så alla får samma schema. Sedan förra veckan kör GitHub Actions alla
> tester på varje pull request.
>
> Men vår största lucka *(peka på Dockerfile)*: Docker-imagen bygger backend, men inte React-appen.
> Lokalt fungerar allt, men det vi driftsätter visar inte det nya gränssnittet. Delarna fungerar var
> för sig, men är inte en leverans än. Därför står det först på listan till v10.

---

### Person 4 – Scope, prioritering och vägen till kvalitetsdemon (3:30–4:40)

**Skärm:** Listan "Måste till v10" (nedan), gärna som issues på boarden.

> Fyra veckor kvar. Vår regel: det som gör MVP-flödet stabilt och demobart går före nya funktioner.
> Det här måste vara klart till vecka 10:
>
> 1. **En leverans:** React byggs in i Docker-imagen, så vi kan demo:a från en riktig miljö.
> 2. **Pengarna stämmer:** saldot dras även under tröskeln, man kan bara betala från sitt eget
>    företags konton, och det måste finnas täckning.
> 3. **Synligt resultat:** en enkel saldovy, så kunden ser att pengarna dras.
> 4. **Säker drift:** JWT-hemligheten flyttas ut ur koden.
> 5. **Bevis:** en `test-status.md` som visar vad som är testat, vilket är vår egen definition av klart.
>
> Vi väljer bort batch, mejlpåminnelser, VD-attest och 2FA till efter demon. En stabil kärna slår
> fem halvfärdiga flöden.
>
> En fråga till dig: har vi prioriterat ner något som du tycker borde vara med på demon? Tack!

---

## Förberedelser innan inspelning

- [ ] Byt Person 1–4 mot namn i tabellen och i introt.
- [ ] Starta stacken lokalt (backend + `npm run dev`) och testa demoflödet en gång innan. Logga in
      initiator och attestant i varsin flik i förväg.
- [ ] Använd en ny betalning i demon, inte seed-betalningarna (deras audit-historik syns inte i
      tidslinjen).
- [ ] Person 2: undvik att visa betalningar under tröskeln och saldo i UI:t, det är kända luckor
      som vi berättar om i stället.
- [ ] Kolla innan inspelning att punkterna under "återstår" fortfarande stämmer mot develop. Stryk
      det som hunnit bli klart.
- [ ] Spela in i Loom (en inspelning, fyra personer turas om att dela skärm), håll under 5 min.
- [ ] Lägg länken under **"DM - teamfilm"** på vår rad i Google Sheet *Chas Extended Challenge –
      Specialist Feed Forward*. Kolla i ett inkognitofönster att den öppnas utan behörighet.
