import LoginForm from "../features/auth/LoginForm";
import styles from "./Login.module.css";
import { Lock } from "lucide-react"
import Divider from "../components/ui/layout/Divider";

export function Login() {
  return (
    <main className={styles.wrapper}>

      <div className={styles.container}>

        <div className={styles.leftIcon}>
          <Lock size={65} strokeWidth={1.2} color="#ffffff"/>
        </div>

        <div className={styles.rightField}>
          <div className={styles.textField}>
            <h1>SEB Företagsbetalningar</h1>
            <p>Logga in för att komma åt ditt konto och dina tjänster.</p>
          </div>
            <Divider/>
            <span style={{marginTop: "1.4rem"}}/>
          <LoginForm />
        </div>
        
      </div>
    </main>
  );
}
