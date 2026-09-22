import { useQuery } from "@tanstack/react-query";
import { CreditCard } from "lucide-react";
import Container from "../../../components/ui/layout/Container";
import { myPaymentsQueryOptions } from "../queries/auditQueryOptions";
import { formatAmount, formatAuditStatus, formatDateTime } from "../utils/formatters";
import styles from "../components/AuditGrid.module.css";

export default function MyPaymentsPage() {
  const { data, isPending, isError } = useQuery(myPaymentsQueryOptions);

  return (
    <Container maxWidth="xl" style={{ marginTop: "2rem" }}>
      {isPending ? (
        <p>Laddar dina betalningar…</p>
      ) : isError ? (
        <p role="alert">Kunde inte hämta dina betalningar. Försök igen senare.</p>
      ) : data.length === 0 ? (
        <p>Du har inte skapat några betalningar än.</p>
      ) : (
        <section className={styles.card} aria-label="Mina betalningar">
          <header className={styles.header}>
            <div>
              <h2>Mina betalningar</h2>
              <p className={styles.description}>
                Följ status och detaljer för betalningar du har skapat.
              </p>
            </div>
            <div className={styles.count} aria-label={`${data.length} betalningar`}>
              <CreditCard size={18} aria-hidden="true" />
              <span>{data.length}</span> betalningar
            </div>
          </header>

          <div className={styles.tableWrap}>
            <table className={styles.grid}>
              <thead>
                <tr>
                  <th>Skapad</th>
                  <th>Referens</th>
                  <th>Belopp</th>
                  <th>Mottagare</th>
                  <th>Status</th>
                  <th>Utförd</th>
                </tr>
              </thead>
              <tbody>
                {data.map((payment) => (
                  <tr key={payment.paymentId}>
                    <td data-label="Skapad" className={styles.date}>
                      {formatDateTime(payment.createdAt)}
                    </td>
                    <td data-label="Referens" className={styles.reference}>
                      {payment.reference?.trim() || "-"}
                    </td>
                    <td data-label="Belopp" className={styles.amount}>
                      {formatAmount(payment.amount, payment.currency)}
                    </td>
                    <td data-label="Mottagare" className={styles.reference}>
                      {payment.toIban ?? "-"}
                    </td>
                    <td data-label="Status">
                      <span className={styles.status}>{formatAuditStatus(payment.status)}</span>
                    </td>
                    <td data-label="Utförd" className={styles.date}>
                      {formatDateTime(payment.executedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </Container>
  );
}
