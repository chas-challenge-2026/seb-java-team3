import styles from "./AuditGrid.module.css";

// Värdena är betalningens status från backend; null betyder alla.
const STATUS_FILTERS: { key: string | null; label: string }[] = [
  { key: null, label: "Alla" },
  { key: "PENDING_APPROVAL", label: "Väntar attest" },
  { key: "COMPLETED", label: "Genomförda" },
  { key: "REJECTED", label: "Avvisade" },
];

type StatusFilterProps = {
  statuses: (string | null)[];
  value: string | null;
  onChange: (status: string | null) => void;
};

// Statusfiltret i auditloggen och Mina betalningar. Antalet räknas på statuses, en per rad.
export default function StatusFilter({ statuses, value, onChange }: StatusFilterProps) {
  function countFor(key: string | null): number {
    return key ? statuses.filter((status) => status === key).length : statuses.length;
  }

  return (
    <div className={styles.filters} role="group" aria-label="Filtrera på status">
      {STATUS_FILTERS.map((filter) => (
        <button
          key={filter.label}
          type="button"
          className={styles.filterButton}
          aria-pressed={value === filter.key}
          onClick={() => onChange(filter.key)}
        >
          {filter.label} <span>{countFor(filter.key)}</span>
        </button>
      ))}
    </div>
  );
}
