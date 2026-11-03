import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CreditCard } from "lucide-react";
import ButtonLink from "../../../components/ui/buttons/ButtonLink";
import IconMessage from "../../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../../components/ui/feedback/LoadErrorMessage";
import LoadingMessage from "../../../components/ui/feedback/LoadingMessage";
import Container from "../../../components/ui/layout/Container";
import { myPaymentsQueryOptions } from "../queries/auditQueryOptions";
import MyPaymentRow from "../components/MyPaymentRow";
import StatusFilter from "../components/StatusFilter";
import { useMarkChangedPaymentsSeen } from "../useChangedPaymentCount";
import styles from "../components/AuditGrid.module.css";

export default function MyPaymentsPage() {
  const { data, dataUpdatedAt, isPending, isError, isFetching, refetch } = useQuery(myPaymentsQueryOptions);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);
  const { mutate: markSeen } = useMarkChangedPaymentsSeen();

  // Varje gång listan har hämtats har initiatorn sett besluten i den, så badgen nollas
  useEffect(() => {
    if (dataUpdatedAt > 0) {
      markSeen();
    }
  }, [dataUpdatedAt, markSeen]);

  const visiblePayments = statusFilter
    ? data?.filter((payment) => payment.status === statusFilter) ?? []
    : data ?? [];

  return (
    <Container maxWidth="xxl">
      <h1 style={{textAlign: "center", marginBottom: "2rem"}}>Mina betalningar</h1>
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
                Följ status och se vem som ska attestera betalningar du har skapat.
              </p>
              <StatusFilter
                statuses={data.map((payment) => payment.status)}
                value={statusFilter}
                onChange={setStatusFilter}
              />
            </div>
            <div className={styles.count} aria-label={`${visiblePayments.length} betalningar`}>
              <CreditCard size={18} aria-hidden="true" />
              <span>{visiblePayments.length}</span> betalningar
            </div>
          </header>

          <div className={styles.tableWrap}>
            <table className={`${styles.grid} ${styles.contentSizedGrid}`}>
              <thead>
                <tr>
                  <th>Skapad</th>
                  <th>Referens</th>
                  <th>Belopp</th>
                  <th>Från konto</th>
                  <th>Mottagarkonto</th>
                  <th>Status</th>
                  <th>Attestant</th>
                  <th>Utförd</th>
                </tr>
              </thead>
              <tbody>
                {visiblePayments.length === 0 ? (
                  <tr>
                    <td colSpan={8} className={styles.stateText}>
                      Inga betalningar med den statusen.
                    </td>
                  </tr>
                ) : (
                  visiblePayments.map((payment) => (
                    <MyPaymentRow key={payment.paymentId} payment={payment} />
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </Container>
  );
}
