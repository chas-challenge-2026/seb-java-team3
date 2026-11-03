import { useState } from "react";
import PaymentForm from "./PaymentForm";
import BatchPaymentUpload from "./BatchPaymentUpload";
import styles from "./NewPayment.module.css";

export function NewPayment() {
  const [mode, setMode] = useState<"single" | "batch">("single");

  return (
    <section className={styles.page}>
      <nav className={styles.modes} aria-label="Betalningssätt">
        <button
          type="button"
          className={mode === "single" ? styles.selected : ""}
          aria-pressed={mode === "single"}
          onClick={() => setMode("single")}
        >
          En betalning
        </button>
        <button
          type="button"
          className={mode === "batch" ? styles.selected : ""}
          aria-pressed={mode === "batch"}
          onClick={() => setMode("batch")}
        >
          Ladda upp flera
        </button>
      </nav>
      {mode === "single" ? <PaymentForm /> : <BatchPaymentUpload />}
    </section>
  );
}