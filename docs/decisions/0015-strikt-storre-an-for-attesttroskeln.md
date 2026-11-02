# ADR 0015 — Attest krävs först över tröskeln (strikt `>`)

- **Status:** Accepterad
- **Datum:** 2026-11-02 (dokumenterar ett beslut som låstes i koden 2026-09-21)
- **Deltagare:** Adnan, Pontus Ingenius · drivande: Pontus Ingenius
- **Berör:** Betalningar och attestflöde
- **Relaterade issues:** #43, BUG-006, R-05
- **Relaterat:** [ADR 0003](0003-mvp-scope-och-avgransningar.md), [ADR 0013](0013-avgransning-av-UX-feedback-infor-MVP.md), `docs/kravtolkning.md` (avsnittet om öppna frågor)

## Kontext

En betalning över tröskeln (`payment.approval.no-attestant-threshold`, 5000 kr) kräver attest. Vad som händer på exakt tröskelbeloppet avgör om en betalning går igenom utan attest. `docs/kravtolkning.md` lyfte frågan och krävde en ADR och ett gränsfallstest.

I v1 fanns tröskeln på flera ställen i koden, och värdena gled isär (BUG-006). `docs/backlog.md` kräver därför en enda tröskeldefinition och anger "> tröskel → atteststeg skapas".

Den första versionen av `PaymentService` (#43) krävde attest även på exakt tröskelbelopp, eftersom den använde `< 0` för betalningar utan attest. Det stämde inte med backloggen. 2026-09-21 ändrades villkoret till `<= 0`, och gränsfallen låstes med tester. Beslutet har hittills bara stått i koden och i `docs/ways-of-working.md`.

## Beslut

Vi kräver attest när beloppet är strikt större än tröskeln (`>`). En betalning på exakt tröskelbeloppet genomförs utan attest.

## Alternativ vi övervägde

- **Strikt `>`** - stämmer med backloggen och med formulärets text "Betalningar över 5 000 kr behöver attesteras". Valdes.
- **`>=`, attest även på exakt tröskelbelopp** - lite striktare, eftersom exakt 5000 kr också attesteras. Valdes bort eftersom regeln då inte stämmer med backloggen och formulärets text, och skillnaden bara gäller ett enda belopp.

## Konsekvenser

**Positiva**
- Regeln är entydig och stämmer i backlog, kod, tester och gränssnitt.
- Backend är den enda källan: frontend hämtar tröskeln från backend och avgör inte själv vad som krävs.
- Gränsfallen är låsta: `PaymentServiceTest.amountEqualToThreshold_shouldCompleteImmediately` och `PaymentServiceTest.amountJustOverThreshold_shouldRequireAttestant`.

**Negativa / risker**
- En betalning på exakt 5000 kr genomförs utan attest. → Accepterat: beloppet ligger på den nivå vi redan släpper igenom utan attest.
- Flera betalningar på högst 5000 kr genomförs utan attest, även om summan blir stor. Regeln ser bara en betalning i taget. → Avgränsat i MVP. Audit visar varje betalning, så mönstret går att följa upp i efterhand.
- Regeln är inte bekräftad med kunden i vår dokumentation. → Se uppföljningen.

## Uppföljning

- Bekräfta tröskelvärdet och regeln på exakt belopp med kunden.
- Dubbel attest är byggd med samma strikta regel, se [ADR 0016](0016-dubbel-attest-over-50000.md).
- Omvärdera om kunden vill begränsa summan av flera betalningar under tröskeln.
