import { useQuery, useQueryClient } from "@tanstack/react-query";
import { accountsQueryOptions, availableBalanceOf } from "../accounts/api";
import React, {
  useEffect,
  useRef,
  useState,
} from "react";
import { useNavigate } from "@tanstack/react-router";
import { Clock3, Info, PartyPopper } from "lucide-react";

import Container from "../../components/ui/layout/Container";
import Button from "../../components/ui/buttons/Button";
import Input from "../../components/ui/forms/Input";
import Select from "../../components/ui/forms/Select";
import PaymentConfirmationDialog from "../confirmation/PaymentConfirmationDialog";

import styles from "./PaymentForm.module.css";
import { formatAmount } from "../audit/utils/formatters";

import type {
  PaymentFormData,
  PaymentFormErrors,
  PaymentResponse,
} from "./types";

import { paymentFormSchema, paymentAmountSchema } from "./schema";
import { zodIssuesToFieldErrors } from "../../lib/zodErrors";
import { createPayment, getPaymentConfig } from "./api";
import {
  isApiError,
  isApiValidationError,
  NETWORK_ERROR_STATUS,
} from "../../error/api.error";

// Samma ordning som fälten visas i formuläret
const FIELD_ORDER = ["account", "recipientIban", "amount", "reference"] as const;

function PaymentForm() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: accounts = [], isPending: accountsPending, isError: accountsError, refetch: refetchAccounts } = useQuery(accountsQueryOptions);

  const [formData, setFormData] = useState<PaymentFormData>({
    account: "",
    recipientIban: "",
    amount: "",
    reference: "",
  });

  const selectedAccount = accounts.find((account) => String(account.id) === formData.account);
  const parsedAmount = paymentAmountSchema.safeParse(formData.amount);
  const exceedsBalance = selectedAccount !== undefined && parsedAmount.success &&
    Number(parsedAmount.data) > availableBalanceOf(selectedAccount);
  const balanceError = exceedsBalance ? "Beloppet överstiger tillgängligt saldo." : undefined;

  const [errors, setErrors] = useState<PaymentFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [approvalThreshold, setApprovalThreshold] = useState<number | null>(null);
  const [completedPayment, setCompletedPayment] =
    useState<PaymentResponse | null>(null);
  const [paymentConfirmation, setPaymentConfirmation] =
    useState<PaymentFormData | null>(null);
  const accountRef = useRef<HTMLButtonElement>(null);
  const recipientIbanRef = useRef<HTMLInputElement>(null);
  const amountRef = useRef<HTMLInputElement>(null);
  const referenceRef = useRef<HTMLInputElement>(null);
  const focusFirstErrorRef = useRef(false);

  // Körs efter att felen renderats, så att skärmläsare läser upp felet med fältet
  useEffect(() => {
    if (!focusFirstErrorRef.current) {
      return;
    }

    focusFirstErrorRef.current = false;

    const fieldRefs = {
      account: accountRef,
      recipientIban: recipientIbanRef,
      amount: amountRef,
      reference: referenceRef,
    };
    const firstInvalidField = FIELD_ORDER.find((field) => errors[field]);

    if (firstInvalidField) {
      fieldRefs[firstInvalidField].current?.focus();
    }
  }, [errors]);

  useEffect(() => {
    let isActive = true;

    getPaymentConfig()
      .then((config) => {
        if (isActive) {
          setApprovalThreshold(config.approvalThreshold);
        }
      })
      .catch((error) => {
        console.error("Failed to load payment config:", error);
      });

    return () => {
      isActive = false;
    };
  }, []);

  const handleSubmit = async (
    event: React.SubmitEvent<HTMLFormElement>
  ) => {
    event.preventDefault();
    setSubmitError(null);

    const result = paymentFormSchema.safeParse(formData);

    if (!result.success) {
      setErrors(zodIssuesToFieldErrors<keyof PaymentFormErrors>(result.error));
      focusFirstErrorRef.current = true;
      return;
    }

    if (!accounts.some((account) => String(account.id) === result.data.account)) {
      setErrors({ account: "Välj ett tillgängligt konto." });
      focusFirstErrorRef.current = true;
      return;
    }
    if (exceedsBalance) {
      setErrors({ amount: balanceError });
      focusFirstErrorRef.current = true;
      return;
    }
    setErrors({});
    setPaymentConfirmation(result.data);
  };

  const handleConfirmPayment = async () => {
    if (!paymentConfirmation) {
      return;
    }

    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const payment = await createPayment(paymentConfirmation);
      // Dashboarden har redan cachat Mina betalningar och saldot, och en ADMIN ser även attestkön
      void queryClient.invalidateQueries({ queryKey: ["audit"] });
      void queryClient.invalidateQueries({ queryKey: ["approvals"] });
      void queryClient.invalidateQueries({ queryKey: ["accounts"] });
      setCompletedPayment(payment);
    } catch (error) {
      console.error("Failed to create payment:", error);
      setSubmitError(getSubmitErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  const cancelPaymentConfirmation = () => {
    setPaymentConfirmation(null);
    setSubmitError(null);
  };

  const handleCancel = () => {
    navigate({ to: "/" });
  };

  const handleNewPayment = () => {
    setCompletedPayment(null);
    setPaymentConfirmation(null);
    setSubmitError(null);
    setFormData({ account: "", recipientIban: "", amount: "", reference: "" });
    setErrors({});
  };

  return (
    <div
      className={styles.paymentCardWrapper}
    >
      <Container
        maxWidth="sm"
        variant="white"
        className={styles.paymentCard}
      >
        <form
          className={styles.paymentForm}
          onSubmit={handleSubmit}
          noValidate
        >
        <header className={styles.formHeader}>
          <div>
            <h2>Ny betalning</h2>
            <p>Fyll i uppgifterna nedan för att skapa en betalning.</p>
          </div>
        </header>

        <div className={styles.formBody}>
          <p className={styles.requiredLegend}>
            Fält markerade med <span className={styles.requiredMark} aria-hidden="true">*</span> är obligatoriska.
          </p>
          <div className={styles.sectionHeading}>
            <h3>Från konto</h3>
            <p>Välj vilket konto betalningen ska dras från.</p>
          </div>
          <Select
            ref={accountRef}
            label="Konto"
            required
            value={formData.account}
            placeholder="Välj konto"
            onChange={(value) => {
              setFormData((prev) => ({
                ...prev,
                account: value,
              }));

              setErrors((prev) => ({
                ...prev,
                account: undefined,
              }));
            }}
            disabled={accountsPending || accountsError || accounts.length === 0}
            options={accounts.map((account) => ({
              value: String(account.id),
              // Tillgängligt, inte saldot: betalningar som väntar på attest har redan reserverat sin del
              label: account.name + " - " + account.iban + " (tillgängligt " + availableBalanceOf(account).toLocaleString("sv-SE", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " " + account.currency + ")",
            }))}
          />

          {accountsPending && <p role="status">Laddar konton…</p>}
          {accountsError && <p role="alert">Kontona kunde inte hämtas. <button type="button" onClick={() => void refetchAccounts()}>Försök igen</button></p>}
          {!accountsPending && !accountsError && accounts.length === 0 && <p role="status">Företaget har inga konton att betala från.</p>}
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
                ref={recipientIbanRef}
                label="Mottagar-IBAN"
                required
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
              ref={amountRef}
              label="Belopp (SEK)"
              required
              placeholder="1 000,00"
              value={formData.amount}
              onChange={(event) => {
                setFormData((prev) => ({ ...prev, amount: event.target.value }));
                setErrors((prev) => ({ ...prev, amount: undefined }));
              }}
              error={balanceError ?? errors.amount}
              inputMode="decimal"
              />

            <Input
              ref={referenceRef}
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
              {approvalThreshold !== null && (
                <p className={styles.thresholdInfo}>
                  <Info size={17} aria-hidden="true" />
                  Betalningar över {formatSek(approvalThreshold)} behöver attesteras.
                </p>
              )}

          <div className={styles.paymentActions}>
            {submitError && !paymentConfirmation && (
              <p className={styles.submitError} role="alert">
                {submitError}
              </p>
            )}
            <Button
              type="button"
              onClick={handleCancel}
              disabled={Boolean(paymentConfirmation) || isSubmitting}
            >
              Avbryt
            </Button>

            <Button
              type="submit"
              variant="primary"
              disabled={Boolean(paymentConfirmation) || isSubmitting || accountsPending || accountsError || accounts.length === 0 || exceedsBalance}
              aria-busy={isSubmitting}
            >
              Skicka betalning
            </Button>
          </div>
        </div>
        </form>
      </Container>
      {paymentConfirmation && (
        <PaymentConfirmationDialog
          title="Bekräfta betalning"
          description="Kontrollera betalningsuppgifterna innan betalningen skickas."
          details={[
            { label: "Belopp", value: formatAmount(Number(paymentConfirmation.amount), "SEK") },
            {
              label: "Från konto",
              value: accounts.find((account) => String(account.id) === paymentConfirmation.account)?.name ?? "Konto",
            },
            { label: "Till IBAN", value: paymentConfirmation.recipientIban },
            { label: "Referens", value: paymentConfirmation.reference || "–" },
          ]}
          confirmLabel={isSubmitting ? "Skickar…" : "Bekräfta och skicka betalning"}
          onConfirm={handleConfirmPayment}
          onCancel={completedPayment ? handleCancel : cancelPaymentConfirmation}
          isConfirming={isSubmitting}
          error={submitError ? (
            <p className={styles.submitError} role="alert">
              {submitError}
            </p>
          ) : undefined}
          result={completedPayment ? {
            title: "Betalningen har skickats",
            description: completedPayment.status === "PENDING_APPROVAL"
              ? "Din betalning har registrerats och väntar på attest."
              : "Din betalning har registrerats och genomförts.",
            decoration: <PartyPopper />,
            statusLabel: "Betalningen har skickats",
            notice: completedPayment.status === "PENDING_APPROVAL" ? (
              <>
                <Clock3 size={19} aria-hidden="true" />
                <div>
                  <strong>Väntar på attest</strong>
                  <p>Betalningen behöver godkännas innan den genomförs.</p>
                </div>
              </>
            ) : undefined,
            details: [
              {
                label: "Belopp",
                value: completedPayment.amount.toLocaleString("sv-SE", {
                  style: "currency",
                  currency: "SEK",
                }),
              },
              {
                label: "Från konto",
                value: accounts.find((account) => String(account.id) === paymentConfirmation.account)?.name ?? "Konto",
              },
              { label: "Till IBAN", value: completedPayment.toIban },
              { label: "Referens", value: paymentConfirmation.reference || "–" },
            ],
            actions: (
              <>
                <Button type="button" onClick={handleNewPayment}>Ny betalning</Button>
                <Button type="button" variant="primary" onClick={handleCancel}>Gå till Översikt</Button>
              </>
            ),
          } : undefined}
        />
      )}
    </div>
  );
}

const SERVER_UNREACHABLE_MESSAGE =
  "Kunde inte nå servern. Betalningen har inte skickats. Försök igen om en stund.";

// Används när betalningen kan ha registrerats trots felet, så att den inte skickas två gånger
const UNCERTAIN_OUTCOME_MESSAGE =
  "Något gick fel och vi vet inte om betalningen registrerades. Kontrollera under Mina betalningar innan du försöker igen.";

function getSubmitErrorMessage(error: unknown): string {
  // Servern svarade, men svaret gick inte att läsa
  if (isApiValidationError(error)) {
    return UNCERTAIN_OUTCOME_MESSAGE;
  }

  if (isApiError(error)) {
    // Inget svar, eller 502/503 från proxyn när backend är nere: inget har registrerats
    if (
      error.status === NETWORK_ERROR_STATUS ||
      error.status === 502 ||
      error.status === 503
    ) {
      return SERVER_UNREACHABLE_MESSAGE;
    }

    if (error.status >= 500) {
      return UNCERTAIN_OUTCOME_MESSAGE;
    }

    // Backendens eget meddelande, eller api.ts standardmeddelande för statuskoden
    return error.message;
  }

  return "Betalningen kunde inte skickas. Försök igen.";
}

function formatSek(amount: number): string {
  return new Intl.NumberFormat("sv-SE", {
    style: "currency",
    currency: "SEK",
    maximumFractionDigits: 0,
  }).format(amount);
}

export default PaymentForm;
