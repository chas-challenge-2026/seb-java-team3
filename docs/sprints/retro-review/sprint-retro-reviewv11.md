# Sprint v11 – Review & Retro

**Datum:** 2026-11-02

## Sprintmål – hur gick det?

Målet var att få klart de viktigaste ändringarna från kundens feedback: tydligt hur man godkänner och avvisar betalningar, och att initiatören ser vad som har hänt med sina betalningar. Dokumentationen skulle stämma med koden inför slutleveransen 4/11.

**Resultat: Uppnått.** All planerad kundfeedback är byggd, och vi hann mer än planerat. Seed-IBAN:erna blev inte fixade i v11, utan i v12.

## Review – vad blev gjort?

| Uppgift | Status | Kommentar | Ansvarig |
| --- | --- | --- | --- |
| Meddelande vid avvisning, sparas med beslutet och syns i historiken | Klar | #244 | Jonathan |
| Endpoint som räknar initiatörens ändrade betalningar | Klar | #236 | Adnan |
| "Sedd"-status för badgen | Klar | #248 | Markus |
| Granska, godkänna och avvisa i attestvyn | Klar | #237 | Pontus |
| Bekräftelse när en betalning skapas | Klar | #239 | Marcus |
| Avvisningsorsak i Mina betalningar | Klar | #240 | Mohammed |
| Badge på Mina betalningar och genväg till attestvyn | Klar | #253, mergad 3/11 | Marcus |
| `test-status.md` uppdaterad | Klar | | Pontus |
| README uppdaterad | Klar | | Team |

Utöver planen kom följande in:

| Uppgift | Kommentar |
| --- | --- |
| R-04: tenant-isolering testad mot Postgres | #238 |
| Samtidiga godkännanden testade mot Postgres | #245 |
| Risktester | #246 |
| Dubbel attest över 50 000 kr | #250, ADR 0016 |
| Admin som administrativ roll och användaradministration | #242, #247, #249, #251, ADR 0017 |

## Vad blev inte klart?

| Uppgift | Varför? | Nästa steg |
| --- | --- | --- |
| Seed-IBAN för fler konton | Lönekonto och Projektkonto har IBAN som inte klarar MOD97 i `V5__dev_seed_data.sql` | Klart i v12 (migrering V15) |

## Retro

### Vad fungerade bra?

- All planerad kundfeedback blev klar, och vi hann dessutom dubbel attest och adminrollen.
- R-04, som flyttats sedan v7, blev äntligen grönt.
- Uppdelningen i backend och frontend med tydliga beroenden (endpoint först, sedan badge) fungerade.

### Vad fungerade mindre bra?

- Åtta PR:er mergades samma dag (2/11). Det gav krockar: två ADR:er fick samma nummer (0015, nu 0017) och två migreringar fick samma nummer (V10, nu V11).
- Mycket blev klart de sista dagarna före leveransen, vilket gav lite tid att testa allt tillsammans.

### Lärdomar till nästa projekt

- Reservera ADR- och migreringsnummer i backloggen när arbetet börjar, så att parallella brancher inte krockar.
- Merga löpande under sprinten i stället för att samla allt till slutet.
