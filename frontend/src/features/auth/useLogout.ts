import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { logoutSession } from "../../lib/authSession";

// Utloggning (ADR 0012): sessionen spärras på servern och access-token töms, och först därefter skickas
// användaren till inloggningen. Väntar in servern så att en snabb ny inloggning inte kan hinna före
// utloggningen; logoutSession har en tidsgräns och kastar aldrig, så användaren loggas alltid ut lokalt.
// Cachen töms efter navigeringen: annars hinner sidor som fortfarande visas hämta data igen utan token,
// få 401 och försöka förnya den session som just avslutats.
export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return async () => {
    await logoutSession();
    await navigate({ to: "/login" });
    queryClient.clear();
  };
}
