import { useState } from "react";
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
import Modal from "../../components/ui/overlay/Modal";
import PaymentConfirmationDialog from "../confirmation/PaymentConfirmationDialog";
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
  // Steget som granskas i rutan, och om avvisningen håller på att fyllas i
  const [reviewingStepId, setReviewingStepId] = useState<number | null>(null);
  const [isRejecting, setIsRejecting] = useState(false);
  const [rejectComment, setRejectComment] = useState("");

  function openReview(step: AttestSteg) {
    setFeedback(null);
    setIsRejecting(false);
    setRejectComment("");
    setReviewingStepId(step.id);
  }

  function closeReview() {
    // Ett fel visas i rutan och gäller bara medan den är öppen
    setFeedback(null);
    setReviewingStepId(null);
    setIsRejecting(false);
    setRejectComment("");
  }

  function startReject() {
    setFeedback(null);
    setRejectComment("");
    setIsRejecting(true);
  }

  function cancelReject() {
    setFeedback(null);
    setIsRejecting(false);
    setRejectComment("");
  }

  function handleReject(step: AttestSteg) {
    setFeedback(null);
    reject.mutate({ stepId: step.id, comment: rejectComment }, {
      onSuccess: () => {
        closeReview();
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
        closeReview();
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
  const reviewingStep = data?.find((step) => step.id === reviewingStepId);

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
              {data.map((m) => (
                  <tr key={m.id}>
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
                          onClick={() => openReview(m)}
                          className={styles.review}
                          buttonStyle="icon-text"
                          icon="chevron"
                          variant="primary"
                          aria-label={`Granska betalning till ${m.mottagare}`}
                          aria-haspopup="dialog"
                        >
                          Granska
                        </Button>
                      </div>
                    </td>
                  </tr>
              ))}
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
  // Medan rutan är öppen är resten av sidan inaktiv, så ett fel visas i rutan
  const showFeedbackInModal = Boolean(reviewingStep) && feedback?.type === "error";

  let reviewModal = null;

  if (reviewingStep) {
    const step = reviewingStep;
    const approvePending = approve.isPending && approve.variables === step.id;
    const rejectPending = reject.isPending && reject.variables?.stepId === step.id;
    const busy = approvePending || rejectPending;

    if (isRejecting) {
      reviewModal = (
        <Modal
          title="Avvisa betalning"
          description="Betalningen genomförs inte. Initiatören ser anledningen i sin historik."
          onClose={closeReview}
          dismissible={!busy}
        >
          <dl className={styles.reviewSummary}>
            <div>
              <dt>Belopp</dt>
              <dd className={styles.reviewAmount}>{formatAmount(step.belopp, step.currency)}</dd>
            </div>
            <div>
              <dt>Till</dt>
              <dd className={styles.recipient}><strong>{step.mottagare}</strong></dd>
            </div>
            <div>
              <dt>Från konto</dt>
              <dd>{step.fromAccountName ?? "-"}</dd>
            </div>
            <div>
              <dt>Saldo efter betalning</dt>
              <dd>
                {step.balanceAfterPayment == null
                  ? "Kan inte beräknas"
                  : formatAmount(step.balanceAfterPayment, step.currency)}
              </dd>
            </div>
            <div>
              <dt>Typ</dt>
              <dd>{step.typ}</dd>
            </div>
            <div>
              <dt>Skapad</dt>
              <dd>{formatDateTime(step.createdAt)}{step.initiatorName ? ` av ${step.initiatorName}` : ""}</dd>
            </div>
            <div>
              <dt>Referens</dt>
              <dd>{step.reference || "–"}</dd>
            </div>
          </dl>

          {showFeedbackInModal && feedback && (
            <p className={`${styles.feedback} ${styles.feedbackError} ${styles.reviewError}`} role="alert">
              <TriangleAlert size={18} aria-hidden="true" />
              {feedback.text}
            </p>
          )}

          <form
            className={styles.rejectForm}
            onSubmit={(event) => {
              event.preventDefault();
              handleReject(step);
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
            <div className={styles.reviewActions}>
              <Button type="button" variant="secondary" onClick={cancelReject} disabled={rejectPending}>
                Tillbaka
              </Button>
              <Button type="submit" className={styles.reject} disabled={rejectPending}>
                {rejectPending ? "Avvisar…" : "Bekräfta avvisning"}
              </Button>
            </div>
          </form>
        </Modal>
      );
    } else {
      reviewModal = (
        <PaymentConfirmationDialog
          title="Granska betalning"
          description="Kontrollera mottagare och belopp innan du fattar beslut."
          details={[
            { label: "Belopp", value: formatAmount(step.belopp, step.currency) },
            { label: "Till", value: <strong>{step.mottagare}</strong> },
            { label: "Från konto", value: step.fromAccountName ?? "-" },
            {
              label: "Saldo efter betalning",
              value: step.balanceAfterPayment == null
                ? "Kan inte beräknas"
                : formatAmount(step.balanceAfterPayment, step.currency),
            },
            { label: "Typ", value: step.typ },
            {
              label: "Skapad",
              value: `${formatDateTime(step.createdAt)}${step.initiatorName ? ` av ${step.initiatorName}` : ""}`,
            },
            { label: "Referens", value: step.reference || "–" },
          ]}
          confirmLabel={approvePending ? "Godkänner…" : `Godkänn ${formatAmount(step.belopp, step.currency)}`}
          onConfirm={() => handleApprove(step)}
          onCancel={closeReview}
          isConfirming={approvePending}
          confirmDisabled={busy}
          error={showFeedbackInModal && feedback ? (
            <p className={`${styles.feedback} ${styles.feedbackError} ${styles.reviewError}`} role="alert">
              <TriangleAlert size={18} aria-hidden="true" />
              {feedback.text}
            </p>
          ) : undefined}
          secondaryAction={
            <Button
              type="button"
              onClick={startReject}
              disabled={busy}
              className={styles.reject}
              buttonStyle="icon-text"
              icon="x"
            >
              Avvisa betalning
            </Button>
          }
        />
      );
    }
  }

  return (
    <>
      {/* Utanför villkoren ovan så att meddelandet läses upp även när sista
          betalningen godkänts och tabellen byts mot det tomma läget. Då döljs
          det visuellt eftersom samma text visas i kortet */}
      <div className={styles.feedbackRegion} aria-live="polite">
        {feedback && !showFeedbackInModal && (
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
      {reviewModal}
    </>
  );
}
