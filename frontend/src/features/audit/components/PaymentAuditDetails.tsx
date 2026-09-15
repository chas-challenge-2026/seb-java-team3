import { useQuery } from "@tanstack/react-query";
import styles from "./AuditGrid.module.css";
import PaymentAuditStep from "./PaymentAuditStep";
import { paymentAuditTimelineQueryOptions } from "../queries/auditQueryOptions";

type PaymentAuditDetailsProps = {
  paymentId: string;
  id: string;
};

export default function PaymentAuditDetails({
  paymentId,
  id,
}: PaymentAuditDetailsProps) {
  const { data, isPending, isError } = useQuery(
    paymentAuditTimelineQueryOptions(paymentId),
  );

  if (isPending) {
    return (
      <div id={id} className={styles.detailsPanel}>
        <p className={styles.stateText}>Laddar händelsekedja...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div id={id} className={styles.detailsPanel}>
        <p className={styles.stateText} role="alert">
          Kunde inte hämta händelsekedjan.
        </p>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div id={id} className={styles.detailsPanel}>
        <p className={styles.stateText}>Inga händelser hittades för betalningen.</p>
      </div>
    );
  }

  return (
    <div id={id} className={styles.detailsPanel}>
      <ol className={styles.timeline}>
        {data.map((entry) => (
          <PaymentAuditStep key={entry.sequence} entry={entry} />
        ))}
      </ol>
    </div>
  );
}
