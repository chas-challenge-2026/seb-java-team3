export type {
  AuditApiEntry,
  MyPaymentStatus,
  PaymentAuditTimelineEntry,
} from "./schema";

export type AuditEntry = {
  id: string;
  paymentId: string;
  tid: string;
  vem: string;
  handelse: string;
  status: string;
  // Betalningens status som den kommer från backend (PENDING_APPROVAL, COMPLETED, REJECTED), för filtret
  statusKey: string | null;
  referens: string;
  belopp: string;
  franKonto: string;
  betalning: string;
};
