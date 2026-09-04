# ADR 0007 - Payment och ApprovalStep som JPA-entiteter

- **Status:** Föreslagen
- **Datum:** 2026-09-04
- **Deltagare:** [fyll i ditt namn] · drivande: [fyll i ditt namn]
- **Berör:** Epic 1 (Arkitektur & lager), Epic 3 (Betalning & approval-kedja)
- **Relaterade issues:** #45 (beror på #46 / PR #59)

## Kontext

v2-arkitekturen (ADR 0004) kräver att domänen modelleras som entiteter, inte som `Map<String,Object>` eller rå SQL. `V3__create_payments_and_approval_steps.sql` (från #46) skapar tabellerna `payments` och `approval_steps`, men paketet `se.comerit.seb.domain` var tomt. #45 mappar dessa tabeller mot JPA-entiteter så att kommande arbete (PaymentService i #43, audit-loggning i #44) har ett typsäkert underlag att bygga på.

Två saker i schemat krävde ett aktivt designval:

1. Statuskolumnerna (`payments.status`, `approval_steps.status`) lagras som lowercase-strängar (`'pending_approval'`, `'pending'` osv.), men Java-enum-konstanter är UPPERCASE av konvention.
2. Flera kolumner (`tenant_id`, `from_account_id`, `created_by`, `attestant_id`) är foreign keys mot tabeller (`tenants`, `accounts`, `users`) som ännu inte har egna JPA-entiteter.

## Beslut

**Statusfält:** Vi mappar status som Java-enums (`PaymentStatus`, `ApprovalStepStatus`) via en egen `AttributeConverter` per enum, istället för standardannoteringen `@Enumerated(EnumType.STRING)`. Convertern gör lowercase↔enum-översättning i båda riktningar, så att databasvärdet förblir lowercase (matchar seed-data i V5) samtidigt som Java-koden får ett typsäkert enum att jobba med.

**Relationer utanför scope:** `tenant_id`, `from_account_id`, `created_by` och `attestant_id` mappas som rena `Long`-fält, inte som `@ManyToOne`-relationer. Vi bygger inte `Tenant`/`Account`/`User`-entiteter i denna task.

**Payment ↔ ApprovalStep:** Denna relation är inom scope för #45 och byggs som en riktig bidirectionell `@OneToMany`/`@ManyToOne`, med `Payment` som ägare av listan (`cascade = ALL`, `orphanRemoval = true`) för att en betalning och dess godkännandekedja alltid ska hänga ihop.

**Optimistisk låsning (`@Version`):** Utelämnad i denna task. Kravet finns i projektplanen (Epic 3: "atomär balans, optimistisk lås") men hör till PaymentService-arbetet, inte entitetsmappningen mot befintligt schema, och kräver dessutom en ny Flyway-migration (`version`-kolumn saknas i V3).

## Alternativ vi övervägde

- **`@Enumerated(EnumType.STRING)` rakt av:** enklare, men hade skrivit enum-namnet exakt (`"PENDING_APPROVAL"`) till databasen — matchar inte lowercase-konventionen i schemat/seed-data. Avvisat.
- **Bygga Tenant/Account/User-entiteter nu, för att få riktiga relationer överallt:** hade gett en "renare" modell, men är utanför scope för #45 och adresseras bättre som egna tasks med egen granskning. Avvisat, dokumenterat som medveten avgränsning istället.
- **Lägga till `@Version` redan nu, "för säkerhets skull":** kräver en migration som inte finns än och tillhör ett annat epic/ägarskap (Java-2). Avvisat för att undvika att gripa in i någon annans arbetsområde utan avstämning.

## Konsekvenser

**Positiva**
- Statuskonvertering är centraliserad i två små, testbara klasser — inga risker för att någon skriver fel case manuellt någon annanstans i koden.
- Payment/ApprovalStep-relationen är komplett och redo att användas direkt av PaymentService (#43).
- Tydlig, dokumenterad avgränsning gör det enkelt för nästa person (t.ex. den som bygger Tenant-entiteten) att veta exakt vad som behöver refaktoreras och varför.

**Negativa / risker**
- Tenant/Account/User som rått `Long`-id ger ingen kompileringstids-koppling mot dessa tabeller — ett felaktigt id upptäcks först vid körning (FK-constraint i databasen), inte av Java-kompilatorn. Accepterat för nu, blir bättre när entiteterna finns.
- Ingen `@Version` innebär att samtidiga godkännanden i värsta fall kan skriva över varandra tills Epic 3 löser detta. Ingen risk i #45 eftersom inget skriver till entiteterna än.

## Uppföljning

- När `Tenant`/`Account`/`User`-entiteter finns: byt ut `Long`-fälten mot `@ManyToOne`-relationer i `Payment`/`ApprovalStep`.
- När Epic 3 (PaymentService) påbörjas: lägg till `version`-kolumn via ny Flyway-migration och `@Version`-fält på `Payment` (och ev. `Account` för saldot).
- Verifierat lokalt: `mvn compile` (BUILD SUCCESS) samt `docker-compose up --build` från tom volym — alla 5 migrationer applicerades och `EntityManagerFactory` initierades utan Hibernate-fel mot det verkliga schemat.