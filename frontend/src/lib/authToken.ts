// Access-token hålls bara i minnet, aldrig i localStorage (ADR 0012). Skadlig kod på sidan kan då inte
// läsa ut en token som ligger kvar i webbläsaren. Efter en omladdning finns ingen token i minnet, och
// appen förnyar den tyst med refresh-cookien, som är HttpOnly och oläsbar för JavaScript. Se authSession.ts.
let accessToken: string | null = null;

export function getToken(): string | null {
  return accessToken;
}

export function setToken(token: string): void {
  accessToken = token;
}

export function clearToken(): void {
  accessToken = null;
}

// Tidigare versioner sparade token i localStorage. Ta bort den så att en kvarglömd, fortfarande
// giltig token inte ligger kvar i webbläsaren efter att vi slutat använda den.
const LEGACY_STORAGE_KEY = "authToken";

function removeLegacyStoredToken(): void {
  try {
    localStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    // localStorage kan vara blockerad (till exempel i ett privat fönster): då finns det inget att ta bort
  }
}

removeLegacyStoredToken();
