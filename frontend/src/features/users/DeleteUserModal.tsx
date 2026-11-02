import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import Button from "../../components/ui/buttons/Button";
import Modal from "../../components/ui/overlay/Modal";
import { isApiError, NETWORK_ERROR_STATUS } from "../../error/api.error";
import { deleteUser } from "./api";
import { ROLE_LABELS } from "./roles";
import { usersQueryKey } from "./usersQueryOptions";
import type { UserResponse } from "./types";

import styles from "./UsersPage.module.css";

interface DeleteUserModalProps {
  user: UserResponse;
  onClose: () => void;
}

export default function DeleteUserModal({ user, onClose }: DeleteUserModalProps) {
  const queryClient = useQueryClient();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => deleteUser(user.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: usersQueryKey });
      onClose();
    },
    onError: (error) => {
      console.error("Failed to delete user:", error);
      setSubmitError(getDeleteErrorMessage(error));
    },
  });

  return (
    <Modal
      title="Ta bort användare"
      description="Är du säker på att du vill ta bort användaren? Det går inte att ångra."
      onClose={onClose}
      dismissible={!mutation.isPending}
    >
      <dl className={styles.summary}>
        <div>
          <dt>Namn</dt>
          <dd>{user.name}</dd>
        </div>
        <div>
          <dt>E-post</dt>
          <dd>{user.email}</dd>
        </div>
        <div>
          <dt>Roll</dt>
          <dd>{ROLE_LABELS[user.role]}</dd>
        </div>
      </dl>

      <p className={styles.permanentNotice}>
        Borttagningen är permanent. Användaren förlorar direkt sin åtkomst till företagets konton.
      </p>

      {submitError && (
        <p className={styles.submitError} role="alert">
          {submitError}
        </p>
      )}

      <div className={styles.actions}>
        <Button type="button" onClick={onClose} disabled={mutation.isPending}>
          Nej, behåll användaren
        </Button>
        <Button
          type="button"
          className={styles.dangerButton}
          onClick={() => {
            setSubmitError(null);
            mutation.mutate();
          }}
          disabled={mutation.isPending}
          aria-busy={mutation.isPending}
        >
          {mutation.isPending ? "Tar bort…" : "Ja, ta bort permanent"}
        </Button>
      </div>
    </Modal>
  );
}

function getDeleteErrorMessage(error: unknown): string {
  if (isApiError(error)) {
    if (error.status === NETWORK_ERROR_STATUS || error.status === 502 || error.status === 503) {
      return "Kunde inte nå servern. Användaren har inte tagits bort. Försök igen om en stund.";
    }

    return error.message;
  }

  return "Användaren kunde inte tas bort. Försök igen.";
}
