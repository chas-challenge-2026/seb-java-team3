import styles from "./AuditGrid.module.css";

export type AuditEntry = {
  id: string;
  tid: string;
  vem: string;
  handelse: string;
  betalning: string;
};

type AuditGridProps = {
  entries: AuditEntry[];
};

export default function AuditGrid({ entries }: AuditGridProps) {
  return (
    <table className={styles.grid}>
      <thead>
        <tr>
          <th>Tid</th>
          <th>Vem</th>
          <th>Händelse</th>
          <th>Betalning</th>
        </tr>
      </thead>
      <tbody>
        {entries.map((entry) => (
          <tr key={entry.id}>
            <td>{entry.tid}</td>
            <td>{entry.vem}</td>
            <td>{entry.handelse}</td>
            <td>{entry.betalning}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
