import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { logoutSession } from "../../lib/authSession";

// Utloggning (ADR 0012): sessionen spärras på servern och access-token töms, och först därefter töms
// cachen och användaren skickas till inloggningen. Väntar in servern så att en snabb ny inloggning
// inte kan hinna före utloggningen. logoutSession kastar aldrig, så användaren loggas alltid ut lokalt.
export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return async () => {
    await logoutSession();
    queryClient.clear();
    navigate({ to: "/login" });
  };
}
