# Accessmatris

Vilka roller som får göra vad i systemet, och om det går att eskalera sin behörighet. Matrisen är kontrollerad mot koden 2026-11-02. Uppdatera den när en endpoint eller roll ändras.

## Kort sammanfattning

- Tre roller finns: `INITIATOR`, `ATTESTANT` och `ADMIN`. Varje användare har en roll och tillhör ett företag.
- Backend avgör all åtkomst. Frontendens spärrar styr bara vad som visas.
- Roll, användar-id och företag läses från den signerade JWT:n, aldrig från anropet.
- **Ett eskaleringshål är öppet:** en admin kan skapa en betalning över tröskeln och godkänna den själv, se [Permission escalation](#permission-escalation).

## Roller

| Roll | Uppgift | Får inte |
|---|---|---|
| `INITIATOR` | Skapar betalningar och följer sina egna. | Attestera eller läsa auditloggen. |
| `ATTESTANT` | Godkänner och avvisar steg som är tilldelade rollinnehavaren, och läser betalningshändelser. | Skapa betalningar eller hantera andras steg. |
| `ADMIN` | Har initiatörens rättigheter och kan dessutom hantera alla steg och läsa hela auditloggen inom företaget. | Se eller ändra andra företags data. |

## API

Allt under `/api/**` kräver giltig JWT, utom de tre öppna auth-endpointsen. Saknad eller ogiltig token ger 401, och fel roll ger 403.

**Teckenförklaring**

- **Nej:** nekas med 403.
- **Tilldelade:** bara steg som är tilldelade den inloggade attestanten.
- **Egna:** bara det som den inloggade användaren själv har skapat.
- **Företaget:** allt inom det egna företaget.

### Inloggning och session

| Åtgärd | Endpoint | Ej inloggad | Initiatör | Attestant | Admin | Teststöd |
|---|---|---|---|---|---|---|
| Logga in | `POST /api/auth/login` | Ja | Ja | Ja | Ja | `AuthServiceInjectionTest`, `AuthControllerRefreshTest` |
| Förnya session | `POST /api/auth/refresh` | Kräver refresh-cookie och `X-Requested-With` | Samma | Samma | Samma | `AuthControllerRefreshTest` |
| Logga ut | `POST /api/auth/logout` | Kräver `X-Requested-With` | Samma | Samma | Samma | `AuthControllerRefreshTest` |
| Läsa egen användare | `GET /api/auth/me` | Nej (401) | Ja | Ja | Ja | `AuthControllerRefreshTest` |

### Konton och betalningar

| Åtgärd | Endpoint | Initiatör | Attestant | Admin | Teststöd |
|---|---|---|---|---|---|
| Läsa konton och saldon | `GET /api/accounts` | Företaget | Företaget | Företaget | `AccountControllerSecurityTest`, `TenantIsolationIntegrationTest` |
| Läsa tröskelvärdet | `GET /api/payments/config` | Ja | Nej | Ja | **Saknas** |
| Skapa betalning | `POST /api/payments` | Från företagets konton | Nej | Från företagets konton | `RoleAuthorizationTest`, `TenantIsolationIntegrationTest` |
| Läsa mina betalningar | `GET /api/my-payments` | Egna | Nej | Egna | `RoleAuthorizationTest`, `AuditServiceMyPaymentsTest` |
| Räkna mina ändrade betalningar | `GET /api/payments/changed-count` | Egna | Nej | Egna | **Saknas** |

### Attest

| Åtgärd | Endpoint | Initiatör | Attestant | Admin | Teststöd |
|---|---|---|---|---|---|
| Läsa attestkorgen | `GET /api/approvals` | Nej | Tilldelade | Företaget | `RoleAuthorizationTest`, `TenantIsolationIntegrationTest` |
| Räkna väntande steg | `GET /api/approvals/count` | Nej | Tilldelade | Företaget | `RoleAuthorizationTest`, `ApprovalApiControllerCountTest` |
| Godkänna | `POST /api/approvals/{stepId}/approve` | Nej | Tilldelade | Företaget, även egna betalningar | `RoleAuthorizationTest`, `ApprovalApiControllerOwnershipTest` |
| Avvisa | `POST /api/approvals/{stepId}/reject` | Nej | Tilldelade | Företaget, även egna betalningar | `RoleAuthorizationTest`, `ApprovalApiControllerOwnershipTest` |

### Audit

| Åtgärd | Endpoint | Initiatör | Attestant | Admin | Teststöd |
|---|---|---|---|---|---|
| Läsa auditloggen (senaste 200) | `GET /api/audit` | Nej | Företagets skapade, godkända och avvisade betalningar | Företaget | `RoleAuthorizationTest`, `TenantIsolationIntegrationTest` |
| Läsa en betalnings tidslinje | `GET /api/payments/{paymentId}/audit` | Nej | Företagets skapade, godkända och avvisade betalningar | Företaget | `RoleAuthorizationTest` (nekad initiatör saknar test), `TenantIsolationIntegrationTest` |

### Utanför `/api`

| Åtgärd | Sökväg | Vem | Teststöd |
|---|---|---|---|
| Hälsokontroll, utan detaljer | `GET /actuator/health` | Alla | `AuditSignerWiringTest` |
| Frontendens filer och routes | `/`, `/assets/**` och frontendens routes | Alla (data kräver fortfarande JWT) | `SpaFallbackControllerTest` |

### Åtgärder som inte finns

Det finns ingen endpoint som låter någon roll göra följande:

- skapa användare eller ändra roller
- ändra eller ta bort en betalning, ett atteststeg eller en auditrad
- ändra saldo eller tröskelvärde direkt

## Frontendens vyer

Spärrarna i `frontend/src/router.tsx` styr bara navigeringen. Skyddet ligger i backend.

| Vy | Route | Ej inloggad | Initiatör | Attestant | Admin |
|---|---|---|---|---|---|
| Inloggning | `/login` | Ja | Ja | Ja | Ja |
| Översikt | `/` | Nej | Ja | Ja | Ja |
| Testsida för UI-komponenter | `/uitest` | Nej | Ja | Ja | Ja |
| Ny betalning | `/payments/new` | Nej | Ja | Nej | Ja |
| Mina betalningar | `/my-payments` | Nej | Ja | Nej | Ja |
| Attestkorg | `/attest` | Nej | Nej | Ja | Ja |
| Auditlogg | `/audit` | Nej | Nej | Ja | Ja |

## Permission escalation

**Vertikal eskalering** betyder att få en högre roll eller dess rättigheter. **Horisontell eskalering** betyder att komma åt en annan användares eller ett annat företags data med samma roll.

### Vertikal

| Försök | Resultat | Bevis |
|---|---|---|
| **Admin godkänner sin egen betalning** | **Öppet hål.** Betalningen genomförs utan en andra person (R-03). | Provkört 2026-11-02 mot Postgres: admin skapade en betalning på 6 000 kr och godkände den, och status blev `completed`. |
| Initiatör attesterar, eller attestant skapar en betalning | Nekas med 403. | `RoleAuthorizationTest` |
| Förfalska en token med högre roll | Nekas. Nyckeln finns varken i repot eller i konfigurationen (R-17). | `JwtServiceTest` |
| Ändra sin egen roll | Går inte. Det finns ingen sådan endpoint. | Genomgång av alla controllers |
| Använda en gammal roll efter ett rollbyte | Går i upp till 15 minuter, tills access-token går ut. Accepterat i ADR 0012. | `RefreshTokenServiceTest` |

### Horisontell

| Försök | Resultat | Bevis |
|---|---|---|
| Attestant hanterar en annan attestants steg | Nekas med 403, och inget ändras (R-03). | `ApprovalApiControllerOwnershipTest` |
| Komma åt ett annat företags data | Syns inte. Ett annat företags steg ger samma 400 som ett steg som inte finns (R-04). | `TenantIsolationIntegrationTest` |
| Skicka ett annat företag eller en annan skapare i anropet | Ignoreras. Värdena hämtas från JWT:n. | `TenantIsolationIntegrationTest`, `NewPaymentController` |
| Betala från ett annat företags konto | Nekas med 400, både när betalningen skapas och när den godkänns (R-04). | `PaymentServiceTest`, `ApprovalServiceTest`, `TenantIsolationIntegrationTest` |
| Läsa en annan initiatörs betalningar | Går inte via mina betalningar, som filtrerar på skaparen. | `AuditServiceMyPaymentsTest` |

## Öppna punkter

1. **Stoppa att admin godkänner sin egen betalning (R-03).** Admins rätt att godkänna andras steg är avsiktlig och testad, men en betalning bör inte kunna godkännas av den som skapade den.
2. **Lägg till rolltester** för `/api/payments/config` och `/api/payments/changed-count`, och ett nekat-test för initiatören på tidslinjen.
3. **Ta bort `approve(stepId, actorId)` och `reject(stepId, actorId, comment)` i `ApprovalService`.** De saknar företags- och ägarkontroll för inloggad användare. Ingen controller anropar dem, så de går inte att nå via API:t.
4. **Ta bort `/uitest`** från produktionsbygget. Alla inloggade kan öppna testsidan.
5. **Ta ställning till attestantens läsrätt.** En attestant kan läsa alla företagets konton, saldon och betalningshändelser, inte bara sina egna steg. Det kan vara rimligt för uppgiften, men det är bredare än minsta möjliga behörighet.
