import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ArrowUpRight, Landmark } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { myPaymentsQueryOptions } from "../features/audit/queries/auditQueryOptions";
import {
  formatAmount,
  formatAuditStatus,
  formatDateTime,
} from "../features/audit/utils/formatters";
import { getDateTimeSortValue } from "../lib/dateTime";
import styles from "./DashboardAccountsAndPayments.module.css";

const mockAccounts = [
  { name: "Driftkonto", number: "•••• 4821", balance: 128450.75 },
  { name: "Lönekonto", number: "•••• 1936", balance: 76420 },
  { name: "Projektkonto", number: "•••• 7054", balance: 215800.5 },
];

const mockTotal = mockAccounts.reduce((total, account) => total + account.balance, 0);

const sekFormatter = new Intl.NumberFormat("sv-SE", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export default function DashboardAccountsAndPayments() {
  const { data: payments, isPending, isError } = useQuery(myPaymentsQueryOptions);
  const recentPayments = [...(payments ?? [])]
    .sort((first, second) => {
      const firstDate = getDateTimeSortValue(first.createdAt) ?? 0;
      const secondDate = getDateTimeSortValue(second.createdAt) ?? 0;
      return secondDate - firstDate;
    })
    .slice(0, 3);

  return (
    <div className={styles.dashboardSummary}>
      <DashboardAccounts />

      <section className={styles.payments} aria-labelledby="dashboard-payments-title">
        <header className={styles.sectionHeader}>
          <div>
            <h2 id="dashboard-payments-title">Senaste betalningar</h2>
            <p>Dina tre senast skapade betalningar.</p>
          </div>
          <Link className={styles.allPayments} to="/my-payments">
            Visa alla <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </header>

        {isPending ? (
          <p className={styles.message} role="status">Laddar betalningar…</p>
        ) : isError ? (
          <p className={styles.message} role="status">
            Betalningarna kunde inte hämtas just nu.
          </p>
        ) : recentPayments.length === 0 ? (
          <p className={styles.message}>Du har inga betalningar ännu.</p>
        ) : (
          <ul className={styles.paymentList}>
            {recentPayments.map((payment) => (
              <li className={styles.payment} key={payment.paymentId}>
                <span className={styles.paymentIcon} aria-hidden="true">
                  <ArrowUpRight size={18} />
                </span>
                <div className={styles.paymentDetails}>
                  <span className={styles.paymentReference}>
                    {payment.reference?.trim() || "Betalning"}
                  </span>
                  <span className={styles.paymentMeta}>
                    {payment.createdAt
                      ? formatDateTime(payment.createdAt)
                      : "Datum saknas"}
                    <span className={styles.status}>{formatAuditStatus(payment.status)}</span>
                  </span>
                </div>
                <span className={styles.paymentAmount}>
                  {formatAmount(payment.amount, payment.currency)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

export function DashboardAccounts({
  standalone = false,
  showNewPayment = true,
}: {
  standalone?: boolean;
  showNewPayment?: boolean;
}) {
  return (
    <section
      className={`${styles.accounts} ${standalone ? styles.accountStandalone : ""}`}
      aria-labelledby="dashboard-accounts-title"
    >
      <header className={styles.sectionHeader}>
        <div>
          <h2 id="dashboard-accounts-title">Konton</h2>
          <p>Samlad överblick över företagets konton.</p>
        </div>
        {showNewPayment && (
          <Link className={styles.allPayments} to="/payments/new">
            Ny betalning <ArrowRight size={16} aria-hidden="true" />
          </Link>
        )}
      </header>

      <div className={styles.accountList}>
        {mockAccounts.map((account) => (
          <article className={styles.account} key={account.name}>
            <div className={styles.accountHeading}>
              <span className={styles.accountIcon} aria-hidden="true">
                <Landmark size={18} />
              </span>
              <div>
                <h3>{account.name}</h3>
                <p>{account.number}</p>
              </div>
            </div>
            <p className={styles.balance}>
              {sekFormatter.format(account.balance)} <span>SEK</span>
            </p>
          </article>
        ))}
      </div>

      <div className={styles.total}>
        <span>Totalt saldo</span>
        <strong>{sekFormatter.format(mockTotal)} SEK</strong>
      </div>
    </section>
  );
}