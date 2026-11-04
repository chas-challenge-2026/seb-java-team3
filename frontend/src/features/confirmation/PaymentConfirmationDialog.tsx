import type { ReactNode } from "react";
import Button from "../../components/ui/buttons/Button";
import Modal from "../../components/ui/overlay/Modal";
import styles from "./PaymentConfirmationDialog.module.css";

export interface ConfirmationDetail {
  label: string;
  value: ReactNode;
}

export interface ConfirmationResult {
  title: string;
  description: string;
  details: ConfirmationDetail[];
  decoration?: ReactNode;
  statusLabel?: string;
  notice?: ReactNode;
  actions: ReactNode;
}

interface PaymentConfirmationDialogProps {
  title: string;
  description: string;
  details: ConfirmationDetail[];
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
  cancelLabel?: string;
  isConfirming?: boolean;
  confirmDisabled?: boolean;
  error?: ReactNode;
  secondaryAction?: ReactNode;
  result?: ConfirmationResult;
}

export default function PaymentConfirmationDialog({
  title,
  description,
  details,
  confirmLabel,
  onConfirm,
  onCancel,
  cancelLabel = "Avbryt",
  isConfirming = false,
  confirmDisabled = false,
  error,
  secondaryAction,
  result,
}: PaymentConfirmationDialogProps) {
  return (
    <Modal
      title={result?.title ?? title}
      description={result?.description ?? description}
      onClose={onCancel}
      dismissible={!isConfirming}
    >
      {result?.notice && <div className={styles.resultNotice} role="status">{result.notice}</div>}
      {result?.decoration && result.statusLabel && (
        <div className={styles.resultBanner} role="status">
          <span className={styles.resultIcon} aria-hidden="true">{result.decoration}</span>
          <strong>{result.statusLabel}</strong>
        </div>
      )}
      <dl className={styles.summary}>
        {(result?.details ?? details).map((detail, index) => (
          <div key={`${detail.label}-${index}`}>
            <dt>{detail.label}</dt>
            <dd className={index === 0 ? styles.amount : undefined}>{detail.value}</dd>
          </div>
        ))}
      </dl>

      {error}

      {result ? (
        <div className={styles.actions}>{result.actions}</div>
      ) : (
        <div className={styles.actions}>
          <Button
            type="button"
            onClick={onCancel}
            disabled={isConfirming}
            className={styles.cancel}
          >
            {cancelLabel}
          </Button>
          {secondaryAction}
          <Button
            type="button"
            variant="primary"
            onClick={onConfirm}
            disabled={isConfirming || confirmDisabled}
            aria-busy={isConfirming}
          >
            {confirmLabel}
          </Button>
        </div>
      )}
    </Modal>
  );
}
