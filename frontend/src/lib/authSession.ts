import { loginResponseSchema, type UserResponse } from "../features/auth/schema";
import { clearToken, setToken } from "./authToken";

const BASE_URL = import.meta.env.VITE_API_URL ?? "";
const REFRESH_PATH = "/api/auth/refresh";
const LOGOUT_PATH = "/api/auth/logout";

// Namn på webbläsarens lås (Web Locks) som ser till att bara en flik åt gången förnyar sessionen.
const LOCK_NAME = "seb-refresh-session";

// /refresh och /logout kräver den här headern (CSRF-skydd, ADR 0012). Värdet spelar ingen roll, det är
// närvaron som räknas: en annan sajt kan inte sätta en egen header utan att webbläsaren först skickar
// en förfrågan i förväg, som servern inte godkänner.
const REQUESTED_WITH = { "X-Requested-With": "fetch" };

// En pågående förnyelse i den här fliken. Flera anrop som får 401 samtidigt delar den, i stället för att
// skicka varsin: servern räknar två förnyelser med samma token som återanvändning och loggar ut användaren.
let inFlight: Promise<UserResponse | null> | null = null;

// Förnyar sessionen med refresh-cookien. Lyckas det sparas den nya access-token i minnet och användaren
// returneras. Returnerar null om det inte finns någon giltig session (eller servern inte gick att nå);
// funktionen kastar aldrig, så anroparen bara behöver välja mellan "inloggad" och "skicka till login".
export function refreshSession(): Promise<UserResponse | null> {
  if (!inFlight) {
    inFlight = withCrossTabLock(callRefreshEndpoint).finally(() => {
      inFlight = null;
    });
  }

  return inFlight;
}

// Utloggning: spärrar sessionen på servern och tömmer access-token här. Fungerar även om servern inte
// går att nå, då loggas användaren ut lokalt och refresh-token går ut av sig själv.
export function logoutSession(): Promise<void> {
  return withCrossTabLock(async () => {
    try {
      await fetch(`${BASE_URL}${LOGOUT_PATH}`, {
        method: "POST",
        credentials: "same-origin",
        headers: REQUESTED_WITH,
      });
    } catch {
      // Servern gick inte att nå. Användaren loggas ändå ut lokalt nedan.
    } finally {
      clearToken();
    }
  });
}

async function callRefreshEndpoint(): Promise<UserResponse | null> {
  try {
    const response = await fetch(`${BASE_URL}${REFRESH_PATH}`, {
      method: "POST",
      credentials: "same-origin",
      headers: REQUESTED_WITH,
    });

    if (!response.ok) {
      clearToken();
      return null;
    }

    const parsed = loginResponseSchema.safeParse(await response.json().catch(() => null));

    if (!parsed.success) {
      clearToken();
      return null;
    }

    const { token, ...user } = parsed.data;
    setToken(token);
    return user;
  } catch {
    // Servern gick inte att nå. En befintlig token behålls: den kan fortfarande vara giltig.
    return null;
  }
}

// Web Locks låter flera flikar av samma webbläsare turas om. Cookien delas mellan flikarna, så en flik
// som får vänta ser den redan roterade cookien när den får sin tur. Saknas Web Locks (äldre webbläsare)
// räcker förnyelsen en i taget per flik.
function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;

  if (!locks) {
    return task();
  }

  return locks.request(LOCK_NAME, task);
}
