# ADR 0005 — Feature Based struktur frontend

- **Status:** Accepterad
- **Datum:** 2026-08-31
- **Deltagare:** Marcus · drivande: Pontus Ingenius
- **Berör:** epic/område
- **Relaterade issues:** #XX, #YY

## Kontext

Beslut om vilken struktur vi ska använda i frontend. Rekommendation är att köra feature-based

## Beslut

Vi väljer feature based för att hålla strukturen organiserad och enkel för många att jobba i.

## Alternativ vi övervägde

- **Alternativ A** - Mappstruktur valdes bort för det blir ofta oorganiserat. Många mappar att navigera i istället för att ha allt på samma plats i en feature

## Konsekvenser

**Positiva**

- Enkelt
- Organiserat
- Mindre hoppande mellan mappar

**Negativa / risker**

- Gemensam kod kan bli duplicerad mellan features om det är otydligt vad som ska ligga gemensamt.
- Strukturen kan bli inkonsekvent om olika features organiseras på olika sätt eller om det saknas tydliga riktlinjer.

## Uppföljning

- Omvärdera strukturen efter att vi har byggt 3–5 större features eller efter cirka 2–3 månader. Kontrollera om utvecklare enkelt hittar relevant kod, om gemensam kod dupliceras och om strukturen används konsekvent mellan olika features. Vid återkommande problem uppdaterar vi riktlinjerna eller omprövar beslutet.
