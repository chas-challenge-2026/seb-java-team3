import { useState } from "react";
import { Clock3 } from "lucide-react";
import styles from "./AuditGrid.module.css";
import AuditGridRow from "./AuditGridRow";
import type { AuditEntry } from "../types";

type AuditGridProps = {
  entries: AuditEntry[];
};

export default function AuditGrid({ entries }: AuditGridProps) {
  const [expandedPayments, setExpandedPayments] = useState<Set<string>>(
    () => new Set(),
  );

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
            Senaste händelsen per betalning med full historik vid behov.
          </p>
        </div>
        <div className={styles.count} aria-label={`${entries.length} händelser`}>
          <Clock3 size={18} aria-hidden="true" />
          <span>{entries.length}</span> händelser
        </div>
      </header>

      <div className={styles.tableWrap}>
        <table className={styles.grid}>
          <thead>
            <tr>
              <th>Tid</th>
              <th>Vem</th>
              <th>Händelse</th>
              <th>Status</th>
              <th>Referens</th>
              <th>Belopp</th>
              <th>Betalning</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => {
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
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
