import { useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Clock3, PartyPopper } from "lucide-react";
import { ApiError } from "../../error/api.error";
import Button from "../../components/ui/buttons/Button";
import PaymentConfirmationDialog from "../confirmation/PaymentConfirmationDialog";
import { accountsQueryOptions } from "../accounts/api";
import { formatAmount } from "../audit/utils/formatters";
import { importPayments } from "./api";
import { CSV_HEADER, MAX_ROWS, parseCsvPreview, type CsvPreviewRow } from "./csvPreview";
import type { PaymentResponse } from "./types";
import styles from "./BatchPaymentUpload.module.css";

const MAX_FILE_BYTES = 1_000_000;

const EXAMPLE_CSV = `${CSV_HEADER}
1,SE3550000000054910000003,1250.50,Faktura 2001
2,SE7850000000054910000005,8000.00,"Lön, oktober"`;

function readFileText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(file, "utf-8");
  });
}

// Summeras i ören så att flyttal inte ger avrundningsfel i totalen
function totalAmount(rows: CsvPreviewRow[]): number {
  return rows.reduce((sum, row) => sum + Math.round(row.amount * 100), 0) / 100;
}

export default function BatchPaymentUpload() {
  const queryClient = useQueryClient();
  const { data: accounts = [], isSuccess: accountsLoaded } = useQuery(accountsQueryOptions);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<CsvPreviewRow[] | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<PaymentResponse[] | null>(null);

  function accountName(accountId: number): string {
    return accounts.find((account) => account.id === accountId)?.name ?? `Konto-id ${accountId}`;
  }

  // Steg 1: läs filen och visa betalningarna i bekräftelserutan
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

    let content: string;
    try {
      content = await readFileText(file);
    } catch {
      setError("Filen kunde inte läsas. Försök igen.");
      return;
    }

    const parsed = parseCsvPreview(content);
    if (!parsed.ok) {
      setError(parsed.error);
      return;
    }

    if (accountsLoaded) {
      const unknown = parsed.rows.find(
        (row) => !accounts.some((account) => account.id === row.fromAccountId),
      );
      if (unknown) {
        setError(
          `Rad ${unknown.line}: konto-id ${unknown.fromAccountId} finns inte bland dina konton. Se listan "Dina konton".`,
        );
        return;
      }
    }

    setSubmitError(null);
    setPreview(parsed.rows);
  };

  // Steg 2: användaren har bekräftat, filen skickas
  const handleConfirm = async () => {
    if (!file) return;
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const importedPayments = await importPayments(file);
      setResult(importedPayments);
      void queryClient.invalidateQueries({ queryKey: ["audit"] });
      void queryClient.invalidateQueries({ queryKey: ["approvals"] });
      void queryClient.invalidateQueries({ queryKey: ["accounts"] });
    } catch (importError) {
      console.error("Failed to import CSV payments:", importError);
      setSubmitError(
        importError instanceof ApiError
          ? importError.message
          : "Kunde inte ladda upp CSV-filen. Inga betalningar har skapats. Försök igen.",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const closeDialog = () => {
    setPreview(null);
    setSubmitError(null);
    if (result) {
      // Betalningarna är skapade, så samma fil ska inte kunna skickas igen av misstag
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const pendingCount = result?.filter((payment) => payment.status === "PENDING_APPROVAL").length ?? 0;
  const completedCount = result?.filter((payment) => payment.status === "COMPLETED").length ?? 0;

  return (
    <section className={styles.card} aria-labelledby="batch-upload-title">
      <header>
        <h1 id="batch-upload-title">Ladda upp betalningar</h1>
        <p>
          Skapa flera betalningar på en gång från en CSV-fil. Du får se och bekräfta betalningarna
          innan något skapas.
        </p>
      </header>

      <div className={styles.instructions}>
        <h2 className={styles.instructionsTitle}>Så fyller du i filen</h2>
        <ol className={styles.steps}>
          <li>
            Första raden ska vara exakt:
            <code>{CSV_HEADER}</code>
          </li>
          <li>
            Sedan en rad per betalning, med fyra kolumner:
            <ul>
              <li>
                <strong>from_account_id</strong>: id för kontot som pengarna dras från. Se{" "}
                <em>Dina konton</em> nedan.
              </li>
              <li>
                <strong>to_iban</strong>: mottagarens IBAN.
              </li>
              <li>
                <strong>amount</strong>: belopp i kronor, med punkt och högst två decimaler, till
                exempel 1250.50.
              </li>
              <li>
                <strong>reference</strong>: valfri text, till exempel ett fakturanummer. Sätt
                texten inom citattecken om den innehåller komma.
              </li>
            </ul>
          </li>
        </ol>

        <h3 className={styles.instructionsTitle}>Dina konton</h3>
        {accounts.length > 0 ? (
          <table className={styles.accounts}>
            <thead>
              <tr>
                <th scope="col">Konto-id</th>
                <th scope="col">Konto</th>
                <th scope="col">Kontonummer (IBAN)</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td className={styles.accountId}>{account.id}</td>
                  <td>{account.name}</td>
                  <td className={styles.iban}>{account.iban}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p>Dina konton visas här när de har laddats.</p>
        )}

        <h3 className={styles.instructionsTitle}>Exempel</h3>
        <pre>{EXAMPLE_CSV}</pre>
        <p>
          Filen ska vara UTF-8, högst 1 MB och innehålla högst {MAX_ROWS} betalningar. Om en
          rad är fel skapas ingen av betalningarna.
        </p>
      </div>

      <form onSubmit={handleSubmit}>
        <label className={styles.fileLabel} htmlFor="payment-csv-file">
          CSV-fil
        </label>
        <input
          ref={fileInputRef}
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
        <Button type="submit" variant="primary" disabled={Boolean(preview)}>
          Granska betalningar
        </Button>
      </form>

      {result && !preview && (
        <div className={styles.success} role="status">
          <strong>{result.length} betalningar har skapats.</strong>
          <p>
            {pendingCount} väntar på attest; {completedCount} har genomförts direkt.
          </p>
        </div>
      )}

      {preview && (
        <PaymentConfirmationDialog
          title="Bekräfta betalningar"
          description="Kontrollera betalningarna innan de skapas."
          details={[
            { label: "Totalt belopp", value: formatAmount(totalAmount(preview), "SEK") },
            { label: "Antal betalningar", value: preview.length },
          ]}
          confirmLabel={
            isSubmitting
              ? "Skapar…"
              : `Bekräfta och skapa ${preview.length} ${preview.length === 1 ? "betalning" : "betalningar"}`
          }
          onConfirm={handleConfirm}
          onCancel={closeDialog}
          isConfirming={isSubmitting}
          error={submitError ? (
            <p className={styles.error} role="alert">{submitError}</p>
          ) : undefined}
          result={result ? {
            title: "Betalningarna har skapats",
            description: `${result.length} ${result.length === 1 ? "betalning har" : "betalningar har"} registrerats.`,
            decoration: <PartyPopper />,
            statusLabel: "Betalningarna har skapats",
            notice: pendingCount > 0 ? (
              <>
                <Clock3 size={19} aria-hidden="true" />
                <div>
                  <strong>{pendingCount} väntar på attest</strong>
                  <p>De behöver godkännas innan de genomförs.</p>
                </div>
              </>
            ) : undefined,
            details: [
              { label: "Totalt belopp", value: formatAmount(totalAmount(preview), "SEK") },
              { label: "Skapade", value: result.length },
              { label: "Väntar på attest", value: pendingCount },
              { label: "Genomförda direkt", value: completedCount },
            ],
            actions: (
              <Button type="button" variant="primary" onClick={closeDialog}>
                Klar
              </Button>
            ),
          } : undefined}
        >
          <ul className={styles.previewList} aria-label="Betalningar i filen">
            {preview.map((row) => (
              <li key={row.line}>
                <div className={styles.previewMain}>
                  <span className={styles.iban}>{row.toIban}</span>
                  <span className={styles.amount}>{formatAmount(row.amount, "SEK")}</span>
                </div>
                <div className={styles.previewMeta}>
                  Från {accountName(row.fromAccountId)} · {row.reference || "Ingen referens"}
                </div>
              </li>
            ))}
          </ul>
          <p className={styles.helpText}>Om en rad är fel skapas ingen av betalningarna.</p>
        </PaymentConfirmationDialog>
      )}
    </section>
  );
}
