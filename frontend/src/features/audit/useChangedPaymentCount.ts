import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchChangedPaymentCount, markChangedPaymentsSeen } from "./api/auditApi";
import { myPaymentsQueryOptions } from "./queries/auditQueryOptions";

export const changedPaymentCountQueryKey = ["my-payments", "changed-count"];

// Initiatorns badge på Mina betalningar: betalningar som godkänts eller avvisats sedan initiatorn
// senast öppnade listan. Beslutet fattas av någon annan, så räknaren hämtas om var 30:e sekund.
export function useChangedPaymentCount(enabled: boolean) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: changedPaymentCountQueryKey,
    queryFn: fetchChangedPaymentCount,
    enabled,
    refetchInterval: 30_000,
  });
  const count = query.data ?? 0;

  // Något har fått ett beslut, så listan är inte längre aktuell. Är Mina betalningar öppen hämtas
  // den om direkt, och då markeras betalningarna som sedda och badgen försvinner.
  useEffect(() => {
    if (count > 0) {
      queryClient.invalidateQueries({ queryKey: myPaymentsQueryOptions.queryKey });
    }
  }, [count, queryClient]);

  return query;
}

// Nollar badgen. Anropas från Mina betalningar varje gång listan har hämtats.
export function useMarkChangedPaymentsSeen() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: markChangedPaymentsSeen,
    onSuccess: () => queryClient.setQueryData(changedPaymentCountQueryKey, 0),
  });
}
