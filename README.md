# SEB Företagsbetalningar (Java)

Ett betalningsportal-system för SEB:s SME-kunder. Initiator skapar betalningar, attestanter godkänner, allt loggas i en granskningslogg.

**Detta är v1 — ett avsiktligt spaghetti-system. Er uppgift är att bygga v2.**

---

## Snabbstart

```bash
git clone <repo-url>
cd ChasChallenge
git checkout 5-seb-java

cd infra
docker compose up --build
```

Öppna [http://localhost:8084](http://localhost:8084)

| Roll | E-post | Lösenord |
|------|--------|---------|
| Initiator | lisa@malmobygg.se | password123 |
| Attestant | johan@malmobygg.se | password123 |
| Admin | sara@malmobygg.se | password123 |

---

## Mappstruktur

```
ChasChallenge/
├── backend/
│   └── SebPortal/                — Spring Boot 2.7 (Spring MVC + Thymeleaf)
│       ├── src/main/java/se/comerit/seb/
│       │   ├── SebPortalApplication.java
│       │   └── controller/       — En controller per sida, all logik här
│       ├── src/main/resources/
│       │   ├── templates/        — Thymeleaf-vyer
│       │   ├── static/           — Statiska filer
│       │   └── application.properties
│       ├── Dockerfile
│       └── pom.xml
├── docs/
│   ├── architecture.md           — Hur v1 är byggd
│   ├── known-bugs.md             — Lista med 12 kända buggar (avsiktliga)
│   ├── README-pain-points.md     — Vad som fungerar vs. går sönder
│   └── v2-targets.md             — Vad ni ska bygga
├── frontend/                     — Tom — er v2 React-app placeras här
├── infra/
│   ├── docker-compose.yml
│   └── seed.sql                  — Schema + testdata
├── native/
│   └── README.md                 — Spec för C/C++ native moduler (v2)
└── shared/
    └── example-batch.csv         — Exempelfil för batchuppladdning
```

---

## Kända problem

Se [docs/known-bugs.md](docs/known-bugs.md) för fullständig lista. De allvarligaste:

- **SQL-injektion** i inloggningsformuläret (BUG-001)
- **MD5-lösenord** — trivialt att knäcka (BUG-002)
- **IBAN-validering** kontrollerar inte kontrollsiffror (BUG-003)
- **CSV-parser** bryter på kommatecken i fält (BUG-004)
- **Attesttröskel** definierad med olika värden i två controllers (BUG-006)
- **Notifieringar** misslyckas tyst, ingen retry (BUG-007)
- **Dubbel audit-logg** — DB + fil, inkonsekvent (BUG-008)

---

## Vad ska ni bygga

Se [docs/v2-targets.md](docs/v2-targets.md) för fullständiga krav.

Kortversion:
- Spring Boot **REST API** (ersätter Spring MVC + Thymeleaf)
- **React 18** frontend
- **Spring Data JPA** + Flyway/Liquibase (ersätter rå JdbcTemplate)
- Proper **autentisering** (Spring Security, JWT, BCrypt)
- **Notifieringskö** med retry
- **IBAN MOD97**-validering
- C/C++ **native moduler** (CSV, IBAN, audit-signering)

---

## Teknisk stack (v1)

- Spring Boot 2.7 (Java)
- Spring MVC + Thymeleaf
- JdbcTemplate (rå SQL)
- PostgreSQL 12
- Docker Compose
- Bootstrap 5 (CDN)
