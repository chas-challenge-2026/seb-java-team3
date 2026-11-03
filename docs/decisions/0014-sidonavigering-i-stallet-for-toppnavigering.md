# ADR 0014 — Sidonavigering i stället för toppnavigering

- **Status:** Accepterad
- **Datum:** 2026-10-27
- **Deltagare:** Team SEB 3
- **Berör:** Frontend / UX / Navigering
- **Relaterade issues:** Ej specificerade

## Kontext

Portalen har en fast sidonavigering till vänster (`SideBar`, 16rem bred) med Översikt, Ny betalning, Attestera (med räknare för väntande attester) och Historik eller Mina betalningar, beroende på roll. Längst ner ligger byte av ljust/mörkt läge, Logga ut och användaren. Under 1000px bredd lägger samma komponent sig redan som en horisontell rad överst, så att smala skärmar fungerar.

Frågan har kommit upp om vi i stället ska byta till en toppnavigering på alla skärmstorlekar. Det skulle innebära att layout och navigeringskomponenter görs om och att alla vyer testas igen.

Det som styr beslutet:
- Portalen är ett arbetsverktyg som används vid dator. Användarna växlar ofta mellan att skapa betalningar, attestera och titta i historiken.
- Vyerna (attestkorg, historik, formulär) är som bredast runt 1140px, så det finns plats för en sidomeny bredvid innehållet på vanliga skärmar.
- Utvecklingstiden fram till MVP och demo är begränsad.

## Beslut

Vi behåller sidonavigeringen och bygger inte om till en toppnavigering. Den tid det skulle ta lägger vi i stället på funktioner som ger användaren mer.

## Alternativ vi övervägde

- **Alternativ A – Behålla sidonavigeringen (valt):** Lätt att navigera eftersom alla huvudval syns samtidigt i en lodrät lista med ikon och text, och det aktiva valet är tydligt markerat. Det finns plats för räknare som "Attestera" och för fler menyval utan att layouten behöver ändras. Vi har bredden som krävs, och smala skärmar hanteras redan. Kostar ingen ny utvecklingstid.
- **Alternativ B – Byta till toppnavigering:** Valdes bort. Den ger mer horisontell yta åt innehållet, men vi behöver inte den ytan eftersom vyerna redan har en maxbredd. En toppmeny får snabbt ont om plats när fler val och räknare tillkommer och behöver då en "mer"-meny eller rullgardin, och då blir det svårare att navigera. Ombyggnaden skulle ta tid från funktioner som ger en bättre kundupplevelse.

## Konsekvenser

**Positiva**
- Användaren ser alltid alla huvudval och var den befinner sig, vilket gör det enklare att hitta rätt.
- Nya menyval kan läggas till i listan utan att layouten behöver göras om.
- Ingen tid går åt till att bygga om och testa om layouten, och den kan läggas på funktioner som ger användaren mer.
- Layouten är redan anpassad till olika skärmbredder, så inget nytt arbete behövs för det.

**Negativa / risker**
- Sidomenyn tar 16rem av skärmbredden på dator → Inget problem i dag eftersom vyerna har en maxbredd. Omvärderas om vi får vyer som behöver hela bredden, till exempel breda tabeller.
- Under 1000px ser navigeringen olika ut beroende på skärmbredd (sidomeny eller rad överst) → Samma komponent och samma menyval används, så skillnaden är bara visuell.

## Uppföljning

- Följ upp i användartester och UX-feedback om användarna hittar rätt i menyn och om någon saknar en toppnavigering.
- Omvärdera om antalet menyval växer så att listan blir svår att överblicka, eller om nya vyer behöver hela skärmbredden.
