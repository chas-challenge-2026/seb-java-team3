import { api } from "../../../lib/api";
import {
  auditApiEntryListSchema,
  changedPaymentCountSchema,
  myPaymentStatusListSchema,
  paymentAuditTimelineEntryListSchema,
} from "../schema";

export function fetchAuditEntries() {
  return api("/api/audit", {}, auditApiEntryListSchema);
}

export function fetchPaymentAuditTimeline(paymentId: string) {
  return api(
    `/api/payments/${paymentId}/audit`,
    {},
    paymentAuditTimelineEntryListSchema,
  );
}

export function fetchMyPaymentStatuses() {
  return api("/api/my-payments", {}, myPaymentStatusListSchema);
}

export async function fetchChangedPaymentCount(): Promise<number> {
  const response = await api("/api/payments/changed-count", {}, changedPaymentCountSchema);
  return response.count;
}

// Initiatorn har sett sina betalningar: räknaren nollas tills nästa beslut fattas
export async function markChangedPaymentsSeen(): Promise<void> {
  await api("/api/payments/changed-count/seen", { method: "POST" });
}
