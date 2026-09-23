## SEB-Team 3 tolkning av uppgift

VI ska visa att vår riskanalys har påverkat hur vi driver projektet. Victor ska kunna se sambandet mellan: ri vad vi fortfarande är osäker på. sk -> resonemang -> beslut/åtgärd -> konsekvens för lösningen ->

Så film strukturen är: 
**0:00–0:20** – kort intro: vad ni visar och varför ni valt just de två riskerna. (Pontus)
**0:20–2:05** – Risk 03: risk -> varför viktig -> åtgärd -> trade-off -> hur ni verifierat. (Marcus Johansson)
**2:05–3:55** – Risk 06: samma struktur. (Jonathan och Adnan) 
**3:55–5:00** – Kvarvarande osäkerheter: vad vi ännu inte vet/testat/verifierat.(Pontus)

Vi har valt 2 risker som vi har jobbat mycket med. R-03, R-06. vi har valt att svara på de här frågorna för att täcka det vi vill prata om.
**Vad var risken?**
**Varför var den viktig för projektet?**   
**Vad gjorde ni åt den?**
**Vad behövde ni offra eller ändra?** 
**Vad blev resultatet – och hur vet ni att åtgärden fungerar?**
**Finns något fortfarande osäkert?**  
### R-03 – Fel attestant kan godkänna ett atteststeg

**Vad var risken?**  
Vi identifierade en IDOR-risk i attestkedjan. I den tidigare lösningen hämtades informationen om vem som utförde attesteringen från requesten i stället för från den autentiserade användaren. Det gjorde att en attestant potentiellt kunde godkänna eller avslå någon annans atteststeg.

**Varför var den viktig?**  
Det här påverkar ett av systemets kärnlöften: att rätt person ska attestera rätt steg i rätt ordning. Om en användare kan agera som en annan attestant förlorar attestkedjan sin säkerhetsfunktion.

**Vad gjorde vi åt den?**  
Vi ändrade flödet så att den agerande användaren hämtas från JWT-token. Därefter kontrolleras användaren mot ägaren av atteststeget innan ett godkännande eller avslag tillåts. Om användaren inte äger steget returneras 403.

**Vad behövde vi ändra eller välja bort?**  
Vi valde att ta bort den gamla sessionsbaserade attestvyn i stället för att försöka underhålla två parallella lösningar. Vi förenklade alltså systemet till en gemensam väg genom JWT och API:et.

**Hur vet vi att åtgärden fungerar?**  
Vi testar skyddet både på servicenivå och genom hela kedjan från JWT-filter och säkerhetslager till controller och service. Testerna verifierar både att fel attestant får 403 och att inget förändras i systemet när försöket nekas. Det var en förhållandevis liten ändring för en risk med hög allvarlighetsgrad.

**Vad är fortfarande osäkert?**  
Vi behöver fortfarande verifiera isoleringen mellan olika tenants. Vi har inte heller testat adminrollens bypass-flöde fullt ut eller vad som händer om två attesteringar sker exakt samtidigt.

### R-06 – Säkerhetsrisker i autentiseringen

**Vad var risken?**  
Den tidigare autentiseringslösningen hade två tydliga säkerhetsproblem. SQL-frågor byggdes genom strängkonkatenering, vilket skapade risk för SQL injection, och lösenord lagrades med MD5 i stället för en modern lösenordshash.

**Varför var den viktig?**  
Det här påverkar själva autentiseringen. Om inloggningsflödet kan manipuleras spelar övrig behörighetskontroll mindre roll, eftersom en angripare potentiellt kan få åtkomst som en annan användare.

**Vad gjorde vi åt den?**  
Vi slutade bygga SQL-frågor genom strängkonkatenering och flyttade autentiseringen till JPA. Vi ersatte också MD5 med BCrypt och migrerade befintliga lösenordshashar till den nya lösningen. Det här var också en av anledningarna till att vi gick över till en lagerdelad arkitektur, controller, service, repository, i stället för att bygga SQL-frågor direkt i controllern. Det beslutet finns dokumenterat i en egen ADR.

**Vad behövde vi ändra eller välja bort?**  
Vi valde bort den gamla autentiseringskoden och gick över till en tydligare lagerstruktur med JPA i stället för att försöka säkra den gamla SQL-baserade lösningen.

**Hur vet vi att åtgärden fungerar?**  
Vi har ett integrationstest som körs mot en riktig databas och skickar in typiska SQL injection-försök, till exempel `' OR 1=1 --`. Försöken ska nekas. Samma tester verifierar också att en legitim användare fortfarande kan logga in med rätt lösenord.

**Vad är fortfarande osäkert?**  
Vi saknar fortfarande skydd mot upprepade inloggningsförsök, exempelvis rate limiting. JWT-secret ligger också öppet i konfigurationen i dag och behöver flyttas till säker hantering innan en skarp driftsättning.