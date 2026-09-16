import {
  formatAmount,
  formatAuditStatus,
  formatDateTime,
  formatEventType,
} from "./utils/formatters";

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

export function toAuditEntry(raw: AuditApiEntry): AuditEntry {
  return {
    id: String(raw.id),
    paymentId: String(raw.entityId),
    tid: formatDateTime(raw.createdAt),
    vem: raw.userName,
    handelse: formatEventType(raw.action),
    status: formatAuditStatus(raw.status),
    referens: raw.reference?.trim() || "-",
    belopp: formatAmount(raw.amount, raw.currency),
    betalning: formatEntityLabel(raw.entityType, raw.entityId),
  };
}

export function latestAuditEntryByPayment(entries: AuditApiEntry[]): AuditApiEntry[] {
  const latestByPayment = new Map<string, AuditApiEntry>();

  for (const entry of entries) {
    const paymentKey = `${entry.entityType}:${entry.entityId}`;
    const current = latestByPayment.get(paymentKey);

    if (!current || isNewerThan(entry, current)) {
      latestByPayment.set(paymentKey, entry);
    }
  }

  return [...latestByPayment.values()].sort(compareNewestFirst);
}

function formatEntityLabel(entityType: string, entityId: number): string {
  if (entityType.toUpperCase() === "PAYMENT") {
    return `Betalning #${entityId}`;
  }

  return `${entityType} #${entityId}`;
}

function isNewerThan(entry: AuditApiEntry, current: AuditApiEntry): boolean {
  return compareNewestFirst(entry, current) < 0;
}

function compareNewestFirst(a: AuditApiEntry, b: AuditApiEntry): number {
  const timeDiff = new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();

  if (timeDiff !== 0) {
    return timeDiff;
  }

  return b.id - a.id;
}
