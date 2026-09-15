import { useQuery } from "@tanstack/react-query";
import { useParams } from "@tanstack/react-router";
import Container from "../../../components/ui/layout/Container";
import { paymentAuditTimelineQueryOptions } from "../queries/auditQueryOptions";
import {
  formatAuditStatus,
  formatDateTime,
  formatEventType,
} from "../utils/formatters";
import styles from "./PaymentAuditTimelinePage.module.css";

export default function PaymentAuditTimelinePage() {
  const { paymentId } = useParams({ from: "/auth/payments/$paymentId/audit" });

  const { data, isPending, isError } = useQuery(
    paymentAuditTimelineQueryOptions(paymentId),
  );

  return (
    <Container maxWidth="xl" variant="white" style={{ marginTop: "10rem" }}>
      <h1 className={styles.title}>Betalning #{paymentId}</h1>

      {isPending ? (
        <p>Laddar händelsekedja…</p>
      ) : isError ? (
        <p role="alert">Kunde inte hämta händelsekedjan.</p>
      ) : data.length === 0 ? (
        <p>Inga händelser hittades för betalningen.</p>
      ) : (
        <ol className={styles.timeline}>
          {data.map((entry) => (
            <li key={entry.sequence} className={styles.item}>
              <div className={styles.order}>{entry.order}</div>
              <div className={styles.content}>
                <div className={styles.meta}>
                  <span>{entry.actor}</span>
                  <span>{formatDateTime(entry.timestamp)}</span>
                  <span>{formatAuditStatus(entry.status)}</span>
                  {entry.stepNumber && <span>Steg {entry.stepNumber}</span>}
                </div>
                <h2 className={styles.event}>{formatEventType(entry.eventType)}</h2>
                <p className={styles.description}>{entry.description}</p>
                <p className={styles.reference}>Referens: {entry.reference?.trim() || "-"}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </Container>
  );
}
