# ADR 0002 — Bevara v1-dokumentation som referens

- **Status:** Förslagen
- **Datum:** 2026-08-26
- **Deltagare:** drivande: Pontus Ingenius 
- **Berör:** Dokumentationsstruktur / repo
- **Relaterade issues:** #

## Kontext

Vi tar över en v1-kodbas med medföljande dokument: `architecture.md`, `known-bugs.md`, `README-pain-points.md`, `v2-targets.md` samt root-`README.md`. Frågan uppstod om dessa borde raderas när v2-arbetet drar igång, eftersom de beskriver det gamla systemet.

## Beslut

Vi **behåller** v1-dokumenten som referens och samlar dem under `docs/v1/`. Vi raderar dem inte. Root-`README.md` skrivs på sikt om till en v2-README (installation, körning, testning för vår lösning), men den gamla versionen bevaras.

## Alternativ vi övervägde

- **Radera v1-docs** valdes bort. De är i praktiken vår kravspec och "före-bild" för projektet
- **Lämna dem oorganiserade i roten** — valdes bort blir rörigt och otydligt vad som är ärvt vs nytt.
- **Samla under `docs/v1/`** — valt: tydlig separation mellan ärvd bild och vårt nya arbete.

## Konsekvenser

**Positiva**
- `known-bugs.md` och `v2-targets.md` fungerar som checklista för refaktoreringen.
- Vi kan visa PL, CTO och kund exakt vad som förbättrats och varför.

**Negativa / risker**
- Dubbla README-filer kan förvirra → Åtgärd: `docs/README.md`-kartan pekar tydligt ut vad som är v1-referens vs aktuellt.

## Uppföljning

- När v2-README:n är klar: kontrollera att den är den som gäller i roten och att v1-varianten ligger tydligt märkt i `docs/v1/`.
