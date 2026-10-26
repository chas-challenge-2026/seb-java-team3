import type { AuditApiEntry, AuditEntry } from "../types";
import {
  formatAmount,
  formatAuditStatus,
  formatDateTime,
  getDateTimeSortValue,
  formatEventType,
} from "./formatters";

export function toAuditEntry(raw: AuditApiEntry): AuditEntry {
  return {
    id: String(raw.id),
    paymentId: String(raw.entityId),
    tid: formatDateTime(raw.createdAt),
    vem: raw.userName,
    handelse: formatHandelse(raw),
    status: formatAuditStatus(raw.status),
    statusKey: raw.status,
    referens: raw.reference?.trim() || "-",
    belopp: formatAmount(raw.amount, raw.currency),
    saldoFore: formatHistoricalBalance(raw.balanceBeforePayment, raw),
    saldoEfter: formatHistoricalBalance(raw.balanceAfterPayment, raw),
    franKonto: raw.fromAccountName ?? "-",
    betalning: formatEntityLabel(raw.entityType, raw.entityId),
  };
}

function formatHistoricalBalance(balance: number | null, entry: AuditApiEntry): string {
  if (balance != null) return formatAmount(balance, entry.currency);
  return entry.status === "COMPLETED" ? "Saknas i historiken" : "Ej genomförd";
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

// En betalning under tröskeln genomförs direkt när den skapas, utan attest. Skapandet är då också
// godkännandet, så händelsen visas som det.
function formatHandelse(raw: AuditApiEntry): string {
  if (raw.action === "CREATE_PAYMENT" && raw.autoApproved) {
    return "Automatiskt godkänd";
  }

  return formatEventType(raw.action);
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
  const aTime = getDateTimeSortValue(a.createdAt);
  const bTime = getDateTimeSortValue(b.createdAt);
  const timeDiff = (bTime ?? 0) - (aTime ?? 0);

  if (timeDiff !== 0) {
    return timeDiff;
  }

  return b.id - a.id;
}
