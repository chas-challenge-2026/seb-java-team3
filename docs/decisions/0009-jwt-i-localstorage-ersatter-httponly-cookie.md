# ADR 0009 — JWT i Authorization-header och localStorage ersätter HttpOnly-cookie

- **Status:** Accepterad (ersätter ADR 0008)
- **Datum:** 2026-09-21
- **Deltagare:** Jonathan Isaksson, Adnan, Marcus Johansson, Pontus Ingenius, Samuel · drivande: Jonathan Isaksson
- **Berör:** Auth & behörighet, frontend
- **Relaterade issues:** #29, #33
- **Ersätter:** [ADR 0008 - Lagring av token i HttpOnly-cookie](0008-Lagring%20av%20token%20i%20HttpOnly-cookie)

## Kontext

ADR 0008 beslutade att autentiseringstoken skulle lagras i en HttpOnly-cookie, främst för att en token i `localStorage` kan läsas av JavaScript och därmed stjälas vid en XSS-attack.

Implementationen följer inte det beslutet. v2-lösningen bygger på en JWT som backend utfärdar vid `POST /api/auth/login` och som frontend skickar i `Authorization`-headern:

- Frontend sparar token i `localStorage` (`frontend/src/lib/authToken.ts`) och skickar den som `Authorization: Bearer ...` med `credentials: "omit"` (`frontend/src/lib/api.ts`).
- Backend verifierar token i `JwtAuthenticationFilter` och läser identitet, tenant och roll ur den (`JwtUserContext`).
- Ingen cookie sätts någonstans.

Cookie-lösningen från ADR 0008 hörde ihop med en session- eller refresh-baserad modell. Refresh-token (rotation, förnyelse) ligger utanför MVP-scope (ADR 0003), så det fanns ingen färdig mekanism att bygga cookie-lösningen på. Utan refresh hade en cookie-baserad JWT ändå krävt egen hantering av utgång, CSRF och cookie-konfiguration mellan frontend och backend, och v2-kravet pekade på JWT.

Resultatet blev en blandning som varken följde ADR 0008 eller var dokumenterad. Den här ADR:n gör det faktiska läget till ett uttalat beslut.

## Beslut

Vi lagrar JWT i `localStorage` och skickar den i `Authorization`-headern. ADR 0008 ersätts.

## Alternativ vi övervägde

- **HttpOnly-cookie (ADR 0008)** - säkrare mot tokenstöld via XSS, men kräver CSRF-skydd, cookie-konfiguration (Secure, SameSite, domän/CORS) och en refresh-mekanism för att bli användbar. Valdes bort för MVP eftersom refresh är utanför scope och tiden inte räcker.
- **JWT i localStorage + Authorization-header** - enkel att implementera, fungerar med den stateless JWT-modellen i backend och kräver inget CSRF-skydd, eftersom webbläsaren inte skickar headern automatiskt. Valdes.
- **JWT i minnet (JavaScript-variabel)** - inte läsbar efter sidladdning och svårare att stjäla, men användaren loggas ut vid varje omladdning utan refresh. Valdes bort av användbarhetsskäl.

## Konsekvenser

**Positiva**
- Enkel och konsekvent modell: en stateless JWT som backend verifierar vid varje anrop.
- Inget CSRF-problem för `/api/**`, eftersom autentiseringen inte skickas automatiskt av webbläsaren.
- Ingen cookie-konfiguration mellan origins att underhålla.

**Negativa / risker**
- Token kan läsas av JavaScript, så en lyckad XSS-attack kan stjäla den (registrerad som R-13, status Accepted, ägare Frontend).
  **Kompenserande kontroller som finns:** token gäller 1 timme (`jwt.expiration-ms=3600000`), frontend använder inte `dangerouslySetInnerHTML` och React escapar output som standard, backend kontrollerar signatur, utgång och roll vid varje anrop.
  **Saknas:** Content-Security-Policy.
- Ingen refresh-token betyder att användaren måste logga in på nytt när token gått ut.

## Uppföljning

- Lägg till Content-Security-Policy (frontend/deploy) för att minska XSS-risken.
- Omvärdera beslutet om refresh-token byggs efter MVP; då kan refresh-token i HttpOnly-cookie kombineras med en kortlivad access-token i minnet.
- Håll R-13 i riskregistret uppdaterat.
- Kontrollera vid slutleverans att ingen kod gör `dangerouslySetInnerHTML` eller liknande med användardata.
