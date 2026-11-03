import LoginForm from "../features/auth/LoginForm";
import styles from "./Login.module.css";

export function Login() {
  return (
    <main className={styles.wrapper}>
      <div className={styles.container}>
        <section className={styles.brandPanel} aria-label="SEB Företagsbetalningar">
          <div>
            <p className={styles.brandName}>SEB</p>
            <h1>Företagsbetalningar</h1>
            <p className={styles.brandDescription}>En trygg plats för ditt företags betalningar och attesteringar.</p>
          </div>
        </section>

        <section className={styles.loginPanel} aria-labelledby="login-title">
          <header className={styles.textField}>
            <p>Välkommen tillbaka</p>
            <h2 id="login-title">Logga in</h2>
            <span>Logga in för att fortsätta till ditt företagskonto.</span>
          </header>
          <LoginForm />
        </section>
      </div>
    </main>
  );
}
