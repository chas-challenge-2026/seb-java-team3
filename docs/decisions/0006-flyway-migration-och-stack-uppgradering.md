# ADR 0006 — Flyway-migration för payments/approval_steps + uppgradering till Java 21 / Spring Boot 3.3.4

- **Status:** Accepterad
- **Datum:** 2026-09-03
- **Deltagare:** AdnanZasella · drivande: AdnanZasella
- **Berör:** Betalning & Godkännandekedja
- **Relaterade issues:** #46

## Kontext

Databasschemat skapades tidigare manuellt via `seed.sql`, mountad som Postgres init-script i `docker-compose.yml`. Detta gav ingen versionshantering, ingen spårbarhet över hur schemat vuxit fram, och skulle krocka med Flyway (dubbelskapande av tabeller) så fort Flyway infördes för #46.

Samtidigt beslutade gruppen att projektet ska köra Java 21 och Spring Boot 3.3.4, vilket vid implementation visade sig kräva flera följdändringar (Jakarta-namespace, Flyway-databasmodul) eftersom v1-koden och beroendena var byggda mot Java 11 / Spring Boot 2.7.18.

## Beslut

Vi flyttar hela databasschemat (`tenants`, `users`, `accounts`, `payments`, `approval_steps`, `audit_entries`) från `seed.sql` till fem versionerade Flyway-migrationer (`V1`–`V5`), och uppgraderar samtidigt stacken till Java 21 / Spring Boot 3.3.4 eftersom de två arbetena visade sig vara tekniskt beroende av varandra under implementation.

## Alternativ vi övervägde

- **Behålla `seed.sql` och bara lägga till `payments`/`approval_steps` i Flyway** – valdes bort eftersom det hade lämnat schemat splittrat på två olika mekanismer (delvis Postgres-init, delvis Flyway), vilket motverkar hela poängen med versionerad, spårbar databashantering.
- **Uppgradera Java/Spring Boot i en separat, senare PR** – övervägdes för att hålla denna PR mindre, men valdes bort eftersom gruppens beslut om Java 21/Spring Boot 3.3.4 redan var tget och att vänta hade inneburit att bygga vidare på en stack som ändå skulle bytas ut inom kort.

## Konsekvenser

**Positiva**
- Hela databasschemat är nu versionerat och reproducerbart – `docker-compose up` bygger identisk databas för alla i gruppen från scratch.
- Foreign keys (t.ex. `approval_steps.payment_id → payments.id`) garanterar dataintegritet på databasnivå, i linje med kundens krav på pålitliga godkännandekedjor.
- Två kritiska säkerhetssårbarheter (CVE-2024-1597 i Postgres-drivern, CVSS 10.0; CVE-2026-40477/40478 i Thymeleaf, CVSS 9.0) upptäcktes och åtgärdades som en direkt följd av arbetet.

**Negativa / risker**
- Stor, sammanslagen commit (dependencies + stack-uppgradering + Jakarta-migrering) eftersom mellanliggande tillstånd inte kompilerade var för sig → framtida ändringar bör committas i mindre, fungerande steg där det är möjligt.
- `audit_entries.user_id` saknar foreign key mot `users` (ärvt från `seed.sql`, medvetet behållet) → risk att en felaktig `user_id` skrivs utan att databasen stoppar det. Åtgärd: applikationslogiken (framtida audit-service) måste själv validera `user_id` innan skrivning.
- Inga `CHECK`-constraints på `payments.status`/`amount` → databasen tillåter tekniskt ogiltiga värden. Åtgärd: valideras i `PaymentService.createPayment` (#43).
- `password_md5` kvarstår som hash-algoritm (kursens krav är BCrypt) → känd brist i v1, ej åtgärdad här eftersom det tillhör autentiseringsarbetet, inte schemat.

## Uppföljning

Verifierat genom `docker-compose down -v && docker-compose up --build`: alla 5 migrationer applicerades i rätt ordning och appen startade felfritt (se PR #46 för fullständig logg). Omvärderas om #43/#45 visar att schemat behöver justeras (t.ex. om `CHECK`-constraints eller ändrad `status`-hantering blir nödvändiga).