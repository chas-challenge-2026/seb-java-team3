# ADR 0012 — Refresh token i HttpOnly-cookie med rotation, access-token i minnet

- **Status:** Föreslagen
- **Datum:** 2026-10-07
- **Deltagare:** Jonathan Isaksson (författare). Övriga fylls i när teamet har gått igenom förslaget · drivande: Jonathan Isaksson
- **Berör:** Auth & behörighet, backend, frontend
- **Relaterade issues:** #194, #195
- **Ersätter delvis:** [ADR 0009](0009-jwt-i-localstorage-ersatter-httponly-cookie.md) (var token lagras). ADR 0009 får status "Delvis ersatt av ADR 0012" när den här ADR:n är Accepterad.
- **Relaterat:** [ADR 0011](0011-jwt-signeringsnyckeln-slumpas-vid-start.md), [ADR 0003](0003-mvp-scope-och-avgransningar.md), CTO-feedbacken i `docs/Risk-feedfoward.md` (punkt 8)

## Kontext

Idag har vi en enda JWT (ADR 0009). Den gäller i 1 timme, är stateless, ligger i `localStorage` och skickas som `Authorization: Bearer`. Det ger fyra problem:

- **Den går inte att återkalla.** Servern slår inte upp token i någon databas. "Logga ut" raderar bara token i webbläsaren, och en kopierad token fungerar tills den går ut.
- **Den kan läsas av JavaScript.** En lyckad XSS-attack kan stjäla den (R-13, accepterad i ADR 0009, som själv pekar ut refresh token som uppföljning).
- **Det finns ingen bra livslängd.** Kort ger ständiga utloggningar, och lång ger en stulen token längre användbarhet.
- **Varje omstart loggar ut alla** sedan ADR 0011 slumpar signeringsnyckeln vid start.

Refresh-token-rotation har legat som "breddning" (ADR 0003, `docs/mvp.md`), men står som acceptanskriterium i `docs/backlog.md`: kort access-token, refresh-token med rotation där den gamla ogiltigförklaras, och en session som går att återkalla. CTO-feedbacken pekade ut JWT-hanteringen som det allvarligaste, och i `Risk-feedfoward.md` antecknade teamet att JWT ska ligga i en variabel i 15 minuter i frontend, med refresh via httpOnly-cookie. Teamet har beslutat att bygga det nu, i sprint v8.

Förutsättningar:

- Frontend och API är samma origin: Spring serverar den byggda appen, och Vite proxar `/api` i utveckling. Stage och prod är HTTPS.
- `/api/**` har CSRF avstängt, eftersom autentiseringen går via en header som webbläsaren inte skickar av sig själv (ADR 0009). En cookie ändrar det för de endpoints som läser den.
- Vi har redan en databas, och en refresh-token som sparas där överlever omstarter.

## Beslut

Vi inför två token med olika uppgifter:

- **Access-token:** samma JWT som idag, men med 15 minuters livslängd. Frontend håller den bara i minnet (inte i `localStorage`) och skickar den som `Authorization: Bearer`.
- **Refresh-token:** en opak, slumpad sträng (256 bit, `SecureRandom`). Servern sparar bara dess SHA-256-hash i tabellen `refresh_tokens`. Den levereras som cookie: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, `Secure` (konfigurerbart) och 7 dagars `Max-Age`.

Regler:

- **Rotation:** varje anrop till `POST /api/auth/refresh` markerar token som använd och ger en ny refresh-token i samma familj (samma inloggning) plus en ny access-token.
- **Strikt återanvändningsdetektion, ingen nådetid:** om en redan använd eller spärrad token visas igen spärras hela familjen och svaret blir 401. Både en eventuell tjuv och den riktiga användaren måste då logga in på nytt.
- **Glidande session på 7 dagar:** varje ny refresh-token gäller 7 dagar från utfärdandet. En aktiv användare förblir inloggad, och efter 7 dagars inaktivitet krävs ny inloggning.
- **Utloggning:** `POST /api/auth/logout` spärrar familjen och tömmer cookien.
- **Refresh läser om användaren** ur databasen. Rollbyten slår igenom vid nästa förnyelse, och en borttagen användare kan inte förnya (familjen spärras).
- **CSRF för de två cookie-endpointsen** (`/refresh`, `/logout`): `SameSite=Strict`, cookien skickas bara till `Path=/api/auth`, och anropen måste ha headern `X-Requested-With`, som en annan sajt inte kan sätta utan att få sin begäran blockerad. Resten av `/api/**` är oförändrat och använder fortfarande bara Bearer.
- **Frontend:** `credentials: "same-origin"` bara för login, refresh och logout (annars ignorerar webbläsaren `Set-Cookie`), en tyst förnyelse vid sidladdning, ett nytt försök efter förnyelse när ett anrop får 401, och en förnyelse i taget (single-flight, samordnad mellan flikar med Web Locks).
- **Utrullning i additiva steg:** backend först utan beteendeförändring för dagens frontend, sedan frontend, och sist själva omkopplingen av access-token från 1 timme till 15 minuter (`jwt.expiration-ms`).

## Alternativ vi övervägde

- **Behålla en enda lång token (som idag)** - valdes bort: går inte att återkalla och ger ett långt fönster för en stulen token.
- **Refresh-token som JWT** - valdes bort: den behöver ändå sparas i databasen för att kunna spärras och roteras, så en självbärande token ger ingenting. En opak token är enklare och kan inte läsas ut.
- **Refresh-token i svarskroppen och i `localStorage`** - valdes bort: då kan XSS läsa den, och hela vinsten med en separat, långlivad token försvinner.
- **Serversessioner med sessionscookie i stället för JWT** - valdes bort: skulle ersätta hela JWT-modellen, ta tillbaka CSRF för alla endpoints och bryta mot ADR 0009.
- **Rotation med nådetid (till exempel 10 sekunder)** - valdes bort för nu. En nådetid ger en tjuv ett fönster. Den vanligaste godartade krocken, två flikar som förnyar samtidigt, hanteras i frontend med single-flight och Web Locks. Kvar är ett nätverksfel där servern hunnit rotera men svaret aldrig nådde webbläsaren; då loggas användaren ut. Nådetid införs om något av detta ställer till det i praktiken.
- **Absolut tak för sessionen (till exempel 30 dagar oavsett aktivitet)** - valdes bort för nu. Det är en extra kolumn och regel som går att lägga till senare, och den glidande sessionen är enklare att förstå.
- **Refresh utan rotation (återanvändbar token)** - valdes bort: en stulen token kan inte upptäckas.

## Konsekvenser

**Positiva**
- En stulen access-token gäller högst 15 minuter i stället för 1 timme.
- Sessioner går att återkalla på riktigt, och logout spärrar den långlivade token på servern.
- Stöld av refresh-token upptäcks genom rotationen.
- Den långlivade token är oläsbar för JavaScript, och `localStorage` håller ingen token längre. R-13 minskar.
- Omstarter märks inte för användarna (ADR 0011): efter en omstart nekas access-token, frontend förnyar tyst, och servern ger en ny access-token signerad med den nya nyckeln.
- Roll- och användarändringar slår igenom inom ungefär 15 minuter.

**Negativa / risker**
- **Fler delar och nya felfall.** Två flikar eller ett nätverksfel kan utlösa den strikta detektionen och logga ut användaren. → Single-flight och Web Locks i frontend; nådetid om det behövs.
- **CSRF kommer tillbaka för `/refresh` och `/logout`.** → `SameSite=Strict`, `Path=/api/auth` och den egna headern.
- **`Secure`-cookie** kräver HTTPS eller localhost, och vissa webbläsare (till exempel Safari) avvisar den över vanlig HTTP. → Konfigurerbart. Om cookien inte sätts misslyckas förnyelsen och användaren loggar in på nytt.
- **Access-token går fortfarande inte att återkalla inom sina 15 minuter.** → Acceptabelt: efter logout eller upptäckt stöld tar sessionen slut senast då.
- **Frontend tappar access-token vid omladdning** och måste förnya tyst (ett extra anrop vid sidladdning).
- **Utloggning i en flik är inte omedelbar i andra flikar** (deras access-token gäller upp till 15 minuter).
- **Tabelltillväxt:** utgångna och spärrade rader städas bort för användaren vid inloggning.
- **XSS försvinner inte.** Skadlig kod kan fortfarande läsa access-token i minnet och agera som användaren medan sidan är öppen, men får inget som håller länge.
- **En omdeploy ogiltigförklarar gamla `localStorage`-token** (de ignoreras), så användare loggar in en gång på nytt.

## Uppföljning

- Verifiera live före merge: migrationen V8 mot riktig Postgres, cookie-attributen, rotation, att återanvändning spärrar familjen, och ett E2E-scenario där sidan laddas om och användaren förblir inloggad.
- När ADR:n är Accepterad: sätt ADR 0009 till "Delvis ersatt av ADR 0012", uppdatera R-13 i riskregistret (#201), och uppdatera `docs/mvp.md`, `docs/kravtolkning.md` och ADR 0003 där refresh-token-rotation står som breddning.
- Omvärdera vid behov: nådetid vid återanvändning, absolut tak för sessionen, ett städjobb för utgångna rader, och "logga ut överallt".
- Ratebegränsning av login och refresh ingår inte här och ligger kvar som separat punkt.
