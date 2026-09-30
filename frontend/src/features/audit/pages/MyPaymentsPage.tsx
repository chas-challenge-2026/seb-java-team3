import { useQuery } from "@tanstack/react-query";
import { CreditCard } from "lucide-react";
import ButtonLink from "../../../components/ui/buttons/ButtonLink";
import IconMessage from "../../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../../components/ui/feedback/LoadErrorMessage";
import LoadingMessage from "../../../components/ui/feedback/LoadingMessage";
import Container from "../../../components/ui/layout/Container";
import { myPaymentsQueryOptions } from "../queries/auditQueryOptions";
import { formatAmount, formatAuditStatus, formatDateTime } from "../utils/formatters";
import styles from "../components/AuditGrid.module.css";

export default function MyPaymentsPage() {
  const { data, isPending, isError, isFetching, refetch } = useQuery(myPaymentsQueryOptions);

  return (
    <Container maxWidth="xl" style={{ marginTop: "2rem" }}>
      {isPending ? (
        <LoadingMessage message="Laddar dina betalningar…" />
      ) : isError ? (
        <LoadErrorMessage
          title="Kunde inte hämta dina betalningar"
          onRetry={() => refetch()}
          isRetrying={isFetching}
        />
      ) : data.length === 0 ? (
        <IconMessage
          title="Inga betalningar än"
          message="Betalningar du skapar visas här, med status och detaljer."
          icon={CreditCard}
          actions={
            <>
              <ButtonLink to="/">Till översikt</ButtonLink>
              <ButtonLink to="/payments/new" variant="primary">Ny betalning</ButtonLink>
            </>
          }
        />
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
