import { useMutation, useQueryClient } from "@tanstack/react-query";

import Select from "../../components/ui/forms/Select";
import { isApiError } from "../../error/api.error";
import { updateUserRole } from "./api";
import { CREATABLE_ROLE_OPTIONS, ROLE_LABELS } from "./roles";
import { creatableRoleSchema, type CreatableRole } from "./schema";
import { usersQueryKey } from "./usersQueryOptions";
import type { UserResponse } from "./types";

import styles from "./UsersPage.module.css";

interface UserRoleCellProps {
  user: UserResponse;
}

export default function UserRoleCell({ user }: UserRoleCellProps) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (role: CreatableRole) => updateUserRole(user.id, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: usersQueryKey }),
    onError: (error) => console.error("Failed to update user role:", error),
  });

  // Det finns bara ett adminkonto och dess roll går inte att ändra
  if (user.role === "ADMIN") {
    return <span className={styles.role}>{ROLE_LABELS.ADMIN}</span>;
  }

  const errorMessage = mutation.isError
    ? isApiError(mutation.error)
      ? mutation.error.message
      : "Rollen kunde inte ändras. Försök igen."
    : null;

  return (
    <div className={styles.roleCell}>
      <Select
        compact
        ariaLabel={`Roll för ${user.name}`}
        value={user.role}
        options={CREATABLE_ROLE_OPTIONS}
        disabled={mutation.isPending}
        onChange={(value) => {
          const role = creatableRoleSchema.safeParse(value);
          if (role.success && role.data !== user.role) {
            mutation.mutate(role.data);
          }
        }}
      />
      {errorMessage && (
        <p className={styles.roleError} role="alert">
          {errorMessage}
        </p>
      )}
    </div>
  );
}
