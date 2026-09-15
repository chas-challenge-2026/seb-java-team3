import { useState } from "react";
import styles from "./AuditGrid.module.css";
import type { PaymentAuditTimelineEntry } from "../types";
import {
  formatAuditStatus,
  formatDateTime,
  formatEventType,
} from "../utils/formatters";

type PaymentAuditStepProps = {
  entry: PaymentAuditTimelineEntry;
};

export default function PaymentAuditStep({ entry }: PaymentAuditStepProps) {
  const [isDetailsOpen, setIsDetailsOpen] = useState(false);
  const detailsId = `audit-step-${entry.sequence}`;

  return (
    <li className={styles.timelineItem}>
      <div className={styles.order}>{entry.order}</div>
      <div className={styles.timelineContent}>
        <div className={styles.meta}>
          <span>{entry.actor}</span>
          <span>{formatDateTime(entry.timestamp)}</span>
          <span>{formatAuditStatus(entry.status)}</span>
          {entry.stepNumber && <span>Steg {entry.stepNumber}</span>}
        </div>
        <div className={styles.stepHeader}>
          <h2 className={styles.event}>{formatEventType(entry.eventType)}</h2>
          <button
            type="button"
            className={styles.moreInfoButton}
            aria-expanded={isDetailsOpen}
            aria-controls={detailsId}
            onClick={() => setIsDetailsOpen((current) => !current)}
          >
            {isDetailsOpen ? "Mindre info" : "Mer info"}
          </button>
        </div>
        {isDetailsOpen && (
          <div id={detailsId} className={styles.stepDetails}>
            <p>{entry.description}</p>
            <p>Referens: {entry.reference?.trim() || "-"}</p>
          </div>
        )}
      </div>
    </li>
  );
}
