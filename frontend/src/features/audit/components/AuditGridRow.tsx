import { ChevronDown, ChevronRight } from "lucide-react";
import styles from "./AuditGrid.module.css";
import PaymentAuditDetails from "./PaymentAuditDetails";
import type { AuditEntry } from "../types";

type AuditGridRowProps = {
  entry: AuditEntry;
  detailsId: string;
  isExpanded: boolean;
  onToggle: () => void;
};

export default function AuditGridRow({
  entry,
  detailsId,
  isExpanded,
  onToggle,
}: AuditGridRowProps) {
  const ChevronIcon = isExpanded ? ChevronDown : ChevronRight;

  return (
    <>
      <tr
        className={styles.clickableRow}
        tabIndex={0}
        role="button"
        aria-expanded={isExpanded}
        aria-controls={detailsId}
        onClick={onToggle}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            onToggle();
          }
        }}
      >
        <td>{entry.tid}</td>
        <td>{entry.vem}</td>
        <td>{entry.handelse}</td>
        <td>{entry.status}</td>
        <td>{entry.referens}</td>
        <td>{entry.belopp}</td>
        <td className={styles.paymentCell}>
          <span className={styles.paymentLabel}>
            <ChevronIcon size={18} aria-hidden="true" />
            {entry.betalning}
          </span>
        </td>
      </tr>
      {isExpanded && (
        <tr className={styles.detailsRow}>
          <td colSpan={7}>
            <PaymentAuditDetails paymentId={entry.paymentId} id={detailsId} />
          </td>
        </tr>
      )}
    </>
  );
}
