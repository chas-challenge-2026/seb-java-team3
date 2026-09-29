import styles from "./AttestGrid.module.css";
import Button from "../../components/ui/buttons/Button";
import ButtonLink from "../../components/ui/buttons/ButtonLink";
import { useApprovals } from "./useApprovals";
import IconMessage from "../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../components/ui/feedback/LoadErrorMessage";
import { CircleCheck, Clock3, PartyPopper } from "lucide-react"
import Container from "../../components/ui/layout/Container";
import { formatDateTime } from "../../lib/dateTime";

export default function AttestGrid() {
  const { data, isLoading, isError, isFetching, refetch, approve } = useApprovals();

  if (isLoading) return <p>Laddar…</p>;
  if (isError) return (
    <LoadErrorMessage
      title="Kunde inte hämta attestkorgen"
      onRetry={() => refetch()}
      isRetrying={isFetching}
    />
  );
  if (!data?.length) return (
    <IconMessage
      title="Allt är klart"
      message="Inget väntar på ditt godkännande just nu."
      icon={PartyPopper}
      actions={
        <>
          <ButtonLink to="/">Till översikt</ButtonLink>
          <ButtonLink to="/audit" variant="primary">Visa historik</ButtonLink>
        </>
      }
    />
  );
  return (
    <Container maxWidth="lg">
      <section className={styles.card} aria-label="Väntande attesteringar">
        <header className={styles.header}>
          <div>
            <h2>Väntar på din attest</h2>
            <p className={styles.description}>Granska betalningarna och fatta beslut när du är redo.</p>
          </div>
          <div className={styles.count} aria-label={`${data.length} väntande attesteringar`}>
            <Clock3 size={18} aria-hidden="true" />
            <span>{data.length}</span> väntande
          </div>
        </header>

        <div className={styles.tableWrap}>
          <table className={styles.grid}>
            <thead>
              <tr>
                <th>Mottagarkonto</th>
                <th>Typ</th>
                <th>Belopp</th>
                <th>Datum</th>
                <th>Referens</th>
                <th><span className={styles.srOnly}>Åtgärd</span></th>
              </tr>
            </thead>
            <tbody>
              {data.map((m) => {
                const pending = approve.isPending && approve.variables === m.id;
                return (
                  <tr key={m.id}>
                    <td data-label="Mottagarkonto">
                      <div className={styles.recipient}>
                        <strong>{m.mottagare}</strong>
                      </div>
                    </td>
                    <td data-label="Typ"><span className={styles.type}>{m.typ}</span></td>
                    <td data-label="Belopp" className={styles.amount}>{m.belopp} <span>{m.currency}</span></td>
                    <td data-label="Datum" className={styles.date}>{formatDateTime(m.createdAt)}</td>
                    <td data-label="Referens" className={styles.reference}>{m.reference}</td>
                    <td data-label="Åtgärd">
                      <div className={styles.actions}>
                        <Button
                          onClick={() => approve.mutate(m.id)}
                          disabled={pending}
                          className={styles.approve}
                          buttonStyle="icon-only"
                          icon="check"
                          variant="primary"
                          aria-label={`Godkänn betalning till ${m.mottagare}`}
                          title={pending ? "Godkänner…" : "Godkänn"}
                        />
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <footer className={styles.footer}>
          <CircleCheck size={17} aria-hidden="true" />
          Kontrollera alltid mottagare och belopp innan du godkänner.
        </footer>
      </section>
    </Container>
  );
}
