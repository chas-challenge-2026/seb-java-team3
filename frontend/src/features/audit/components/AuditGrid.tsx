import { useState } from "react";
import { Clock3 } from "lucide-react";
import styles from "./AuditGrid.module.css";
import AuditGridRow from "./AuditGridRow";
import type { AuditEntry } from "../types";

type AuditGridProps = {
  entries: AuditEntry[];
};

// Statusfiltret. Värdena är betalningens status från backend; null betyder alla.
const STATUS_FILTERS: { key: string | null; label: string }[] = [
  { key: null, label: "Alla" },
  { key: "PENDING_APPROVAL", label: "Väntar attest" },
  { key: "COMPLETED", label: "Genomförda" },
  { key: "REJECTED", label: "Avvisade" },
];

export default function AuditGrid({ entries }: AuditGridProps) {
  const [expandedPayments, setExpandedPayments] = useState<Set<string>>(
    () => new Set(),
  );
  const [statusFilter, setStatusFilter] = useState<string | null>(null);

  const visibleEntries = statusFilter
    ? entries.filter((entry) => entry.statusKey === statusFilter)
    : entries;

  function countFor(key: string | null): number {
    return key ? entries.filter((entry) => entry.statusKey === key).length : entries.length;
  }

  function togglePayment(paymentId: string) {
    setExpandedPayments((current) => {
      const next = new Set(current);

      if (next.has(paymentId)) {
        next.delete(paymentId);
      } else {
        next.add(paymentId);
      }

      return next;
    });
  }

  return (
    <section className={styles.card} aria-label="Auditlogg">
      <header className={styles.header}>
        <div>
          <h2>Auditlogg</h2>
          <p className={styles.description}>
            Senaste händelsen per betalning. Öppna en rad för saldon, referens och historik.
          </p>
          <div className={styles.filters} role="group" aria-label="Filtrera på status">
            {STATUS_FILTERS.map((filter) => (
              <button
                key={filter.label}
                type="button"
                className={styles.filterButton}
                aria-pressed={statusFilter === filter.key}
                onClick={() => setStatusFilter(filter.key)}
              >
                {filter.label} <span>{countFor(filter.key)}</span>
              </button>
            ))}
          </div>
        </div>
        <div className={styles.count} aria-label={`${visibleEntries.length} händelser`}>
          <Clock3 size={18} aria-hidden="true" />
          <span>{visibleEntries.length}</span> händelser
        </div>
      </header>

      <div className={styles.tableWrap}>
        <table className={`${styles.grid} ${styles.contentSizedGrid}`}>
          <thead>
            <tr>
              <th>Tid</th>
              <th>Vem</th>
              <th>Händelse</th>
              <th>Status</th>
              <th>Belopp</th>
              <th>Från konto</th>
              <th>Betalning</th>
            </tr>
          </thead>
          <tbody>
            {visibleEntries.length === 0 ? (
              <tr>
                <td colSpan={7} className={styles.stateText}>
                  Inga betalningar med den statusen.
                </td>
              </tr>
            ) : (
              visibleEntries.map((entry) => {
                const isExpanded = expandedPayments.has(entry.paymentId);
                const detailsId = `payment-audit-${entry.paymentId}`;

                return (
                  <AuditGridRow
                    key={entry.id}
                    entry={entry}
                    detailsId={detailsId}
                    isExpanded={isExpanded}
                    onToggle={() => togglePayment(entry.paymentId)}
                  />
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
