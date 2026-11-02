import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Trash2, Users } from "lucide-react";

import Button from "../../components/ui/buttons/Button";
import IconMessage from "../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../components/ui/feedback/LoadErrorMessage";
import LoadingMessage from "../../components/ui/feedback/LoadingMessage";
import CreateUserModal from "./CreateUserModal";
import DeleteUserModal from "./DeleteUserModal";
import UserRoleCell from "./UserRoleCell";
import type { UserResponse } from "./types";
import { usersQueryOptions } from "./usersQueryOptions";

import styles from "./UsersPage.module.css";

export default function UserList() {
  const { data, isPending, isError, isFetching, refetch } = useQuery(usersQueryOptions);
  const [isCreating, setIsCreating] = useState(false);
  const [userToDelete, setUserToDelete] = useState<UserResponse | null>(null);

  return (
    <section className={styles.card} aria-label="Användare">
      <header className={styles.header}>
        <div>
          <h2>Användare</h2>
          <p>Alla användare i företaget och deras roller.</p>
        </div>
        <div className={styles.headerActions}>
          {data && (
            <div className={styles.count} aria-label={`${data.length} användare`}>
              <Users size={18} aria-hidden="true" />
              <span>{data.length}</span> användare
            </div>
          )}
          <Button
            type="button"
            variant="primary"
            buttonStyle="icon-text"
            icon="plus"
            onClick={() => setIsCreating(true)}
          >
            Skapa användare
          </Button>
        </div>
      </header>

      {isPending ? (
        <div className={styles.listState}>
          <LoadingMessage message="Laddar användare…" />
        </div>
      ) : isError ? (
        <div className={styles.listState}>
          <LoadErrorMessage
            title="Kunde inte hämta användarna"
            onRetry={() => refetch()}
            isRetrying={isFetching}
          />
        </div>
      ) : data.length === 0 ? (
        <div className={styles.listState}>
          <IconMessage
            title="Inga användare än"
            message="Skapa den första användaren med knappen ovan."
            icon={Users}
          />
        </div>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.grid}>
            <thead>
              <tr>
                <th>Namn</th>
                <th>E-post</th>
                <th className={`${styles.shrinkCol} ${styles.roleCol}`}>Roll</th>
                <th className={`${styles.shrinkCol} ${styles.actionCol}`}>
                  <span className={styles.srOnly}>Åtgärder</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {data.map((user) => (
                <tr key={user.id}>
                  <td className={styles.name}>{user.name}</td>
                  <td className={styles.email}>{user.email}</td>
                  <td className={`${styles.shrinkCol} ${styles.roleCol}`}>
                    <UserRoleCell user={user} />
                  </td>
                  <td className={`${styles.shrinkCol} ${styles.actionCol}`}>
                    {/* Det enda adminkontot kan inte tas bort */}
                    {user.role !== "ADMIN" && (
                      <button
                        type="button"
                        className={styles.deleteButton}
                        aria-label={`Ta bort ${user.name}`}
                        title="Ta bort användare"
                        onClick={() => setUserToDelete(user)}
                      >
                        <Trash2 size={16} aria-hidden="true" />
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isCreating && <CreateUserModal onClose={() => setIsCreating(false)} />}
      {userToDelete && (
        <DeleteUserModal user={userToDelete} onClose={() => setUserToDelete(null)} />
      )}
    </section>
  );
}
