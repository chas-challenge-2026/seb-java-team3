import type { ReactNode } from "react";
import Button from "../buttons/Button";
import Modal from "./Modal";
import styles from "./PaymentConfirmationDialog.module.css";

export interface ConfirmationDetail {
  label: string;
  value: ReactNode;
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
}: PaymentConfirmationDialogProps) {
  return (
    <Modal
      title={title}
      description={description}
      onClose={onCancel}
      dismissible={!isConfirming}
    >
      <dl className={styles.summary}>
        {details.map((detail, index) => (
          <div key={`${detail.label}-${index}`}>
            <dt>{detail.label}</dt>
            <dd className={index === 0 ? styles.amount : undefined}>{detail.value}</dd>
          </div>
        ))}
      </dl>

      {error}

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
    </Modal>
  );
}
