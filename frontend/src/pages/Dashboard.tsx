import { Wrench } from "lucide-react";
import Container from "../components/ui/layout/Container";
import UserAvatarDashboard from "../components/ui/user/UserAvatarDashboard";
import { useUser } from "../features/auth/useUser";
import DashboardAccountsAndPayments from "./DashboardAccountsAndPayments";
import styles from "./Dashboard.module.css";

export function Dashboard() {
  const unavailable = false;
  const { data: user } = useUser();

  return (
    <Container maxWidth="lg" className={styles.page}>
      <h1>Översikt</h1>

      {unavailable ? (
        <section className={styles.status} aria-labelledby="dashboard-status-title">
          <div className={styles.icon}>
            <Wrench size={30} strokeWidth={1.8} aria-hidden="true" />
          </div>
          <div>
            <h2 id="dashboard-status-title">Översikten är under utveckling</h2>
            <p>Här kommer du snart att kunna se en samlad bild av dina betalningar och konton.</p>
          </div>
        </section>
      ) : (
        <>
          <UserAvatarDashboard name={user?.name ?? "Användare"} />
          <DashboardAccountsAndPayments />
        </>
      )}
    </Container>
  );
}
