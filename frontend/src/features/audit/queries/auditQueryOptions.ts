import { queryOptions } from "@tanstack/react-query";
import { fetchAuditEntries, fetchMyPaymentStatuses, fetchPaymentAuditTimeline } from "../api/auditApi";

export const auditQueryOptions = queryOptions({
  queryKey: ["audit", "entries"],
  queryFn: fetchAuditEntries,
});

export const myPaymentsQueryOptions = queryOptions({
  queryKey: ["audit", "my-payments"],
  queryFn: fetchMyPaymentStatuses,
});

export function paymentAuditTimelineQueryOptions(paymentId: string) {
  return queryOptions({
    queryKey: ["payments", paymentId, "audit"],
    queryFn: () => fetchPaymentAuditTimeline(paymentId),
  });
}
