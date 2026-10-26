import { Fragment, useState } from "react";
import styles from "./AttestGrid.module.css";
import Button from "../../components/ui/buttons/Button";
import Input from "../../components/ui/forms/Input";
import ButtonLink from "../../components/ui/buttons/ButtonLink";
import { useApprovals } from "./useApprovals";
import IconMessage from "../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../components/ui/feedback/LoadErrorMessage";
import LoadingMessage from "../../components/ui/feedback/LoadingMessage";
import { CircleCheck, Clock3, PartyPopper, TriangleAlert } from "lucide-react"
import Container from "../../components/ui/layout/Container";
import { formatDateTime } from "../../lib/dateTime";
import { formatAmount } from "../audit/utils/formatters";
import { isApiError } from "../../error/api.error";
import type { AttestSteg } from "./schema";

// Samma gräns som backend (ApprovalService.MAX_COMMENT_LENGTH) och kolumnen approval_steps.comment
const MAX_COMMENT_LENGTH = 255;

type ApprovalFeedback = {
  type: "success" | "error";
  text: string;
};

export default function AttestGrid() {
  const { data, isLoading, isError, isFetching, refetch, approve, reject } = useApprovals();
  const [feedback, setFeedback] = useState<ApprovalFeedback | null>(null);
  // Steget vars avvisning håller på att fyllas i, och anledningen som skrivits
  const [rejectingStepId, setRejectingStepId] = useState<number | null>(null);
  const [rejectComment, setRejectComment] = useState("");

  function startReject(step: AttestSteg) {
    setFeedback(null);
    setRejectComment("");
    setRejectingStepId(step.id);
  }

  function cancelReject() {
    setRejectingStepId(null);
    setRejectComment("");
  }

  function handleReject(step: AttestSteg) {
    setFeedback(null);
    reject.mutate({ stepId: step.id, comment: rejectComment }, {
      onSuccess: () => {
        cancelReject();
        setFeedback({
          type: "success",
          text: `Betalningen till ${step.mottagare} på ${formatAmount(step.belopp, step.currency)} är avvisad.`,
        });
      },
      onError: (error) => {
        setFeedback({
          type: "error",
          text: `Kunde inte avvisa betalningen till ${step.mottagare}. ${
            isApiError(error) ? error.message : "Försök igen."
          }`,
        });
      },
    });
  }

  function handleApprove(step: AttestSteg) {
    setFeedback(null);
    approve.mutate(step.id, {
      onSuccess: () => {
        setFeedback({
          type: "success",
          text: `Betalningen till ${step.mottagare} på ${formatAmount(step.belopp, step.currency)} är godkänd.`,
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
    <Container maxWidth="xxl">
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
          <table className={`${styles.grid} ${styles.contentSized}`}>
            <thead>
              <tr>
                <th>Mottagarkonto</th>
                <th>Från konto</th>
                <th>Typ</th>
                <th>Belopp</th>
                <th>Datum</th>
                <th>Skapad av</th>
                <th>Referens</th>
                <th><span className={styles.srOnly}>Åtgärd</span></th>
              </tr>
            </thead>
            <tbody>
              {data.map((m) => {
                const pending = approve.isPending && approve.variables === m.id;
                const rejecting = rejectingStepId === m.id;
                const rejectPending = reject.isPending && reject.variables?.stepId === m.id;
                const reasonId = `reject-reason-${m.id}`;
                return (
                  <Fragment key={m.id}>
                  <tr>
                    <td data-label="Mottagarkonto" className={styles.noWrap}>
                      <div className={styles.recipient}>
                        <strong>{m.mottagare}</strong>
                      </div>
                    </td>
                    <td data-label="Från konto" className={`${styles.date} ${styles.noWrap}`}>
                      {m.fromAccountName ?? "-"}
                      <span className={styles.balanceAfterPayment}>
                        Saldo efter betalning: {m.balanceAfterPayment == null
                          ? "Kan inte beräknas"
                          : formatAmount(m.balanceAfterPayment, m.currency)}
                      </span>
                    </td>
                    <td data-label="Typ" className={styles.noWrap}><span className={styles.type}>{m.typ}</span></td>
                    <td data-label="Belopp" className={`${styles.amount} ${styles.noWrap}`}>
                      {formatAmount(m.belopp, m.currency)}
                    </td>
                    <td data-label="Datum" className={`${styles.date} ${styles.noWrap}`}>
                      {formatDateTime(m.createdAt)}
                    </td>
                    <td data-label="Skapad av" className={`${styles.date} ${styles.noWrap}`}>{m.initiatorName ?? "-"}</td>
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
                        <Button
                          onClick={() => (rejecting ? cancelReject() : startReject(m))}
                          disabled={pending || rejectPending}
                          className={styles.reject}
                          buttonStyle="icon-only"
                          icon="x"
                          variant="secondary"
                          aria-label={`Avvisa betalning till ${m.mottagare}`}
                          aria-expanded={rejecting}
                          aria-controls={reasonId}
                          title="Avvisa"
                        />
                      </div>
                    </td>
                  </tr>
                  {rejecting && (
                    <tr className={styles.rejectRow}>
                      <td colSpan={8}>
                        <form
                          id={reasonId}
                          className={styles.rejectForm}
                          onSubmit={(event) => {
                            event.preventDefault();
                            handleReject(m);
                          }}
                        >
                          <Input
                            label="Anledning (valfritt)"
                            placeholder="Till exempel: otillräckligt saldo"
                            value={rejectComment}
                            maxLength={MAX_COMMENT_LENGTH}
                            onChange={(event) => setRejectComment(event.target.value)}
                            autoFocus
                          />
                          <div className={styles.rejectActions}>
                            <Button type="button" variant="secondary" onClick={cancelReject} disabled={rejectPending}>
                              Avbryt
                            </Button>
                            <Button type="submit" variant="dark" disabled={rejectPending}>
                              {rejectPending ? "Avvisar…" : "Avvisa betalningen"}
                            </Button>
                          </div>
                        </form>
                      </td>
                    </tr>
                  )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
        <footer className={styles.footer}>
          <CircleCheck size={17} aria-hidden="true" />
          Kontrollera alltid mottagare och belopp innan du godkänner.
          Beräknat saldo gäller efter denna betalning och kan ändras av andra transaktioner.
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
