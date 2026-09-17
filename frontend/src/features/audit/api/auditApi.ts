import { api } from "../../../lib/api";
import type { AuditApiEntry, MyPaymentStatus, PaymentAuditTimelineEntry } from "../types";

export function fetchAuditEntries() {
  return api<AuditApiEntry[]>("/api/audit");
}

export function fetchPaymentAuditTimeline(paymentId: string) {
  return api<PaymentAuditTimelineEntry[]>(`/api/payments/${paymentId}/audit`);
}

export function fetchMyPaymentStatuses() {
  return api<MyPaymentStatus[]>("/api/my-payments");
}
