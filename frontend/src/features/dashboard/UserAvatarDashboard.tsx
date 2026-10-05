import styles from "./UserAvatarDashboard.module.css";

interface UserAvatarDashboardProps {
  name: string;
}

export default function UserAvatarDashboard({ name }: UserAvatarDashboardProps) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");

  return (
    <section className={styles.welcome} aria-labelledby="dashboard-welcome-title">
      <div className={styles.message}>
        <p className={styles.eyebrow}>Personlig översikt</p>
        <h2 id="dashboard-welcome-title">Välkommen, {name}!</h2>
        <p className={styles.description}>
          Här får du en samlad bild av dina betalningar och konton.
        </p>
      </div>

      <div className={styles.profile} aria-label={`Inloggad som ${name}`}>
        <div className={styles.avatar} aria-hidden="true">
          {initials}
        </div>
        <div className={styles.profileText}>
          <span className={styles.profileName}>{name}</span>
          <span className={styles.profileStatus}>Inloggad</span>
        </div>
      </div>
    </section>
  );
}