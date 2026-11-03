import { useState } from "react";
import { Clock3 } from "lucide-react";
import styles from "./AuditGrid.module.css";
import AuditGridRow from "./AuditGridRow";
import StatusFilter from "./StatusFilter";
import type { AuditEntry } from "../types";

type AuditGridProps = {
  entries: AuditEntry[];
};

export default function AuditGrid({ entries }: AuditGridProps) {
  const [expandedPayments, setExpandedPayments] = useState<Set<string>>(
    () => new Set(),
  );
  const [statusFilter, setStatusFilter] = useState<string | null>(null);

  const visibleEntries = statusFilter
    ? entries.filter((entry) => entry.statusKey === statusFilter)
    : entries;

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
            Senaste händelsen per betalning samt användarhändelser. Öppna en betalning för saldon, referens och historik.
          </p>
          <StatusFilter
            statuses={entries.map((entry) => entry.statusKey)}
            value={statusFilter}
            onChange={setStatusFilter}
          />
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
              <th>Gäller</th>
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
                const isExpanded = entry.paymentId !== null && expandedPayments.has(entry.paymentId);
                const detailsId = `payment-audit-${entry.id}`;

                return (
                  <AuditGridRow
                    key={entry.id}
                    entry={entry}
                    detailsId={detailsId}
                    isExpanded={isExpanded}
                    onToggle={() => { if (entry.paymentId !== null) togglePayment(entry.paymentId); }}
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
