import { useState } from "react";
import styles from "./AttestGrid.module.css";
import Button from "../../components/ui/buttons/Button";
import ButtonLink from "../../components/ui/buttons/ButtonLink";
import { useApprovals } from "./useApprovals";
import IconMessage from "../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../components/ui/feedback/LoadErrorMessage";
import LoadingMessage from "../../components/ui/feedback/LoadingMessage";
import { CircleCheck, Clock3, PartyPopper, TriangleAlert } from "lucide-react"
import Container from "../../components/ui/layout/Container";
import { formatDateTime } from "../../lib/dateTime";
import { isApiError } from "../../error/api.error";
import type { AttestSteg } from "./schema";

type ApprovalFeedback = {
  type: "success" | "error";
  text: string;
};

export default function AttestGrid() {
  const { data, isLoading, isError, isFetching, refetch, approve } = useApprovals();
  const [feedback, setFeedback] = useState<ApprovalFeedback | null>(null);

  function handleApprove(step: AttestSteg) {
    setFeedback(null);
    approve.mutate(step.id, {
      onSuccess: () => {
        setFeedback({
          type: "success",
          text: `Betalningen till ${step.mottagare} på ${formatBelopp(step)} är godkänd.`,
        });
      },
      onError: (error) => {
        setFeedback({
          type: "error",
          text: `Kunde inte godkänna betalningen till ${step.mottagare}. ${
            isApiError(error) ? error.message : "Försök igen."
          }`,
        });
      },
    });
  }

  // När sista betalningen godkänts visas bekräftelsen i "Allt är klart"-kortet istället för ovanför
  const isAllApproved = !isLoading && !isError && !data?.length;
  const showFeedbackInCard = isAllApproved && feedback?.type === "success";

  let content;

  if (isLoading) {
    content = <LoadingMessage message="Laddar attestkorgen…" />;
  } else if (isError) {
    content = (
      <LoadErrorMessage
        title="Kunde inte hämta attestkorgen"
        onRetry={() => refetch()}
        isRetrying={isFetching}
      />
    );
  } else if (!data?.length) {
    content = (
      <IconMessage
        title="Allt är klart"
        message={
          showFeedbackInCard
            ? `${feedback.text} Inget mer väntar på ditt godkännande.`
            : "Inga betalningar väntar på godkännande just nu."
        }
        icon={PartyPopper}
        actions={
          <>
            <ButtonLink to="/">Till översikt</ButtonLink>
            <ButtonLink to="/audit" variant="primary">Visa historik</ButtonLink>
          </>
        }
      />
    );
  } else {
    content = (
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
                          onClick={() => handleApprove(m)}
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

  const FeedbackIcon = feedback?.type === "error" ? TriangleAlert : CircleCheck;

  return (
    <>
      {/* Utanför villkoren ovan så att meddelandet läses upp även när sista
          betalningen godkänts och tabellen byts mot det tomma läget. Då döljs
          det visuellt eftersom samma text visas i kortet */}
      <div className={styles.feedbackRegion} aria-live="polite">
        {feedback && (
          <p
            className={
              showFeedbackInCard
                ? styles.srOnly
                : `${styles.feedback} ${
                    feedback.type === "error" ? styles.feedbackError : styles.feedbackSuccess
                  }`
            }
          >
            <FeedbackIcon size={18} aria-hidden="true" />
            {feedback.text}
          </p>
        )}
      </div>
      {content}
    </>
  );
}

function formatBelopp(step: AttestSteg): string {
  return step.belopp.toLocaleString("sv-SE", {
    style: "currency",
    currency: step.currency,
  });
}
