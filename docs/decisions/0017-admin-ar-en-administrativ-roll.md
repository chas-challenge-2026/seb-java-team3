# ADR 0017 — Admin är en administrativ roll och flyttar inga pengar

- **Status:** Accepterad
- **Datum:** 2026-11-02
- **Deltagare:** Team SEB 3 · drivande: Jonathan Isaksson
- **Berör:** Auth & behörighet, betalning och godkännandekedja, audit
- **Relaterade issues:** #242, #243, #80
- **Relaterat:** [ADR 0012](0012-refresh-token-i-httponly-cookie-med-rotation.md), kundbehov 1 och 7 i `docs/kravtolkning.md`, CTO-feedbacken punkt 2 och 4 i `docs/Risk-feedfoward.md`

**Uppföljning 2026-11-03:** frontendens route-spärrar, navigation och dashboard är anpassade och admins attestvy är skrivskyddad. Användarsidan är API-kopplad. Audit-vyn skiljer användarhändelser från betalningar. Backendens Postgres-svit och frontendens admin-E2E passerar; se [projektgenomgången](../project-review.md) och [teststatus](../test-status.md). Beslutets ursprungliga konsekvenser och uppföljningslista nedan beskriver läget när det fattades.

## Kontext

Kundens första behov är att rätt person godkänner rätt betalning i rätt ordning, och att det går att visa svart på vitt (`docs/kravtolkning.md`, behov 1 och 6). Vi har tre roller: initiator, attestant och admin.

Så här var rollen admin byggd till och med den här ändringen:

- Admin fick **skapa betalningar**, med samma rättigheter som en initiator.
- Admin fick **godkänna och avvisa alla steg** i företaget och slapp kontrollen av vilken attestant som steget är tilldelat.
- Admin fick läsa hela audit-loggen, medan en attestant bara ser skapa, godkänn och avvisa.
- Admin hade **ingen egen uppgift**. Det finns inga vyer för användare och roller (#80 är inte byggd), så roller kunde bara ändras direkt i databasen.

Det betyder att en admin kunde skapa en betalning och godkänna den själv. Det går emot behov 1, och det bryter fyraögonprincipen som är poängen med attestkedjan. CTO-feedbacken bad oss se över både vertikal och horisontell behörighet och ta fram en accessmatris (punkt 2 och 4).

Två saker begränsar lösningen. Varje användare har bara en roll, och e-postadressen är unik i hela databasen. Rollen ligger i JWT:n, så ett rollbyte slår igenom vid nästa förnyelse av inloggningen, högst 15 minuter senare (ADR 0012).

## Beslut

Admin är en **administrativ roll** som inte flyttar pengar. Följande gäller:

1. **Admin skapar inga betalningar** och har inga egna betalningar (`/api/my-payments`, `/api/payments/config` och `/api/payments/changed-count` är bara för initiatorn).
2. **Admin godkänner och avvisar inga betalningar.** Bara attestanten som steget är tilldelat kan fatta beslutet.
3. **Admin får läsa och få översikt** i det egna företaget: hela audit-loggen, tidslinjen per betalning med atteststegen, attestkorgen och räknaren, samt konton.
4. **Admin hanterar användare och roller** i det egna företaget (#242): lista, skapa, byta roll mellan INITIATOR och ATTESTANT och ta bort en användare som aldrig har gjort något. ADMIN kan inte tilldelas via API:t, och en administratörs roll ändras aldrig där, inte heller admins egen, så att ingen kan ge sig själv eller någon annan mer makt eller ta bort den sista administratören. En attestant med väntande atteststeg kan inte bli initiator. Varje skapande, rolländring och borttagning loggas i audit (`CREATE_USER`, `UPDATE_USER_ROLE`, `DELETE_USER`), utan lösenord.
5. **Fyraögonprincipen gäller alla roller:** den som har skapat en betalning kan aldrig själv godkänna eller avvisa den, även om hen är tilldelad steget. Kontrollen sitter i `ApprovalService` och gäller alla vägar in.

Behörigheten kontrolleras i backend, med `@PreAuthorize` i controllern och i servicen. Frontend döljer bara det som inte går att göra.

### Accessmatris

Allt gäller bara det egna företaget.

| Åtgärd | Initiator | Attestant | Admin |
|---|---|---|---|
| Skapa betalning, se egna betalningar och badge | Ja | Nej | Nej |
| Se företagets konton | Ja | Ja | Ja |
| Se attestkorgen och räknaren | Nej | Egna steg | Hela företaget, bara läsa |
| Godkänna eller avvisa | Nej | Egna steg, aldrig sin egen betalning | Nej |
| Audit-logg och tidslinje | Nej | Skapa, godkänn och avvisa | Allt |
| Hantera användare och roller | Nej | Nej | Ja |

### API för användaradministration

Alla anrop kräver rollen ADMIN. Företaget kommer alltid från admins token, aldrig från anropet. Felsvar har formen `{ timestamp, status, error, message }`, utom 401 och det vanliga 403 för fel roll.

| Anrop | Kropp | Svar |
|---|---|---|
| `GET /api/admin/users` | - | 200 `[{ id, name, email, role }]`, sorterat på namn |
| `POST /api/admin/users` | `{ name, email, password, role }` | 201 `{ id, name, email, role }`, 400 (ogiltiga uppgifter), 409 (e-posten används) |
| `PATCH /api/admin/users/{userId}/role` | `{ role }` | 200 `{ id, name, email, role }`, 400 (ogiltig roll), 403 (målet är en administratör), 404 (användaren finns inte i företaget), 409 (attestanten har väntande steg) |
| `DELETE /api/admin/users/{userId}` | - | 204, 403 (målet är en administratör), 404 (användaren finns inte i företaget), 409 (användaren har skapat betalningar eller har tilldelats eller beslutat atteststeg) |

`DELETE` tar bort användaren permanent, men bara om hen aldrig har gjort något: betalningarna och atteststegen pekar på användaren, och historiken (vem som skapade och godkände vad) ska bevaras. I praktiken går det alltså att ta bort ett konto som skapats av misstag, inte en användare som har arbetat i systemet. Användarens refresh-token tas bort samtidigt, så hen kan inte förnya sin inloggning, men ett redan utfärdat access-token gäller tills det går ut (högst 15 minuter). Borttagningen loggas som `DELETE_USER` med namn och e-post.

Regler för `POST`: namn högst 100 tecken, giltig e-post högst 100 tecken (sparas med små bokstäver), lösenord minst 12 tecken och högst 72 byte, och `role` är `INITIATOR` eller `ATTESTANT`. Lösenordet sparas som BCrypt med kostnad 12.

## Alternativ vi övervägde

- **Behålla admin som superanvändare (som tidigare)** - valdes bort. Fyraögonprincipen går att kringgå med rollen, och rollen har ingen egen uppgift.
- **Ta bort admin helt** - valdes bort. Då finns ingen som kan se hela loggen, och användare och roller måste skötas direkt i databasen. Kunden vill kunna visa vem som godkände vad och när, och det kräver en roll med överblick.
- **Låta admin skapa betalningar men förbjuda att godkänna sina egna** - valdes bort. Regeln om att skaparen inte får godkänna behålls för alla roller, men admin tillför då fortfarande ingenting administrativt. Dessutom kan den som delar ut behörigheter själv flytta pengar, vilket vi vill undvika.
- **Låta admin ändra alla roller, även administratörers** - valdes bort. Då kan en admin ta bort den sista administratören eller ge sig själv en roll med andra rättigheter. Att göra någon till administratör hanteras i stället direkt i databasen tills vi har ett uttalat behov.
- **Flera roller per användare** (en person kan vara både admin och attestant) - skjuts upp. Det kräver att rollen i JWT:n och alla kontroller görs om, och det går emot att rollerna ska hållas isär.

## Konsekvenser

**Positiva**
- Fyraögonprincipen går inte att kringgå med en roll. Det är lättare att visa kunden att ingen kan godkänna sin egen betalning.
- Den som delar ut behörigheter kan inte själv flytta pengar. Ansvaret är tydligt uppdelat.
- Rollen får en egen uppgift, och CTO-feedbackens accessmatris finns nu i repot.
- Regeln om skaparen gör att en framtida rolländring inte kan öppna för att någon godkänner sin egen betalning.

**Negativa / risker**
- **Frontend och backend går isär tills frontend ändras.** `router.tsx` släpper fortfarande in admin på `/payments/new`, `/my-payments` och `/attest`, och `SideBar` visar Attestera för admin. Admin får därför 403 på de sidorna. → Frontend ändras separat (#243). Admin ska behålla en skrivskyddad attestvy utan godkänn- och avvisa-knappar.
- **Seed-användaren Sara Ek (admin) kan inte längre skapa eller godkänna** betalningar. Demon använder Lisa (initiator) och Johan (attestant), så den påverkas inte.
- **En person som både ska administrera och attestera** måste ha två konton med olika e-postadresser, eftersom en användare bara har en roll. Det är ett medvetet val tills kunden efterfrågar något annat.
- **Dubbel attest (ADR 0016) har inte längre någon nödutgång.** ADR 0016 räknade med att en admin kunde besluta om ett steg när en attestant var borta. Det går inte längre: en betalning som väntar på en frånvarande attestant ligger kvar, och pengarna förblir reserverade, tills attestanten är tillbaka. → Det behövs en ersättare eller en omtilldelning av steget, där admin flyttar steget till en annan attestant utan att själv besluta. Det är inte byggt och bör bli en egen issue.
- **Audit-listan visar bara de 200 senaste händelserna.** Admin ser alltså inte "all historik" ännu. → Sidindelning eller filter behövs.
- **En rolländring slår igenom när inloggningen förnyas**, högst 15 minuter senare, eftersom rollen ligger i token (ADR 0012). Betalningsflödet tål mellantiden: beslut kontrolleras mot steget och skaparen, och en betalning tilldelas aldrig sin skapare. En attestant som just blivit initiator kan inte heller ha väntande steg, eftersom ändringen då nekas.
- **E-postadressen är unik i hela databasen** (V1), inte per företag. Skapar admin en användare med en adress som finns hos ett annat företag får hen 409, och kan därför avgöra om adressen finns där. Att göra den unik per företag kräver en migration och en ändring av inloggningen, som slår upp på e-post. Vi accepterar det för nu.
- **Den sista attestanten kan göras om till initiator** om hen inte har väntande steg. Betalningar över tröskeln får då 409 ("ingen attestant") tills en ny attestant finns. Vi har inte lagt någon spärr på det.
- **En användare med historik kan inte tas bort, och det finns inget sätt att inaktivera en användare eller byta lösenord.** En användare som skapat betalningar eller deltagit i attest får därför ligga kvar. Att kunna avsluta ett sådant konto kräver en inaktivering (en spärrflagga som inloggningen kontrollerar) i stället för en borttagning. Ett lösenord som admin har satt kan användaren inte byta själv.
- **Audit-rader för användare använder `entity_type = USER` och användarens id.** Audit-vyn i frontend öppnar betalningens tidslinje för `entityId` på varje rad, så en användarrad skulle visa tidslinjen för en betalning med samma id. → Frontend ska bara göra rader expanderbara när de gäller en betalning.

## Uppföljning

- Frontend (#243): ta bort admin från de tre betalningsroutes och från knapparna i attestvyn. Bygg användarsidan mot API:t ovan, med ett rollval per rad. Lägg till svenska etiketter för `CREATE_USER` och `UPDATE_USER_ROLE` i `formatters.ts`, och gör audit-rader expanderbara bara när `entityType` är `PAYMENT`.
- Kör Docker-sviten: testerna som ändrades i #242 och radlåset vid rolländring är inte körda mot Postgres lokalt.
- Audit-listan: sidindelning eller filter, så att admin kan se hela historiken.
- Behov som inte är byggda: inaktivera en användare, byta lösenord, att göra någon till administratör, och en spärr mot att göra den sista attestanten till initiator.
- README skrivs om i #202. Raderna om roller är uppdaterade här.
- Omvärdera beslutet om kunden vill ha en roll som både administrerar och attesterar (flera roller per användare).
