# ADR 0004 - Lagerdelad arkitektur med Spring Data JPA

- **Status:** Föreslagen
- **Datum:** 2026-08-27
- **Deltagare:** [fyll i] · drivande: Pontus Ingenius 
- **Berör:** Backend-arkitektur (alla epics)
- **Relaterade issues:** (inga ännu)

## Kontext

v1 har inga lager: varje controller innehåller rå SQL (`JdbcTemplate` med konkatenerade strängar), affärslogik, e-postskick och auditloggning i samma klass, en "Big Ball of Mud" per controller (se `docs/v1/architecture.md`). Det ger bl.a. SQL-injektion (BUG-001) och omöjliggör testning. v2-kraven pekar ut ren, lagerdelad arkitektur.

## Beslut

Vi inför en lagerdelad arkitektur: Controller → Service → Repository, med Spring Data JPA (Hibernate) som dataåtkomst och en migrations-kedja. Controllers anropar bara services. Ingen SQL i controllern. Domänen modelleras som entiteter, inte `Map<String,Object>`.

## Alternativ vi övervägde

- Behålla `JdbcTemplate` men parameterisera: snabbare på kort sikt, men löser inte avsaknaden av lager/domänmodell och missar kursmål om arkitektur.
- Full JPA + lagerdelning: valdes för att adresserar både säkerhet (parameteriserade queries) och kvalitetsmålen. Mer omställning men rätt riktning för v2.

## Konsekvenser

**Positiva**
- Parameteriserade queries eliminerar SQL-injektion.
- Testbart servicelager (unit-test av affärslogik utan HTTP/DB).
- Tydlig struktur -> lättare individuell synlighet per lager.

**Negativa / risker**
- Risk för överarkitektur åtgärd: Inför lager bara där de bär sitt värde, håll det enkelt för MVP.

## Uppföljning

- Ratificera detta som team på PL-passet innan produktion (v3). Verifiera med en CodeQL/SonarQube-scan att inga SQL-injektioner kvarstår.
