import { useApprovals } from "../attest/useApprovals";
import { formatAmount } from "../audit/utils/formatters";
import { formatDateTime } from "../../lib/dateTime";
import { ArrowRight, Clock3, PartyPopper, TriangleAlert } from "lucide-react";
import { Link } from "@tanstack/react-router";
import styles from "./DashboardApprovals.module.css";

const MAX_VISIBLE_APPROVALS = 3;

export default function DashboardApprovals() {
  const { data, isPending, isError, isFetching, refetch } = useApprovals();

  if (isPending) {
    return (
      <section className={styles.panel} aria-labelledby="dashboard-approvals-title">
        <h2 id="dashboard-approvals-title">Attesteringar</h2>
        <p className={styles.message} role="status">Hämtar väntande attesteringar…</p>
      </section>
    );
  }

  if (isError) {
    return (
      <section className={styles.panel} aria-labelledby="dashboard-approvals-title">
        <h2 id="dashboard-approvals-title">Attesteringar</h2>
        <div className={styles.error} role="alert">
          <TriangleAlert size={20} aria-hidden="true" />
          <span>Kunde inte hämta väntande attesteringar.</span>
          <button type="button" onClick={() => refetch()} disabled={isFetching}>
            Försök igen
          </button>
        </div>
      </section>
    );
  }

  if (data.length === 0) {
    return (
      <section className={`${styles.panel} ${styles.empty}`} aria-labelledby="dashboard-approvals-title">
        <PartyPopper size={30} aria-hidden="true" />
        <h2 id="dashboard-approvals-title">Inga betalningar väntar på attest</h2>
      </section>
    );
  }

  const visibleApprovals = data.slice(0, MAX_VISIBLE_APPROVALS);

  return (
    <section className={styles.panel} aria-labelledby="dashboard-approvals-title">
      <header className={styles.header}>
        <div>
          <h2 id="dashboard-approvals-title">Betalningar som väntar på attest</h2>
          <p>
            {data.length === 1
              ? "Du har en betalning att granska."
              : `Du har ${data.length} betalningar att granska.`}
          </p>
        </div>
        <span className={styles.count}>
          <Clock3 size={16} aria-hidden="true" />
          {data.length} väntande
        </span>
      </header>

      <ul className={styles.list}>
        {visibleApprovals.map((approval) => (
          <li className={styles.item} key={approval.id}>
            <div className={styles.details}>
              <strong>{approval.mottagare}</strong>
              <span>{approval.reference?.trim() || approval.typ}</span>
              <span className={styles.date}>{formatDateTime(approval.createdAt)}</span>
            </div>
            <strong className={styles.amount}>
              {formatAmount(approval.belopp, approval.currency)}
            </strong>
          </li>
        ))}
      </ul>

      {data.length > MAX_VISIBLE_APPROVALS && (
        <footer className={styles.footer}>
          <Link to="/attest">
            Visa alla {data.length} <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </footer>
      )}
    </section>
  );
}