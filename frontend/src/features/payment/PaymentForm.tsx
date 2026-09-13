import {useState, type FormEvent} from "react";
import {useNavigate} from "@tanstack/react-router"

import Container from "../../components/ui/layout/Container"
import Button from "../../components/ui/buttons/Button"
import Divider from "../../components/ui/layout/Divider"
import Input from "../../components/ui/forms/Input"
import Select from "../../components/ui/forms/Select"
import styles from "./PaymentForm.module.css";

import type {PaymentFormData, PaymentFormErrors} from "./types";

import {createPayment} from "./api";

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
    event: FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    const newErrors: PaymentFormErrors = {};

    if (!formData.account) {
      newErrors.account =
        "Välj vilket konto betalningen ska dras från.";
    }

    if (!formData.recipientIban.trim()) {
      newErrors.recipientIban =
        "Ange mottagarens IBAN.";
    }

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

    if (Object.keys(newErrors).length > 0) {
      return;
    }

    try {
      await createPayment(formData);

      navigate({ to: "/" });
    } catch (error) {
      console.error("Failed to create payment:", error);
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
            setFormData({
              ...formData,
              account: event.target.value,
            });

            setErrors({
              ...errors,
              account: undefined,
            });
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
            setFormData({
              ...formData,
              recipientIban: event.target.value,
            });

            setErrors({
              ...errors,
              recipientIban: undefined,
            });
          }}
          error={errors.recipientIban}
        />

        <Input
          label="Belopp (SEK)"
          placeholder="1000.00"
          value={formData.amount}
          onChange={(event) => {
            setFormData({
              ...formData,
              amount: event.target.value,
            });

            setErrors({
              ...errors,
              amount: undefined,
            });
          }}
          error={errors.amount}
          inputMode="decimal"
        />

        <Input
          label="Referens"
          placeholder="Faktura #1234"
          value={formData.reference}
          onChange={(event) => {
            setFormData({
              ...formData,
              reference: event.target.value,
            });

            setErrors({
              ...errors,
              reference: undefined,
            });
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