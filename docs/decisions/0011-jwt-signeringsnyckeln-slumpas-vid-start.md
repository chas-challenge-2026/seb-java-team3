# ADR 0011 — JWT-signeringsnyckeln slumpas vid start

- **Status:** Föreslagen
- **Datum:** 2026-10-06
- **Deltagare:** Jonathan Isaksson · drivande: Jonathan Isaksson
- **Berör:** Auth & behörighet, drift
- **Relaterade issues:** #194
- **Relaterat:** [ADR 0009](0009-jwt-i-localstorage-ersatter-httponly-cookie.md), CTO-feedbacken i `docs/Risk-feedfoward.md` (punkt 8: JWT-nyckel, rotation, läckt nyckel)

## Kontext

Våra JWT signeras med en symmetrisk nyckel (HS256). `JwtService.parseToken` litar på innehållet i token (användar-id, `tenantId`, roll) så fort signaturen stämmer och slår inte upp användaren i databasen. Den som kan signera kan alltså vara vem som helst, i vilket företag som helst, med vilken roll som helst.

Nyckeln stod i klartext i `application.properties` (`jwt.secret`), och repot är publikt. Vem som helst kunde därmed bygga en giltig ADMIN-token för valfritt företag, utan inloggning. Det är verifierat två gånger: live mot en isolerad stack 2026-09-21 (en förfalskad ADMIN-token gav 200 på `/api/audit`) och i `JwtServiceTest.tokenForgedWithAKnownSecret_isOnlyAcceptedByAServerUsingThatSecret`.

Begränsningar som styr valet:

- Compose-filen ligger i det publika repot, och `DRIFT.md` beskriver inget sätt att skicka in hemligheter till stage eller prod. En miljövariabel hade också behövt stå i repot.
- Stage byggs om vid varje push till `develop`. En app som vägrar starta utan hemlighet riskerar att ta ner miljön.
- CTO-feedbacken pekade ut JWT-hanteringen som det allvarligaste: symmetrisk eller asymmetrisk nyckel, rotation och vad som händer om nyckeln läcker.

## Beslut

Ingen JWT-hemlighet finns i repot. Om `jwt.secret` saknas slumpar `JwtService` en HS256-nyckel vid varje start, och nyckeln finns bara i minnet. En fast nyckel går att sätta med miljövariabeln `JWT_SECRET` (minst 32 byte), och den gamla, läckta dev-hemligheten avvisas med ett startfel. Den gamla hemligheten finns kvar i `JwtService` som en spärrlista, så att ingen kan sätta den igen. Den är redan bränd och används inte som nyckel någonstans.

## Alternativ vi övervägde

- **Byta till en ny, bättre hemlighet i `application.properties`** - valdes bort. Allt som committas i ett publikt repo är publikt, även i git-historiken.
- **`JWT_SECRET` som obligatorisk miljövariabel, startfel om den saknas** - det vanliga valet i en riktig drift, men plattformen kan inte skicka in hemligheter och stage kunde sluta starta. Koden stöder redan `JWT_SECRET`, så det går att införa när plattformen får stöd.
- **Nyckel som genereras och sparas i databasen** (överlever omstart, ger riktig rotation med `kid`) - valdes inte nu. Det kräver tabell, tjänst och rotationslogik, och nyckeln ligger i samma databas som användarna, så ett läckage av databasen räcker för att förfalska token. En kandidat om inloggningar ska överleva omstart.
- **Asymmetrisk signering (RS256 eller EdDSA)** - ger isolering först när en andra tjänst ska kunna verifiera token utan att kunna signera. Vi har en tjänst som både signerar och verifierar, så vi får ingen säkerhetsvinst, bara mer nyckelhantering. Omvärderas om en andra tjänst tillkommer.

## Konsekvenser

**Positiva**
- Token förfalskade med den gamla, publika hemligheten nekas (`SignatureException`). Ingenting i repot, git-historiken eller konfigurationen kan längre användas för att förfalska token.
- Fungerar utan stöd från plattformen och utan manuell konfiguration: appen startar alltid.
- Omstart är samtidigt nyckelrotation. Om nyckeln misstänks ha läckt är åtgärden en redeploy.
- En egen nyckel kan fortfarande sättas via `JWT_SECRET`, och en för kort eller läckt hemlighet ger ett tydligt startfel.

**Negativa / risker**
- Varje omstart (varje push till `develop` eller `main`) gör alla token ogiltiga, så användarna måste logga in igen. → Märks inte när refresh token finns (planerad, #195), eftersom den kan sparas i databasen och överleva omstart.
- Fungerar bara med en instans. Två instanser får olika nycklar och nekar varandras token. → Då behövs en delad nyckel via `JWT_SECRET` eller en nyckel lagrad i databasen.
- Den gamla hemligheten ligger kvar i git-historiken och räknas som bränd. En miljö som fortfarande kör gammal kod accepterar förfalskade token tills den byggts om. → Deploya så fort ändringen är mergad.
- Skyddar inte mot stulna token (XSS, R-13) eller mot att någon når serverns minne.
- Seed-användarna med lösenordet `password123` är en separat, accepterad risk: de är avsiktligt publika i README för demon. → Go-live: ta bort seeden och byt lösenord.

## Uppföljning

- Refresh token (#195) gör att en omstart inte märks för användarna. Designen beskrivs i en egen ADR.
- Lägg risken "committad JWT-hemlighet" i riskregistret när det checkas in (#201), med status Mitigerad.
- Omvärdera vid behov: om plattformen får stöd för hemligheter, sätt `JWT_SECRET` i drift. Om vi får fler än en instans, eller om inloggningar ska överleva omstart, lagra nyckeln i databasen med `kid`.
- Före go-live: kontrollera att ingen hemlighet finns i repot (`git grep -i "jwt.secret"` ska bara hitta kod och dokumentation) och att seed-användarna är borttagna.
