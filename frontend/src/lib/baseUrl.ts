// Adressen till API:t. Tom betyder samma server som appen (Vite-proxyn i dev, samma server i Docker).
// Delas av api.ts och authSession.ts, så att vanliga anrop och sessionsanrop alltid går till samma ställe.
export const BASE_URL = import.meta.env.VITE_API_URL ?? "";
