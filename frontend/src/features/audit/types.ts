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

export type AuditApiEntry = {
  id: number;
  action: string;
  entityType: string;
  entityId: number;
  description: string;
  status: string | null;
  reference: string | null;
  amount: number | null;
  currency: string | null;
  createdAt: string;
  userName: string;
};

export type MyPaymentStatus = {
  paymentId: number;
  reference: string | null;
  amount: number | null;
  currency: string | null;
  toIban: string | null;
  status: string;
  createdAt: string | null;
  executedAt: string | null;
};

export type PaymentAuditTimelineEntry = {
  order: number;
  sequence: string;
  actor: string;
  eventType: string;
  description: string;
  timestamp: string | null;
  stepNumber: number | null;
  status: string;
  reference: string | null;
  amount: number | null;
  currency: string | null;
  toIban: string | null;
};
