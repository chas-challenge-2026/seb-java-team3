import { useQuery } from "@tanstack/react-query";
import Container from "../../../components/ui/layout/Container";
import { myPaymentsQueryOptions } from "../queries/auditQueryOptions";
import { formatAmount, formatAuditStatus, formatDateTime } from "../utils/formatters";
import styles from "../components/AuditGrid.module.css";

export default function MyPaymentsPage() {
  const { data, isPending, isError } = useQuery(myPaymentsQueryOptions);

  return (
    <Container maxWidth="xl" variant="white" style={{ marginTop: "10rem" }}>
      {isPending ? (
        <p>Laddar dina betalningar…</p>
      ) : isError ? (
        <p role="alert">Kunde inte hämta dina betalningar. Försök igen senare.</p>
      ) : data.length === 0 ? (
        <p>Du har inte skapat några betalningar än.</p>
      ) : (
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
                <td>{formatDateTime(payment.createdAt)}</td>
                <td>{payment.reference?.trim() || "-"}</td>
                <td>{formatAmount(payment.amount, payment.currency)}</td>
                <td>{payment.toIban ?? "-"}</td>
                <td>{formatAuditStatus(payment.status)}</td>
                <td>{formatDateTime(payment.executedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Container>
  );
}
