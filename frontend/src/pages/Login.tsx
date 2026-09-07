import LoginForm from "../features/auth/LoginForm";
import Container from "../components/ui/layout/Container";
import styles from "./Login.module.css";

export function Login() {
  return (
    <main className={styles.mainContainer}>
        <div className={styles.textField}>
      <h1>Välkommen tillbaka</h1>
      <p>Logga in för att komma åt ditt konto och dina tjänster.</p>
        </div>
      <Container maxWidth="md">
        <LoginForm />
      </Container>
    </main>
  );
}
