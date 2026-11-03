import Input from "../../components/ui/forms/Input";
import { useState } from "react";
import { useLogin } from "./useLogin";
import { isApiError } from "../../error/api.error";
import { loginInputSchema } from "./schema";
import { zodIssuesToFieldErrors } from "../../lib/zodErrors";
import Button from "../../components/ui/buttons/Button";
import styles from "./LoginForm.module.css"

type FieldErrors = Partial<Record<string, string>>;

export default function LoginForm() {
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [clientErrors, setClientErrors] = useState<FieldErrors>({});
  const { mutate: login, isPending, error } = useLogin();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const result = loginInputSchema.safeParse({ email, password });

    if (!result.success) {
      setClientErrors(zodIssuesToFieldErrors(result.error));
      return;
    }

    setClientErrors({});
    login(result.data);
  }

  const serverFieldErrors: FieldErrors =
    isApiError(error) && error.data && typeof error.data === "object"
      ? ((error.data as { errors?: FieldErrors }).errors ?? {})
      : {};

  const fieldErrors: FieldErrors = { ...serverFieldErrors, ...clientErrors };

  const message = getLoginErrorMessage(error);

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <Input
        label="E-post"
        name="email"
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fieldErrors.email}
        className={styles.inputField}
        aria-required
      />
      <Input
        label="Lösenord"
        name="password"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
        className={styles.inputField}
        aria-required
      />

      {message && Object.keys(fieldErrors).length === 0 && (
        <p className={styles.formError} role="alert">{message}</p>
      )}
      <div className={styles.btnContainer}>
      <Button className={styles.loginBtn} buttonStyle="icon-text" type="submit" variant="primary" icon="chevron" disabled={isPending}>
        {isPending ? "Loggar in…" : "Logga in"}
      </Button>
      </div>
    </form>
  );
}

function getLoginErrorMessage(error: unknown): string | null {
  if (!error) {
    return null;
  }

  // Vid inloggning betyder 401 fel uppgifter, inte att sessionen gått ut
  if (isApiError(error) && error.status === 401) {
    return "Fel e-post eller lösenord. Kontrollera uppgifterna och försök igen.";
  }

  if (isApiError(error)) {
    return error.message;
  }

  return "Något gick fel när du skulle loggas in. Försök igen om en stund.";
}
