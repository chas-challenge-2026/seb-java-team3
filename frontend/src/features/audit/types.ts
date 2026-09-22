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
  referens: string;
  belopp: string;
  betalning: string;
};
