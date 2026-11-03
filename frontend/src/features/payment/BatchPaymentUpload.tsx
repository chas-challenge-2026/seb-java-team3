import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "../../error/api.error";
import Button from "../../components/ui/buttons/Button";
import { importPayments } from "./api";
import type { PaymentResponse } from "./types";
import styles from "./BatchPaymentUpload.module.css";

const MAX_FILE_BYTES = 1_000_000;

export default function BatchPaymentUpload() {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<PaymentResponse[] | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setResult(null);

    if (!file) {
      setError("Välj en CSV-fil att ladda upp.");
      return;
    }
    if (!file.name.toLowerCase().endsWith(".csv")) {
      setError("Filen måste ha filändelsen .csv.");
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      setError("CSV-filen får vara högst 1 MB.");
      return;
    }

    setIsSubmitting(true);
    try {
      const importedPayments = await importPayments(file);
      setResult(importedPayments);
      void queryClient.invalidateQueries({ queryKey: ["audit"] });
      void queryClient.invalidateQueries({ queryKey: ["approvals"] });
      void queryClient.invalidateQueries({ queryKey: ["accounts"] });
    } catch (submitError) {
      console.error("Failed to import CSV payments:", submitError);
      setError(
        submitError instanceof ApiError
          ? submitError.message
          : "Kunde inte ladda upp CSV-filen. Försök igen.",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className={styles.card} aria-labelledby="batch-upload-title">
      <header>
        <h1 id="batch-upload-title">Ladda upp betalningar</h1>
        <p>Skapa flera betalningar från en CSV-fil. Högst 500 betalningar per fil.</p>
      </header>

      <div className={styles.instructions}>
        <p>Filen ska vara UTF-8 och ha denna rubrikrad, i exakt denna ordning:</p>
        <code>from_account_id,to_iban,amount,reference</code>
        <p>En rad per betalning. Exempel:</p>
        <pre>{`from_account_id,to_iban,amount,reference
42,SE4550000000058398257466,100.50,"Faktura 123"`}</pre>
        <p>
          Belopp anges med punkt och högst två decimaler. Kontot måste tillhöra
          ditt företag. En fil behandlas helt eller inte alls.
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        <label className={styles.fileLabel} htmlFor="payment-csv-file">
          CSV-fil
        </label>
        <input
          id="payment-csv-file"
          type="file"
          accept=".csv,text/csv"
          aria-describedby="payment-csv-help"
          onChange={(event) => {
            setFile(event.target.files?.[0] ?? null);
            setError(null);
            setResult(null);
          }}
        />
        <p id="payment-csv-help" className={styles.helpText}>
          Maximal filstorlek: 1 MB.
        </p>
        {error && <p className={styles.error} role="alert">{error}</p>}
        <Button type="submit" variant="primary" disabled={isSubmitting}>
          {isSubmitting ? "Laddar upp…" : "Ladda upp och skapa betalningar"}
        </Button>
      </form>

      {result && (
        <div className={styles.success} role="status">
          <strong>{result.length} betalningar har skapats.</strong>
          <p>
            {result.filter((payment) => payment.status === "PENDING_APPROVAL").length} väntar
            på attest; {result.filter((payment) => payment.status === "COMPLETED").length} har
            genomförts direkt.
          </p>
        </div>
      )}
    </section>
  );
}
