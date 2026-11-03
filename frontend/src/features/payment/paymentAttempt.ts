// Ett obekräftat försök behålls även efter omladdning i samma flik. Vi lagrar bara en hash av
// uppgifterna och en slumpad nyckel, aldrig IBAN, referens eller token. Separat per användare.
const pending = new Map<string, string>();

export async function paymentAttempt(userId: number, body: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(body));
  const hash = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  const storageKey = `seb-payment-attempt:${userId}:${hash}`;
  let key = pending.get(storageKey);
  try {
    key ??= sessionStorage.getItem(storageKey) ?? undefined;
  } catch {
    // Blockerad sessionStorage: samma försök behålls i minnet under sidans livstid.
  }
  key ??= crypto.randomUUID();
  pending.set(storageKey, key);
  try {
    sessionStorage.setItem(storageKey, key);
  } catch {
    // Minneskopian används när lagring inte är tillgänglig.
  }

  return {
    key,
    confirmed() {
      // Ett sent svar får inte ta bort ett senare försök med samma uppgifter.
      if (pending.get(storageKey) === key) pending.delete(storageKey);
      try {
        if (sessionStorage.getItem(storageKey) === key) sessionStorage.removeItem(storageKey);
      } catch {
        // Ingen lagrad kopia att rensa.
      }
    },
  };
}
