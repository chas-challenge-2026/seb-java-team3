import { useId, useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import type { MyPaymentStatus } from "../schema";
import {
  formatAmount,
  formatAttestant,
  formatDateTime,
  formatMyPaymentStatus,
  formatRejectionReason,
} from "../utils/formatters";
import styles from "./AuditGrid.module.css";
import rowStyles from "./MyPaymentRow.module.css";

export default function MyPaymentRow({ payment }: { payment: MyPaymentStatus }) {
  const [isExpanded, setIsExpanded] = useState(false);
  const detailsId = useId();
  const isRejected = payment.status === "REJECTED";
  const reason = isRejected ? formatRejectionReason(payment)?.trim() : null;
  const ChevronIcon = isExpanded ? ChevronUp : ChevronDown;

  function toggle() {
    setIsExpanded((expanded) => !expanded);
  }

  return (
    <>
      {/* Klick var som helst på en avvisad rad fäller ut orsaken. Tangentbord och skärmläsare
          använder knappen i statuskolumnen, så raden behåller sin tabellsemantik. */}
      <tr
        className={isRejected ? styles.clickableRow : undefined}
        onClick={isRejected ? toggle : undefined}
      >
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
          {isRejected ? (
            <button
              type="button"
              className={`${styles.status} ${rowStyles.statusToggle}`}
              aria-expanded={isExpanded}
              aria-controls={isExpanded ? detailsId : undefined}
              aria-label={`${isExpanded ? "Dölj" : "Visa"} avvisningsorsak för betalning #${payment.paymentId}`}
              onClick={(event) => {
                // Raden hanterar också klick, så annars skulle den fällas ut och ihop direkt
                event.stopPropagation();
                toggle();
              }}
            >
              {formatMyPaymentStatus(payment)}
              <ChevronIcon size={14} aria-hidden="true" />
            </button>
          ) : (
            <span className={styles.status}>{formatMyPaymentStatus(payment)}</span>
          )}
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
