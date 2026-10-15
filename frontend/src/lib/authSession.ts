import { loginResponseSchema, type UserResponse } from "../features/auth/schema";
import { clearToken, getTokenUserId, setToken } from "./authToken";
import { BASE_URL } from "./baseUrl";

const REFRESH_PATH = "/api/auth/refresh";
const LOGOUT_PATH = "/api/auth/logout";

// Namn på webbläsarens lås (Web Locks) som ser till att bara en flik åt gången förnyar sessionen.
const LOCK_NAME = "seb-refresh-session";

// /refresh och /logout kräver den här headern (CSRF-skydd, ADR 0012). Värdet spelar ingen roll, det är
// närvaron som räknas: en annan sajt kan inte sätta en egen header utan att webbläsaren först skickar
// en förfrågan i förväg, som servern inte godkänner.
const REQUESTED_WITH = { "X-Requested-With": "fetch" };

// Hur länge /refresh och /logout får ta. Anropen håller låset mellan flikarna, så en server som hänger
// får inte stoppa förnyelse och utloggning i alla flikar tills webbläsaren själv ger upp.
const SESSION_REQUEST_TIMEOUT_MS = 10_000;

// Utfallet av en förnyelse:
//  - renewed: ny access-token i minnet, användaren returneras
//  - no-session: servern svarade att det inte finns någon giltig session (401), token är tömd
//  - unreachable: servern gick inte att nå eller svarade med 5xx (omstart, proxyfel). Sessionen kan
//    fortfarande vara giltig, så ingenting töms och användaren ska inte skickas till inloggningen
//  - user-changed: sessionen tillhör nu en annan användare än den fliken är inloggad som (någon har
//    loggat in i en annan flik, och cookien delas). Fliken får inte fortsätta som den nya användaren
export type RefreshResult =
  | { status: "renewed"; user: UserResponse }
  | { status: "no-session" }
  | { status: "unreachable" }
  | { status: "user-changed" };

// En pågående förnyelse i den här fliken. Flera anrop som får 401 samtidigt delar den, i stället för att
// skicka varsin: servern räknar två förnyelser med samma token som återanvändning och loggar ut användaren.
let inFlight: Promise<RefreshResult> | null = null;

const userChangedListeners = new Set<() => void>();

// Anropas när en förnyelse visar att sessionen tillhör en annan användare. main.tsx laddar då om appen,
// så att allt som visas hör till den användare som sessionen faktiskt tillhör.
export function onUserChanged(listener: () => void): () => void {
  userChangedListeners.add(listener);
  return () => {
    userChangedListeners.delete(listener);
  };
}

// Förnyar sessionen med refresh-cookien. Kastar aldrig, utfallet beskrivs av RefreshResult.
export function refreshSession(): Promise<RefreshResult> {
  if (!inFlight) {
    inFlight = withCrossTabLock(callRefreshEndpoint).finally(() => {
      inFlight = null;
    });
  }

  return inFlight;
}

// Utloggning: spärrar sessionen på servern och tömmer access-token här. Fungerar även om servern inte
// går att nå eller inte svarar i tid, då loggas användaren ut lokalt och refresh-token går ut av sig själv.
export function logoutSession(): Promise<void> {
  return withCrossTabLock(async () => {
    try {
      await fetch(`${BASE_URL}${LOGOUT_PATH}`, {
        method: "POST",
        credentials: "same-origin",
        headers: REQUESTED_WITH,
        signal: AbortSignal.timeout(SESSION_REQUEST_TIMEOUT_MS),
      });
    } catch {
      // Servern gick inte att nå. Användaren loggas ändå ut lokalt nedan.
    } finally {
      clearToken();
    }
  });
}

async function callRefreshEndpoint(): Promise<RefreshResult> {
  let response: Response;

  try {
    response = await fetch(`${BASE_URL}${REFRESH_PATH}`, {
      method: "POST",
      credentials: "same-origin",
      headers: REQUESTED_WITH,
      signal: AbortSignal.timeout(SESSION_REQUEST_TIMEOUT_MS),
    });
  } catch {
    // Servern gick inte att nå eller svarade inte i tid. En befintlig token behålls: den kan fortfarande vara giltig.
    return { status: "unreachable" };
  }

  if (response.status >= 500) {
    // Servern startar om eller proxyn når den inte (502/503/504). Det säger ingenting om sessionen.
    return { status: "unreachable" };
  }

  if (!response.ok) {
    clearToken();
    return { status: "no-session" };
  }

  const parsed = loginResponseSchema.safeParse(await response.json().catch(() => null));

  if (!parsed.success) {
    clearToken();
    return { status: "no-session" };
  }

  const { token, ...user } = parsed.data;
  const currentUserId = getTokenUserId();

  if (currentUserId !== null && currentUserId !== user.id) {
    // Den nya token tillhör någon annan. Den sparas inte, och fliken behåller sin gamla (utgångna) token,
    // så att varje anrop fortsätter att nekas tills appen laddats om som den nya användaren.
    userChangedListeners.forEach((listener) => listener());
    return { status: "user-changed" };
  }

  setToken(token, user.id);
  return { status: "renewed", user };
}

// Web Locks låter flera flikar av samma webbläsare turas om. Cookien delas mellan flikarna, så en flik
// som får vänta ser den redan roterade cookien när den får sin tur. Web Locks saknas i äldre webbläsare
// och på alla http-adresser utom localhost (det kräver en säker anslutning). Där förnyar varje flik en i
// taget för sig, och två flikar som förnyar samtidigt kan logga ut varandra (servern ser återanvändning).
function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;

  if (!locks) {
    return task();
  }

  return locks.request(LOCK_NAME, task);
}
