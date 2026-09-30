       
    1. CIA, tunn behövs det mer? Tänk CIA där, confidentiality, integrity, availability. Vilka saker kan ni hitta på alla de här olika ställena? Ni behöver ju såklart se över de riskerna som är kvar. 
       Det är ganska mycket som inte är fixat då. Så såklart behöver ju det vara på plats innan man vågar gå live med den här.
       
    2. Authorization versus authentication (Roller) - Läsa på om vertikal, horizontal authentication. 
       
    3. Autoincrementerande ID. Läs om GUID. PRIO. Implementera. /Backend Adnan
       
    4. Accessmatris som listar alla roller, vilka actions de har får lov att göra. Finns det permission escalation. Tid – 2.10 – Lågt – Lista över actions på roller i /docs. -- Breddning. 

    5. Ehm MD5 hashar inte säkert heller. Det kommer ni behöva byta ut till en starkare om ni inte har gjort det redan. - MD5 borttaget, nu använder vi Bcrypt -- LÖST

    6. Problem med SQL injections, lösningen är ju att använda prepared statements – LÖST, (Kolla hur och lägg till här)

    7. unittester i sin CICD pipeline – npm audit. Kolla test CVE, slår mot npm sårbarheter, failar den, failar unittestet.  ← kolla hur vår CICD pipeline är uppbyggd. Säkerhetsgaranti vi lovar. - lågt, breddning
       
    8.  Titta över symmetriska versus asymmetrisk  JWT-nyckel, då kan ni isolera vilken del av systemet som får lov att signera nya JWT. Tänk på hur de kanske behöver roteras över tid. Eh, hur hur kommer det gå till om man byter ut om den blir läckt liksom? Hur kan man rotera den JWT nyckel. - Hög. Prio. / Jonathan. 
    httpOnly i backend. Jwt i variabl 15 min i frontend. Refresh med httpOnly
       
    9. Kolla igenom databastransaktion om ni inte har det redan så att ni har atomic commits genom systemet så att den inte bryter halvvägs. Kolla igenom eh vilken data som spottas tillbaka till klienten. Man vill inte ha fulla stack traces på serverside hos klienten till exempel. - läs på om atomic commits, fulla stack tracers. @transactional. -- LÖST. 