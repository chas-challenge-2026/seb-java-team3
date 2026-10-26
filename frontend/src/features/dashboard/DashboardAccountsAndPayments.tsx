import { useQuery } from "@tanstack/react-query";
import { ArrowRight, ArrowUpRight, Landmark } from "lucide-react";
import { Link } from "@tanstack/react-router";
import { myPaymentsQueryOptions } from "../audit/queries/auditQueryOptions";
import {
  formatAmount,
  formatAuditStatus,
  formatDateTime,
} from "../audit/utils/formatters";
import { getDateTimeSortValue } from "../../lib/dateTime";
import { accountsQueryOptions, availableBalanceOf } from "../accounts/api";
import { useUser } from "../auth/useUser";
import styles from "./DashboardAccountsAndPayments.module.css";

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
  const { data: user } = useUser();
  const canReadAccounts = user?.role === "INITIATOR" || user?.role === "ATTESTANT" || user?.role === "ADMIN";
  const { data: accounts = [], isPending, isError, refetch } = useQuery({ ...accountsQueryOptions, enabled: canReadAccounts });
  const totals = accounts.reduce<Record<string, number>>((result, account) => {
    result[account.currency] = (result[account.currency] ?? 0) + account.balance;
    return result;
  }, {});
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

      {!canReadAccounts ? <p className={styles.message}>Din roll har inte behörighet att visa konton.</p>
        : isPending ? <p className={styles.message} role="status">Laddar konton…</p>
        : isError ? <p className={styles.message} role="alert">Kontona kunde inte hämtas. <button type="button" onClick={() => void refetch()}>Försök igen</button></p>
        : accounts.length === 0 ? <p className={styles.message}>Företaget har inga konton.</p>
        : <>
      <div className={styles.accountList}>
        {accounts.map((account) => (
          <article className={styles.account} key={account.id}>
            <div className={styles.accountHeading}>
              <span className={styles.accountIcon} aria-hidden="true">
                <Landmark size={18} />
              </span>
              <div>
                <h3>{account.name}</h3>
                <p>{account.iban}</p>
              </div>
            </div>
            <div>
              <p className={styles.balance}>
                {sekFormatter.format(account.balance)} <span>{account.currency}</span>
              </p>
              {(account.reserved ?? 0) > 0 && (
                <p className={styles.available}>
                  Tillgängligt {sekFormatter.format(availableBalanceOf(account))} {account.currency}
                </p>
              )}
            </div>
          </article>
        ))}
      </div>

      <div className={styles.total}>
        <span>Totalt saldo</span>
        <strong>{Object.entries(totals).map(([currency, total]) => (
          <span key={currency}>{sekFormatter.format(total)} {currency}<br /></span>
        ))}</strong>
      </div>
      </>}
    </section>
  );
}
