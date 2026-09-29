import { useQuery } from "@tanstack/react-query";
import styles from "./AuditGrid.module.css";
import PaymentAuditStep from "./PaymentAuditStep";
import { paymentAuditTimelineQueryOptions } from "../queries/auditQueryOptions";
import { formatAmount } from "../utils/formatters";

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

  // Mottagare, belopp och referens gäller hela betalningen, inte enskilda steg
  const [payment] = data;

  return (
    <div id={id} className={styles.detailsPanel}>
      <dl className={styles.paymentSummary}>
        <div>
          <dt>Mottagare</dt>
          <dd className={styles.iban}>{payment.toIban ?? "-"}</dd>
        </div>
        <div>
          <dt>Belopp</dt>
          <dd>{formatAmount(payment.amount, payment.currency)}</dd>
        </div>
        <div>
          <dt>Referens</dt>
          <dd>{payment.reference?.trim() || "-"}</dd>
        </div>
      </dl>
      <ol className={styles.timeline}>
        {data.map((entry) => (
          <PaymentAuditStep key={entry.sequence} entry={entry} />
        ))}
      </ol>
    </div>
  );
}
