import type { AuditApiEntry, AuditEntry } from "../types";
import {
  formatAmount,
  formatAuditStatus,
  formatDateTime,
  getDateTimeSortValue,
  formatEventType,
} from "./formatters";

export function toAuditEntry(raw: AuditApiEntry): AuditEntry {
  const isPayment = raw.entityType.toUpperCase() === "PAYMENT";
  return {
    id: String(raw.id),
    paymentId: isPayment ? String(raw.entityId) : null,
    beskrivning: raw.description,
    tid: formatDateTime(raw.createdAt),
    vem: raw.userName,
    handelse: formatHandelse(raw),
    status: isPayment ? formatAuditStatus(raw.status) : "-",
    statusKey: isPayment ? raw.status : null,
    referens: raw.reference?.trim() || "-",
    belopp: isPayment ? formatAmount(raw.amount, raw.currency) : "-",
    saldoFore: isPayment ? formatHistoricalBalance(raw.balanceBeforePayment, raw) : "-",
    saldoEfter: isPayment ? formatHistoricalBalance(raw.balanceAfterPayment, raw) : "-",
    franKonto: isPayment ? raw.fromAccountName ?? "-" : "-",
    betalning: formatEntityLabel(raw.entityType, raw.entityId),
  };
}

function formatHistoricalBalance(balance: number | null, entry: AuditApiEntry): string {
  if (balance != null) return formatAmount(balance, entry.currency);
  return entry.status === "COMPLETED" ? "Saknas i historiken" : "Ej genomförd";
}

export function latestAuditEntryByPayment(entries: AuditApiEntry[]): AuditApiEntry[] {
  const latestByPayment = new Map<string, AuditApiEntry>();
  const otherEvents: AuditApiEntry[] = [];

  for (const entry of entries) {
    if (entry.entityType.toUpperCase() !== "PAYMENT") {
      otherEvents.push(entry);
      continue;
    }
    const paymentKey = `${entry.entityType}:${entry.entityId}`;
    const current = latestByPayment.get(paymentKey);

    if (!current || isNewerThan(entry, current)) {
      latestByPayment.set(paymentKey, entry);
    }
  }

  return [...latestByPayment.values(), ...otherEvents].sort(compareNewestFirst);
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
  if (entityType.toUpperCase() === "USER") return `Användare #${entityId}`;

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
