# ADR 0010 – AI-stöd för delar av backendkoden

* **Status:** Accepterad
* **Datum:** 2026-09-15
* **Deltagare:** Marcus Johansson, Jonathan Isaksson · drivande: Pontus Ingenius
* **Berör:** Backendkod

## Kontext

Projektet har hamnat efter den ursprungliga tidsplanen på grund av bland annat frånvaro och låg närvaro inom gruppen. Det återstår därför mer utvecklingsarbete än vad gruppen bedömer att vi hinner genomföra manuellt inom den återstående projekttiden.

För att öka möjligheten att leverera en fungerande och genomförbar MVP behöver vi effektivisera utvecklingen av delar av backend.

Samtidigt behöver gruppen fortfarande kunna förstå, förklara, felsöka och vidareutveckla all kod som ingår i projektet, även när AI har använts som stöd vid implementationen.

## Beslut

Vi väljer att använda AI som stöd för att generera och implementera delar av backendkoden där detta kan påskynda utvecklingen och hjälpa projektet att komma ikapp tidsplanen.

AI-genererad kod får inte inkluderas okontrollerat i projektet. För kod där AI har stått för en större del av implementationen krävs **två code reviews av gruppmedlemmar** innan koden betraktas som godkänd.

Syftet med code reviews är att säkerställa att gruppen:

* förstår hur implementationen fungerar,
* kan förklara viktiga design- och kodbeslut,
* kan identifiera eventuella fel eller säkerhetsproblem,
* kan underhålla och vidareutveckla koden utan att vara beroende av AI.

AI används därmed som ett utvecklingsverktyg och inte som ersättning för gruppens ansvar för kodens kvalitet och funktion.

## Alternativ vi övervägde

* **Alternativ A – Använda AI som stöd för delar av backend.**
  Detta ger möjlighet att öka utvecklingstakten och förbättra chanserna att leverera den planerade MVP:n inom projekttiden.

* **Alternativ B – Fortsätta utveckla backend utan AI-genererad kod.**
  Detta skulle ge gruppen större direkt kontroll över all implementation, men innebär en högre risk att vi inte hinner färdigställa den planerade MVP:n inom projektets tidsram.

* **Alternativ C – Minska omfattningen av MVP:n.**
  Ett ytterligare alternativ hade varit att ta bort eller förenkla funktionalitet för att anpassa projektets omfattning efter den återstående tiden. Detta bedömdes kunna påverka MVP:ns funktionalitet mer än att använda AI som utvecklingsstöd.

## Konsekvenser

### Positiva

* Utvecklingen av backend kan gå snabbare.
* Gruppen får större möjlighet att komma ikapp den befintliga tidsplanen.
* Sannolikheten ökar att en fungerande MVP kan färdigställas inom projekttiden.
* AI kan hjälpa till med exempelvis boilerplate, implementation av tydligt definierade funktioner och förslag på lösningar.
* Code review-processen kan samtidigt bidra till ökad kodförståelse inom gruppen.

### Negativa / risker

* **AI kan generera felaktig eller olämplig kod.**
Åtgärd: All AI-genererad kod granskas och testas innan den godkänns.

* **Gruppen kan få sämre förståelse för kod som genererats av AI.**
Åtgärd: Minst två code reviews krävs för större AI-genererade implementationer.

* **AI kan föreslå lösningar som inte följer projektets arkitektur eller kodstandard.**
Åtgärd: Koden anpassas till projektets befintliga struktur och conventions under code review.

* **Det finns risk för ett för stort beroende av AI under fortsatt utveckling och felsökning.**
Åtgärd: Gruppen ska kunna förklara och modifiera den kod som accepteras in i projektet.

* **AI-genererad kod kan innehålla säkerhetsproblem eller edge cases som inte är uppenbara direkt.**
Åtgärd: Relevant testning och manuell granskning ska genomföras innan merge.

## Uppföljning

Beslutet följs upp under resterande utveckling av MVP:n.

Vi bedömer beslutet som lyckat om:

* AI-stödet bidrar till att planerad backendfunktionalitet för MVP:n blir färdig inom projekttiden,
* AI-genererad kod klarar projektets tester,
* kod som tagits fram med AI genomgår två code reviews innan den godkänns,
* gruppmedlemmarna kan förklara och vidareutveckla implementationerna utan att vara beroende av den ursprungliga AI-genereringen.

Beslutet omvärderas om AI-genererad kod leder till betydande mängder buggar, om code reviews tar mer tid än vad AI-användningen sparar eller om gruppen bedömer att kodförståelsen blir otillräcklig.
