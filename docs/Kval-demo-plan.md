Vi har 10 min att presentera.

1. Demo manus.
- Intro. Ram: Prata om kundens problem i hans ord. samt vår tolkning? Övergång med Så här tar vår lösning bort det. 1 min - Pontus
- Kärnflödet. Live MVP flöde.  1:00-5:30 min.  ( Pontus, Marcus )
- Tygghet. 5:30-7:30 min t.ex. ett företag ser aldrig ett annat företags data, saldot dras bara en gång, lösenord lagras med BCrypt. refresh token. Visa att E2E-testet går grönt på cirka 7 sekunder. Vem? Jonathan, Markus, Mohammed
- Teknikval: 7:30-9:30 t.ex. Spring Boot, React, Postgres i Docker, IBAN-validering i C. Kort om varför, inte hur: Adnan och Samuel. 
- Avslut. 9:30-10:00 min- Vad vi avgränsat och varför. 

2. Logga in som initiator, skapa en betalning över tröskel (IBAN valideras vid inmatning), attestkedjan triggas, attestanten ser badge, godkänner, saldo dras atomärt, och hela händelsen syns i auditvyn med vem, vad och när.

3. Spela in en video som fallback, samt att vi har skärmdumpar av nyckelvyer så att vi kan växla till inspelning utan avbrott. Lokal miljö som andra reserv.
4. Avgränsningar, bevisad kärna är det kunden själv rankar högst, framför fler halvfärdiga flöden
- batch-CSV-upload - På väg, håller på att byggas
- dubbel attest / attestant 2
- multi-account-dashboard i full bredd
- compliance-export
- native audit-signering och native CSV (byggs fristående parallellt, integreras när kärnan står)
- valutaomvandling.
5. Frågor:
- Hur vet jag att rätt person godkänt, och att ingen kan godkänna i deras namn?
- Vad händer om något går fel mitt i en betalning?
- Varför kan jag inte ladda upp en hel fil med betalningar?
- Kan ett företag se ett annat företags betalningar?
- Kan pengarna dras två gånger om två personer godkänner samtidigt?
- Hur säkra är inloggning och lösenord?