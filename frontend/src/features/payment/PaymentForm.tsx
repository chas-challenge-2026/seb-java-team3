import React, {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { useNavigate } from "@tanstack/react-router";

import Container from "../../components/ui/layout/Container";
import Button from "../../components/ui/buttons/Button";
import Input from "../../components/ui/forms/Input";
import Select from "../../components/ui/forms/Select";

import styles from "./PaymentForm.module.css";

import type {
  PaymentFormData,
  PaymentFormErrors,
  PaymentResponse,
} from "./types";

import { createPayment } from "./api";

function PaymentForm() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState<PaymentFormData>({
    account: "",
    recipientIban: "",
    amount: "",
    reference: "",
  });

  const [errors, setErrors] = useState<PaymentFormErrors>({});
  const [completedPayment, setCompletedPayment] =
    useState<PaymentResponse | null>(null);
  const [pendingConfirmation, setPendingConfirmation] =
    useState<PaymentResponse | null>(null);
  const [cardDimensions, setCardDimensions] = useState<{
    height: number;
    width: number;
  } | null>(null);
  const [formWidth, setFormWidth] = useState<number | null>(null);
  const [returningToForm, setReturningToForm] = useState(false);
  const [isFormEntering, setIsFormEntering] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!pendingConfirmation) {
      return;
    }

    const confirmationTimer = window.setTimeout(() => {
      setCompletedPayment(pendingConfirmation);
      setPendingConfirmation(null);
    }, 250);

    return () => window.clearTimeout(confirmationTimer);
  }, [pendingConfirmation]);

  useLayoutEffect(() => {
    const cardWrapper = cardRef.current;
    const card = cardWrapper?.firstElementChild;

    if (!(cardWrapper && card instanceof HTMLElement)) {
      return;
    }

    if (pendingConfirmation && cardDimensions === null) {
      const { height, width } = card.getBoundingClientRect();
      setCardDimensions({ height, width });
      setFormWidth(width);
      return;
    }

    if (completedPayment && cardDimensions !== null) {
      const confirmationWidth = Math.min(cardDimensions.width, 420);

      card.style.height = "auto";
      cardWrapper.style.width = `${confirmationWidth}px`;
      const nextDimensions = card.getBoundingClientRect();
      card.style.height = `${cardDimensions.height}px`;
      cardWrapper.style.width = `${cardDimensions.width}px`;

      if (
        Math.abs(nextDimensions.height - cardDimensions.height) > 1 ||
        Math.abs(confirmationWidth - cardDimensions.width) > 1
      ) {
        const animationFrame = window.requestAnimationFrame(() => {
          setCardDimensions({
            height: nextDimensions.height,
            width: confirmationWidth,
          });
        });

        return () => window.cancelAnimationFrame(animationFrame);
      }
    }

    if (isFormEntering && cardDimensions !== null && formWidth !== null) {
      card.style.height = "auto";
      cardWrapper.style.width = `${formWidth}px`;
      const nextDimensions = card.getBoundingClientRect();
      card.style.height = `${cardDimensions.height}px`;
      cardWrapper.style.width = `${cardDimensions.width}px`;

      if (
        Math.abs(nextDimensions.height - cardDimensions.height) > 1 ||
        Math.abs(formWidth - cardDimensions.width) > 1
      ) {
        const animationFrame = window.requestAnimationFrame(() => {
          setCardDimensions({
            height: nextDimensions.height,
            width: formWidth,
          });
        });

        return () => window.cancelAnimationFrame(animationFrame);
      }
    }
  }, [
    cardDimensions,
    completedPayment,
    formWidth,
    isFormEntering,
    pendingConfirmation,
  ]);

  useEffect(() => {
    if (!returningToForm) {
      return;
    }

    const formTimer = window.setTimeout(() => {
      setCompletedPayment(null);
      setReturningToForm(false);
      setIsFormEntering(true);
      setFormData({
        account: "",
        recipientIban: "",
        amount: "",
        reference: "",
      });
      setErrors({});
    }, 250);

    return () => window.clearTimeout(formTimer);
  }, [returningToForm]);

  useEffect(() => {
    if (!isFormEntering) {
      return;
    }

    const enteringTimer = window.setTimeout(() => {
      setIsFormEntering(false);
    }, 350);

    return () => window.clearTimeout(enteringTimer);
  }, [isFormEntering]);

  const cardStyle = {
    boxSizing: "border-box" as const,
    height: cardDimensions
      ? `${cardDimensions.height}px`
      : undefined,
  };

  const cardWrapperStyle = {
    width: cardDimensions
      ? `${cardDimensions.width}px`
      : undefined,
  };

  const handleSubmit = async (
    event: React.SubmitEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    const newErrors: PaymentFormErrors = {};

    // Validera konto
    if (!formData.account) {
      newErrors.account =
        "Välj vilket konto betalningen ska dras från.";
    }

    // Validera IBAN
    if (!formData.recipientIban.trim()) {
      newErrors.recipientIban =
        "Ange mottagarens IBAN.";
    }

    // Validera belopp
    if (!formData.amount.trim()) {
      newErrors.amount = "Ange ett belopp.";
    } else if (
      !/^\d+(\.\d{1,2})?$/.test(formData.amount)
    ) {
      newErrors.amount =
        "Ange ett giltigt belopp med maximalt två decimaler.";
    } else if (Number(formData.amount) <= 0) {
      newErrors.amount =
        "Beloppet måste vara större än 0.";
    }

    setErrors(newErrors);

    // Avbryt om formuläret innehåller fel
    if (Object.keys(newErrors).length > 0) {
      return;
    }

    try {
      const payment = await createPayment(formData);
      setPendingConfirmation(payment);
    } catch (error) {
      console.error(
        "Failed to create payment:",
        error
      );
    }
  };

  const handleCancel = () => {
    navigate({ to: "/" });
  };

  const handleNewPayment = () => {
    setReturningToForm(true);
  };

  if (completedPayment) {
    return (
      <div
        ref={cardRef}
        className={styles.paymentCardWrapper}
        style={cardWrapperStyle}
      >
        <Container
          maxWidth="sm"
          variant="white"
          className={styles.paymentCard}
          style={cardStyle}
        >
          <section
            className={`${styles.paymentConfirmation} ${
              returningToForm ? styles.confirmationExiting : ""
            }`}
            aria-labelledby="payment-confirmation-title"
          >
          <header className={`${styles.formHeader} ${styles.confirmationHeader}`}>
            <div>
              <h2 id="payment-confirmation-title">Betalningen har skickats</h2>
              <p>Din betalning har registrerats och väntar på hantering.</p>
            </div>
          </header>

          <div className={styles.confirmationBody}>
            <dl className={styles.paymentSummary}>
              <div>
                <dt>Belopp</dt>
                <dd>
                  {completedPayment.amount.toLocaleString("sv-SE", {
                    style: "currency",
                    currency: "SEK",
                  })}
                </dd>
              </div>
              <div>
                <dt>Från konto</dt>
                <dd>{getAccountLabel(formData.account)}</dd>
              </div>
              <div>
                <dt>Till IBAN</dt>
                <dd>{completedPayment.toIban}</dd>
              </div>
              <div>
                <dt>Referens</dt>
                <dd>{formData.reference || "–"}</dd>
              </div>
            </dl>

            <div className={styles.confirmationActions}>
              <Button type="button" onClick={handleNewPayment}>
                Ny betalning
              </Button>
              <Button
                type="button"
                variant="primary"
                onClick={handleCancel}
              >
                Gå till Översikt
              </Button>
            </div>
          </div>
          </section>
        </Container>
      </div>
    );
  }

  return (
    <div
      ref={cardRef}
      className={styles.paymentCardWrapper}
      style={cardWrapperStyle}
    >
      <Container
        maxWidth="sm"
        variant="white"
        className={styles.paymentCard}
        style={cardStyle}
      >
        <form
          className={`${styles.paymentForm} ${
            pendingConfirmation
              ? styles.formExiting
              : isFormEntering
                ? styles.formEntering
                : ""
          }`}
          onSubmit={handleSubmit}
        >
        <header className={styles.formHeader}>
          <div>
            <h2>Ny betalning</h2>
            <p>Fyll i uppgifterna nedan för att skapa en betalning.</p>
          </div>
        </header>

        <div className={styles.formBody}>
          <div className={styles.sectionHeading}>
            <h3>Från konto</h3>
            <p>Välj vilket konto betalningen ska dras från.</p>
          </div>
          <Select
            label="Konto"
            value={formData.account}
            placeholder="Välj konto"
            onChange={(event) => {
              setFormData((prev) => ({
                ...prev,
                account: event.target.value,
              }));

              setErrors((prev) => ({
                ...prev,
                account: undefined,
              }));
            }}
            options={[
              {
                value: "driftkonto",
                label:
                  "Driftkonto - SE4550000000058398257466 (2425000.00 SEK)",
              },
              {
                value: "lönekonto",
                label:
                  "Lönekonto - SE4550000000058398257466 (2425000.00 SEK)",
              },
              {
                value: "projektkonto",
                label:
                  "Projektkonto - SE4550000000058398257466 (2425000.00 SEK)",
              },
            ]}
          />

          {errors.account && (
            <p className={styles.fieldError}>
              {errors.account}
            </p>
          )}

          <div className={styles.sectionHeading}>
            <h3>Betalningsuppgifter</h3>
            <p>Ange mottagare, belopp och en referens för betalningen.</p>
          </div>
          <div className={styles.formGrid}>
            <div className={styles.fullWidth}>
              <Input
                label="Mottagar-IBAN"
                placeholder="SE45 5000 0000 0583 9825 7466"
                value={formData.recipientIban}
                onChange={(event) => {
                  setFormData((prev) => ({ ...prev, recipientIban: event.target.value }));
                  setErrors((prev) => ({ ...prev, recipientIban: undefined }));
                }}
                error={errors.recipientIban}
              />
            </div>

            <Input
              label="Belopp (SEK)"
              placeholder="1000.00"
              value={formData.amount}
              onChange={(event) => {
                setFormData((prev) => ({ ...prev, amount: event.target.value }));
                setErrors((prev) => ({ ...prev, amount: undefined }));
              }}
              error={errors.amount}
              inputMode="decimal"
            />

            <Input
              label="Referens"
              placeholder="Faktura #1234"
              value={formData.reference}
              onChange={(event) => {
                setFormData((prev) => ({ ...prev, reference: event.target.value }));
                setErrors((prev) => ({ ...prev, reference: undefined }));
              }}
              error={errors.reference}
            />
          </div>

          <div className={styles.paymentActions}>
            <Button
              type="button"
              onClick={handleCancel}
              disabled={Boolean(pendingConfirmation)}
            >
              Avbryt
            </Button>

            <Button
              type="submit"
              variant="primary"
              buttonStyle="icon-text"
              icon="check"
              disabled={Boolean(pendingConfirmation)}
            >
              Skicka betalning
            </Button>
          </div>
        </div>
        </form>
      </Container>
    </div>
  );
}

function getAccountLabel(account: string) {
  const accounts: Record<string, string> = {
    driftkonto: "Driftkonto",
    lönekonto: "Lönekonto",
    projektkonto: "Projektkonto",
  };

  return accounts[account] ?? account;
}

export default PaymentForm;
