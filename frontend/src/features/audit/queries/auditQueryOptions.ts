import { queryOptions } from "@tanstack/react-query";
import { fetchAuditEntries, fetchPaymentAuditTimeline } from "../api/auditApi";

export const auditQueryOptions = queryOptions({
  queryKey: ["audit", "entries"],
  queryFn: fetchAuditEntries,
});

export function paymentAuditTimelineQueryOptions(paymentId: string) {
  return queryOptions({
    queryKey: ["payments", paymentId, "audit"],
    queryFn: () => fetchPaymentAuditTimeline(paymentId),
  });
}
