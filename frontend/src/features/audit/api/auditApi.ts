import { api } from "../../../lib/api";
import {
  auditApiEntryListSchema,
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
