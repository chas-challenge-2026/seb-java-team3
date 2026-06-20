# Arkitektur — SEB Företagsbetalningar v1 (Java)

## Översikt

SEB Företagsbetalningar är en server-renderad monolit byggd med Spring Boot 2.7 (Spring MVC + Thymeleaf). Applikationen hanterar betalningsflöden för SME-kunder: en initiator skapar betalningar, attestanter godkänner dem, och allt loggas (delvis) i en granskningslogg.

```
┌─────────────────────────────────────────────────────┐
│              Browser (ingen SPA-ram)                │
└───────────────────┬─────────────────────────────────┘
                    │ HTTP (port 8084)
┌───────────────────▼─────────────────────────────────┐
│        Spring Boot 2.7 (Spring MVC + Thymeleaf)     │
│                                                     │
│  controller/                                        │
│  ├── AuthController       — Inloggning (MD5, SQLi)  │
│  ├── DashboardController  — Kontoöversikt           │
│  ├── PaymentController    — Skapa betalning         │
│  ├── ApprovalController   — Attestkorg (IDOR)       │
│  ├── BatchController      — CSV-import (naiv split) │
│  └── AuditController      — Granskningslogg (delvis)│
│                                                     │
│  templates/  — Thymeleaf-vyer (en per controller)  │
└───────────────────┬─────────────────────────────────┘
                    │ JdbcTemplate (rå SQL direkt i controllers)
┌───────────────────▼─────────────────────────────────┐
│              PostgreSQL 12                          │
│  Tabeller: tenants, users, accounts, payments,      │
│  approval_steps, audit_entries                      │
└─────────────────────────────────────────────────────┘

Sidoeffekter (ej i diagrammet):
- /tmp/audit.log — textfil (FileWriter), skrivs parallellt med DB
- SMTP-anrop (JavaMailSender) — direkt i controllers
```

## Lagerstacken (eller bristen på den)

Det finns **inga lager**. Varje controller innehåller:

- Databas-queries (`JdbcTemplate` med konkatenerad SQL direkt)
- Affärslogik (tröskelkontroller, statusövergångar)
- E-postskick (inline `JavaMailSender`)
- Auditloggning (till fil OCH databas, inkonsekvent)

Detta gör varje controller till en liten "Big Ball of Mud". Det finns inga `@Service`-klasser, inga `@Repository`-klasser och inga domänmodeller — alla rader hanteras som `Map<String, Object>` rakt från `JdbcTemplate.queryForList`.

## Databasåtkomst

Används: `JdbcTemplate` (rå SQL, ingen ORM). Inga repositories, inga entiteter, inga migrations. SQL-strängar byggs med stränkonkatenering (därav BUG-001 SQL-injektion).

Uppkopplingsuppgifterna finns på flera ställen:
1. `application.properties` — primär källa (`spring.datasource.*`)
2. Hårdkodad fallback-sträng i varje controller (`Host=localhost;...`) — duplicerad men oanvänd för faktisk anslutning, ren teknisk skuld
3. `docker-compose.yml` — miljövariabler som överskriver punkt 1

## Autentisering

Session-baserad. `HttpSession` lagrar `userId`, `role`, `name`, `tenantId`. Lösenord hashas med MD5 (`MessageDigest`, kryptografiskt trasigt). Det finns ingen Spring Security.

Rollkontroll sker via `equals("attestant")`-strängjämförelse — ingen policy, inga authorities, inga `@PreAuthorize`.

## Notifieringar

`JavaMailSender` injiceras och anropas direkt i `PaymentController` och `ApprovalController`. Undantag sväljs tyst i `catch (Exception e) {}`. Ingen kö, ingen retry, ingen loggning av misslyckade försök.

## Deployment

Docker Compose med två tjänster: `db` (PostgreSQL 12) och `app` (Spring Boot 2.7, byggd med Maven). Port 8084. Databas initieras via `seed.sql`.
