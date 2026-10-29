import { useId, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import type { MyPaymentStatus } from "../schema";
import {
  formatAmount,
  formatAttestant,
  formatAuditStatus,
  formatDateTime,
  formatRejectionReason,
} from "../utils/formatters";
import styles from "./AuditGrid.module.css";
import rowStyles from "./MyPaymentRow.module.css";

export default function MyPaymentRow({ payment }: { payment: MyPaymentStatus }) {
  const [isExpanded, setIsExpanded] = useState(false);
  const detailsId = useId();
  const isRejected = payment.status === "REJECTED";
  const reason = isRejected ? formatRejectionReason(payment)?.trim() : null;
  const ChevronIcon = isExpanded ? ChevronDown : ChevronRight;

  return (
    <>
      <tr>
        <td data-label="Skapad" className={`${styles.date} ${styles.noWrap}`}>
          {formatDateTime(payment.createdAt)}
        </td>
        <td data-label="Referens" className={styles.reference}>
          {payment.reference?.trim() || "-"}
        </td>
        <td data-label="Belopp" className={`${styles.amount} ${styles.noWrap}`}>
          {formatAmount(payment.amount, payment.currency)}
        </td>
        <td data-label="Från konto" className={`${styles.date} ${styles.noWrap}`}>
          {payment.fromAccountName ?? "-"}
        </td>
        <td data-label="Mottagarkonto" className={`${styles.reference} ${styles.noWrap}`}>
          {payment.toIban ?? "-"}
        </td>
        <td data-label="Status" className={styles.noWrap}>
          <div className={rowStyles.statusContent}>
            <span className={styles.status}>{formatAuditStatus(payment.status)}</span>
            {isRejected && (
              <button
                type="button"
                className={rowStyles.toggle}
                aria-expanded={isExpanded}
                aria-controls={isExpanded ? detailsId : undefined}
                aria-label={`${isExpanded ? "Dölj" : "Visa"} avvisningsorsak för betalning #${payment.paymentId}`}
                onClick={() => setIsExpanded((expanded) => !expanded)}
              >
                <ChevronIcon size={16} aria-hidden="true" />
                {isExpanded ? "Dölj orsak" : "Visa orsak"}
              </button>
            )}
          </div>
        </td>
        <td data-label="Attestant" className={styles.date}>
          {formatAttestant(payment)}
        </td>
        <td data-label="Utförd" className={`${styles.date} ${styles.noWrap}`}>
          {formatDateTime(payment.executedAt)}
        </td>
      </tr>
      {isRejected && isExpanded && (
        <tr className={styles.detailsRow}>
          <td colSpan={8}>
            <div id={detailsId} className={styles.detailsPanel}>
              <h3 className={rowStyles.heading}>Avvisningsorsak</h3>
              <p className={rowStyles.reason}>
                {reason || "Ingen avvisningsorsak har angetts."}
              </p>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}
