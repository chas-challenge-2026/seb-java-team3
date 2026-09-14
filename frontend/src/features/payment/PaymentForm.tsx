import React, { useState } from "react";
import { useNavigate } from "@tanstack/react-router";

import Container from "../../components/ui/layout/Container";
import Button from "../../components/ui/buttons/Button";
import Divider from "../../components/ui/layout/Divider";
import Input from "../../components/ui/forms/Input";
import Select from "../../components/ui/forms/Select";

import styles from "./PaymentForm.module.css";

import type {
  PaymentFormData,
  PaymentFormErrors,
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
      await createPayment(formData);

      // Tillfälligt: gå tillbaka till dashboard
      // efter att betalningen har skickats.
      navigate({ to: "/" });
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

  return (
    <Container
      maxWidth="sm"
      variant="white"
      style={{ marginTop: "10rem" }}
    >
      <form onSubmit={handleSubmit}>
        <h2 style={{ textAlign: "center" }}>
          Ny betalning
        </h2>

        <Select
          label="Konto"
          value={formData.account}
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

        <Input
          label="Mottagar-IBAN"
          placeholder="SE45 5000 0000 0583 9825 7466"
          value={formData.recipientIban}
          onChange={(event) => {
            setFormData((prev) => ({
              ...prev,
              recipientIban: event.target.value,
            }));

            setErrors((prev) => ({
              ...prev,
              recipientIban: undefined,
            }));
          }}
          error={errors.recipientIban}
        />

        <Input
          label="Belopp (SEK)"
          placeholder="1000.00"
          value={formData.amount}
          onChange={(event) => {
            setFormData((prev) => ({
              ...prev,
              amount: event.target.value,
            }));

            setErrors((prev) => ({
              ...prev,
              amount: undefined,
            }));
          }}
          error={errors.amount}
          inputMode="decimal"
        />

        <Input
          label="Referens"
          placeholder="Faktura #1234"
          value={formData.reference}
          onChange={(event) => {
            setFormData((prev) => ({
              ...prev,
              reference: event.target.value,
            }));

            setErrors((prev) => ({
              ...prev,
              reference: undefined,
            }));
          }}
          error={errors.reference}
        />

        <Divider shortWidth />

        <div className={styles.paymentActions}>
          <Button
            type="button"
            onClick={handleCancel}
          >
            Avbryt
          </Button>

          <Button
            type="submit"
            variant="primary"
            buttonStyle="icon-text"
            icon="check"
          >
            Skicka Betalning
          </Button>
        </div>
      </form>
    </Container>
  );
}

export default PaymentForm;