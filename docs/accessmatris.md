# Accessmatris

Vilka roller som får göra vad i systemet, och om det går att eskalera sin behörighet. Matrisen är kontrollerad mot koden 2026-11-03. Adminrollens delar är uppdaterade i #242 (ADR 0017). Uppdatera den när en endpoint eller roll ändras.

## Kort sammanfattning

- Tre roller finns: `INITIATOR`, `ATTESTANT` och `ADMIN`. Varje användare har en roll och tillhör ett företag.
- Backend avgör all åtkomst. Frontendens spärrar styr bara vad som visas.
- Roll, användar-id och företag läses från den signerade JWT:n, aldrig från anropet.
- **Eskaleringshålet där en admin kunde godkänna sin egen betalning är stängt** (R-03, ADR 0017): admin kan varken skapa, godkänna eller avvisa betalningar, och den som skapat en betalning kan aldrig själv godkänna eller avvisa den, oavsett roll, se [Permission escalation](#permission-escalation). Betalningar över 50 000 kr kräver dessutom två olika attestanter (ADR 0016).

## Roller

| Roll | Uppgift | Får inte |
|---|---|---|
| `INITIATOR` | Skapar betalningar och följer sina egna. | Attestera eller läsa auditloggen. |
| `ATTESTANT` | Godkänner och avvisar steg som är tilldelade rollinnehavaren, aldrig en betalning hen själv skapat, och läser betalningshändelser. | Skapa betalningar eller hantera andras steg. |
| `ADMIN` | Administrativ roll (ADR 0017): läser hela auditloggen, tidslinjen, attestkorgen och konton inom företaget, och hanterar användare och roller. | Skapa, godkänna eller avvisa betalningar, och se eller ändra andra företags data. |

## API

Allt under `/api/**` kräver giltig JWT, utom de tre öppna auth-endpointsen. Saknad eller ogiltig token ger 401, och fel roll ger 403.

**Teckenförklaring**

- **Nej:** nekas med 403.
- **Tilldelade:** bara steg som är tilldelade den inloggade attestanten.
- **Egna:** bara det som den inloggade användaren själv har skapat.
- **Företaget:** allt inom det egna företaget.
- **På tur:** vid dubbel attest gäller listor och beslut bara steget som är på tur. Steg 2 syns och kan hanteras först när steg 1 är godkänt.

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
| Läsa tröskelvärdena | `GET /api/payments/config` | Ja | Nej | Nej | `DualApprovalIntegrationTest`, `RoleAuthorizationTest` (nekad admin och attestant) |
| Skapa betalning | `POST /api/payments` | Från företagets konton | Nej | Nej | `RoleAuthorizationTest`, `TenantIsolationIntegrationTest` |
| Läsa mina betalningar | `GET /api/my-payments` | Egna | Nej | Nej | `RoleAuthorizationTest`, `AuditServiceMyPaymentsTest` |
| Räkna mina ändrade betalningar | `GET /api/payments/changed-count` | Egna | Nej | Nej | `RoleAuthorizationTest` |
| Markera mina ändrade betalningar som sedda | `POST /api/payments/changed-count/seen` | Egna | Nej | Nej | `RoleAuthorizationTest` |

### Attest

| Åtgärd | Endpoint | Initiatör | Attestant | Admin | Teststöd |
|---|---|---|---|---|---|
| Läsa attestkorgen | `GET /api/approvals` | Nej | Tilldelade, på tur | Företaget, på tur, bara läsa (översikt) | `RoleAuthorizationTest`, `TenantIsolationIntegrationTest`, `DualApprovalIntegrationTest` |
| Räkna väntande steg | `GET /api/approvals/count` | Nej | Tilldelade, på tur | Som attestkorgen | `RoleAuthorizationTest`, `ApprovalApiControllerCountTest`, `DualApprovalIntegrationTest` |
| Godkänna | `POST /api/approvals/{stepId}/approve` | Nej | Tilldelade, på tur, aldrig sin egen betalning. Vid dubbel attest högst ett av stegen | Nej | `RoleAuthorizationTest`, `ApprovalApiControllerOwnershipTest`, `DualApprovalIntegrationTest` |
| Avvisa | `POST /api/approvals/{stepId}/reject` | Nej | Tilldelade, på tur, aldrig sin egen betalning | Nej | `RoleAuthorizationTest`, `ApprovalApiControllerOwnershipTest`, `DualApprovalIntegrationTest` |

### Audit

| Åtgärd | Endpoint | Initiatör | Attestant | Admin | Teststöd |
|---|---|---|---|---|---|
| Läsa auditloggen (senaste 200) | `GET /api/audit` | Nej | Företagets skapade, godkända och avvisade betalningar | Företaget | `RoleAuthorizationTest`, `TenantIsolationIntegrationTest` |
| Läsa en betalnings tidslinje | `GET /api/payments/{paymentId}/audit` | Nej | Företagets skapade, godkända och avvisade betalningar | Företaget | `RoleAuthorizationTest` (även nekad initiator), `TenantIsolationIntegrationTest` |

### Användaradministration

| Åtgärd | Endpoint | Initiatör | Attestant | Admin | Teststöd |
|---|---|---|---|---|---|
| Lista användare | `GET /api/admin/users` | Nej | Nej | Företaget | `AdminUserControllerTest`, `UserAdministrationIntegrationTest` |
| Skapa användare (INITIATOR eller ATTESTANT) | `POST /api/admin/users` | Nej | Nej | I det egna företaget | `AdminUserControllerTest`, `UserAdministrationIntegrationTest`, `UserAdminServiceTest` |
| Byta roll mellan INITIATOR och ATTESTANT | `PATCH /api/admin/users/{userId}/role` | Nej | Nej | I det egna företaget, aldrig en administratör | `AdminUserControllerTest`, `UserAdministrationIntegrationTest`, `UserAdminServiceTest` |
| Ta bort en användare (bara utan historik) | `DELETE /api/admin/users/{userId}` | Nej | Nej | I det egna företaget, aldrig en administratör | `AdminUserControllerTest`, `UserAdministrationIntegrationTest`, `UserAdminServiceTest` |

### Utanför `/api`

| Åtgärd | Sökväg | Vem | Teststöd |
|---|---|---|---|
| Hälsokontroll, utan detaljer | `GET /actuator/health` | Alla | `AuditSignerWiringTest` |
| Frontendens filer och routes | `/`, `/assets/**` och frontendens routes | Alla (data kräver fortfarande JWT) | `SpaFallbackControllerTest` |

### Åtgärder som inte finns

Det finns ingen endpoint som låter någon roll göra följande:

- inaktivera en användare, eller byta lösenord
- ta bort en användare som har betalningar eller atteststeg kopplade till sig, eller ta bort en administratör
- ge någon rollen ADMIN, eller ändra en administratörs roll
- ändra eller ta bort en betalning, ett atteststeg eller en auditrad
- ändra saldo eller tröskelvärden direkt

## Frontendens vyer

Spärrarna i `frontend/src/router.tsx` styr bara navigeringen. Skyddet ligger i backend.

| Vy | Route | Ej inloggad | Initiatör | Attestant | Admin |
|---|---|---|---|---|---|
| Inloggning | `/login` | Ja | Ja | Ja | Ja |
| Översikt | `/` | Nej | Ja | Ja | Ja |
| Ny betalning | `/payments/new` | Nej | Ja | Nej | Nej |
| Mina betalningar | `/my-payments` | Nej | Ja | Nej | Nej |
| Attestkorg | `/attest` | Nej | Nej | Ja | Ja, skrivskyddad översikt utan beslutsknappar |
| Auditlogg | `/audit` | Nej | Nej | Ja | Ja |
| Användaradministration | `/admin/users` | Nej | Nej | Nej | Ja, kopplad till `/api/admin/users` för lista, skapa, rollbyte och borttagning |

`/uitest` är bortkommenterad i routern och ingår inte i det aktiva routeträdet. Admins navigation och dashboard använder bara tillåtna vyer och anrop. `frontend/e2e/admin.spec.ts` verifierar omdirigering från initiatorns routes, att förbjudna API-anrop uteblir och att attestöversikten saknar beslutsknappar. Användarhändelser i historiken kan inte öppna en betalningstidslinje. Se [projektgenomgången](project-review.md).

## Permission escalation

**Vertikal eskalering** betyder att få en högre roll eller dess rättigheter. **Horisontell eskalering** betyder att komma åt en annan användares eller ett annat företags data med samma roll.

### Vertikal

| Försök | Resultat | Bevis |
|---|---|---|
| Admin skapar en betalning och godkänner den själv | **Stängt** (R-03, ADR 0017). Admin kan varken skapa eller godkänna betalningar och nekas med 403. | `RoleAuthorizationTest`, `ApprovalApiControllerOwnershipTest`. Det tidigare hålet provkördes 2026-11-02 mot Postgres: en betalning på 6 000 kr genomfördes. |
| Admin godkänner något steg vid dubbel attest | Nekas med 403 (ADR 0017). Admin kan inte vara någon av de två personerna. | `DualApprovalIntegrationTest.anAdminCannotDecideOnAnyStep_butSeesWhatWaits` |
| Skaparen godkänner eller avvisar sin egen betalning, till exempel efter en rolländring | Nekas med 403, även som tilldelad attestant (ADR 0017). Skaparen tilldelas heller aldrig sin egen betalning. | `ApprovalServiceTest`, `ApprovalApiControllerOwnershipTest`, `PaymentServiceTest`, `DualApprovalIntegrationTest` |
| Admin ger sig själv eller någon annan rollen ADMIN, eller ändrar en administratörs roll | Nekas. ADMIN kan inte tilldelas (400), och en administratörs roll ändras aldrig via API:t (403). | `UserAdministrationIntegrationTest`, `UserAdminServiceTest` |
| Initiatör attesterar, eller attestant skapar en betalning | Nekas med 403. | `RoleAuthorizationTest` |
| Förfalska en token med högre roll | Nekas. Nyckeln finns varken i repot eller i konfigurationen (R-17). | `JwtServiceTest` |
| Ändra sin egen roll | Går inte. Admins egen roll ändras aldrig (403), och andras roll kan bara en admin ändra, bara mellan INITIATOR och ATTESTANT. | `UserAdministrationIntegrationTest` |
| Använda en gammal roll efter ett rollbyte | Går i upp till 15 minuter, tills access-token går ut. Accepterat i ADR 0012. | `RefreshTokenServiceTest` |

### Horisontell

| Försök | Resultat | Bevis |
|---|---|---|
| Attestant hanterar en annan attestants steg | Nekas med 403, och inget ändras (R-03). | `ApprovalApiControllerOwnershipTest` |
| Attestant 2 hoppar före attestant 1 | Nekas med 409, både vid godkännande och avvisning (ADR 0016). | `DualApprovalIntegrationTest` |
| Komma åt ett annat företags data | Syns inte. Ett annat företags steg ger samma 400 som ett steg som inte finns (R-04). | `TenantIsolationIntegrationTest` |
| Skicka ett annat företag eller en annan skapare i anropet | Ignoreras. Värdena hämtas från JWT:n. | `TenantIsolationIntegrationTest`, `NewPaymentController` |
| Betala från ett annat företags konto | Nekas med 400, både när betalningen skapas och när den godkänns (R-04). | `PaymentServiceTest`, `ApprovalServiceTest`, `TenantIsolationIntegrationTest` |
| Läsa en annan initiatörs betalningar | Går inte via mina betalningar, som filtrerar på skaparen. | `AuditServiceMyPaymentsTest` |

## Öppna punkter

1. **Ta bort `approve(stepId, actorId)` och `reject(stepId, actorId, comment)` i `ApprovalService`.** De saknar företags- och ägarkontroll för inloggad användare. Ingen controller anropar dem, så de går inte att nå via API:t.
2. **Ta ställning till attestantens läsrätt.** En attestant kan läsa alla företagets konton, saldon och betalningshändelser, inte bara sina egna steg. Det kan vara rimligt för uppgiften, men det är bredare än minsta möjliga behörighet.
