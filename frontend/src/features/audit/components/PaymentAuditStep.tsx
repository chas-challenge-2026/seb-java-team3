import styles from "./AuditGrid.module.css";
import type { PaymentAuditTimelineEntry } from "../types";
import { formatDateTime, formatEventType, formatTimelineComment } from "../utils/formatters";

type PaymentAuditStepProps = {
  entry: PaymentAuditTimelineEntry;
};

export default function PaymentAuditStep({ entry }: PaymentAuditStepProps) {
  const comment = formatTimelineComment(entry);

  return (
    <li className={styles.timelineItem}>
      <div className={styles.order}>{entry.order}</div>
      <div className={styles.timelineContent}>
        <div className={styles.stepHeader}>
          <h3 className={styles.event}>{formatEventType(entry.eventType)}</h3>
          {entry.stepNumber !== null && (
            <span className={styles.stepBadge}>Steg {entry.stepNumber}</span>
          )}
        </div>
        <p className={styles.meta}>
          <span>{entry.actor}</span>
          <time dateTime={entry.timestamp ?? undefined}>
            {formatDateTime(entry.timestamp)}
          </time>
        </p>
        {comment && (
          <p className={styles.comment}>
            <span className={styles.commentLabel}>Meddelande</span>
            {comment}
          </p>
        )}
      </div>
    </li>
  );
}
