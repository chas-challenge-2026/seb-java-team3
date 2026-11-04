import { useState } from "react";
import Container from "../../components/ui/layout/Container";
import PaymentForm from "./PaymentForm";
import BatchPaymentUpload from "./BatchPaymentUpload";
import styles from "./NewPayment.module.css";

export function NewPayment() {
  const [mode, setMode] = useState<"single" | "batch">("single");

  return (
    <>
      {/* Container ger samma övre marginal som titeln har på övriga sidor */}
      <Container maxWidth="xxl">
        <h1 style={{textAlign: "center", marginBottom: "0"}}>Ny betalning</h1>
      </Container>
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
    </>
  );
}
