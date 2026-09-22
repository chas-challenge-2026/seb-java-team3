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
        <td data-label="Tid" className={styles.date}>
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
        </td>
        <td data-label="Status">
          <span className={styles.status}>{entry.status}</span>
        </td>
        <td data-label="Referens" className={styles.reference}>
          {entry.referens}
        </td>
        <td data-label="Belopp" className={styles.amount}>
          {entry.belopp}
        </td>
        <td data-label="Betalning" className={styles.paymentCell}>
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

function getInitials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("") || "?";
}
