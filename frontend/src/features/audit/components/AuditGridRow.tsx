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
  const actorInitials = getInitials(entry.vem);
  const isPayment = entry.paymentId !== null;

  return (
    <>
      <tr
        className={isPayment ? styles.clickableRow : undefined}
        tabIndex={isPayment ? 0 : undefined}
        role={isPayment ? "button" : undefined}
        aria-expanded={isPayment ? isExpanded : undefined}
        aria-controls={isPayment ? detailsId : undefined}
        onClick={isPayment ? onToggle : undefined}
        onKeyDown={isPayment ? (event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            onToggle();
          }
        } : undefined}
      >
        <td data-label="Tid" className={`${styles.date} ${styles.noWrap}`}>
          {entry.tid}
        </td>
        <td data-label="Vem" className={styles.actor}>
          <span className={styles.actorContent}>
            <span className={styles.actorAvatar} aria-hidden="true">
              {actorInitials}
            </span>
            <span>{entry.vem}</span>
          </span>
        </td>
        <td data-label="Händelse" className={styles.eventCell}>
          {entry.handelse}
          {!isPayment && <p>{entry.beskrivning}</p>}
        </td>
        <td data-label="Status" className={styles.noWrap}>
          <span className={styles.status}>{entry.status}</span>
        </td>
        <td data-label="Belopp" className={`${styles.amount} ${styles.noWrap}`}>
          {entry.belopp}
        </td>
        <td data-label="Från konto" className={`${styles.date} ${styles.noWrap}`}>
          {entry.franKonto}
        </td>
        <td data-label="Betalning" className={styles.paymentCell}>
          <span className={styles.paymentLabel}>
            {isPayment && <ChevronIcon size={18} aria-hidden="true" />}
            {entry.betalning}
          </span>
        </td>
      </tr>
      {isExpanded && entry.paymentId !== null && (
        <tr className={styles.detailsRow}>
          <td colSpan={7}>
            <PaymentAuditDetails
              paymentId={entry.paymentId}
              id={detailsId}
              saldoFore={entry.saldoFore}
              saldoEfter={entry.saldoEfter}
            />
          </td>
        </tr>
      )}
    </>
  );
}

function getInitials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("") || "?";
}
