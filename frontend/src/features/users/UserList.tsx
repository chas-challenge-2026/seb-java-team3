import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Users } from "lucide-react";

import Button from "../../components/ui/buttons/Button";
import IconMessage from "../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../components/ui/feedback/LoadErrorMessage";
import LoadingMessage from "../../components/ui/feedback/LoadingMessage";
import CreateUserModal from "./CreateUserModal";
import UserRoleCell from "./UserRoleCell";
import { usersQueryOptions } from "./usersQueryOptions";

import styles from "./UsersPage.module.css";

export default function UserList() {
  const { data, isPending, isError, isFetching, refetch } = useQuery(usersQueryOptions);
  const [isCreating, setIsCreating] = useState(false);

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
                <th>Roll</th>
              </tr>
            </thead>
            <tbody>
              {data.map((user) => (
                <tr key={user.id}>
                  <td className={styles.name}>{user.name}</td>
                  <td className={styles.email}>{user.email}</td>
                  <td>
                    <UserRoleCell user={user} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {isCreating && <CreateUserModal onClose={() => setIsCreating(false)} />}
    </section>
  );
}
