import Input from "../../components/ui/forms/Input";
import { useState } from "react";
import { useLogin } from "./useLogin";
import { isApiError } from "../../error/api.error";
import Button from "../../components/ui/buttons/Button";
import styles from "./LoginForm.module.css"

type FieldErrors = Record<string, string>;

export default function LoginForm() {
  const [email, setEmail] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const { mutate: login, isPending, error } = useLogin();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    login({ email, password });
  }

  const fieldErrors: FieldErrors =
    isApiError(error) && error.data && typeof error.data === "object"
      ? ((error.data as { errors?: FieldErrors }).errors ?? {})
      : {};

  const message = isApiError(error)
    ? error.message
    : error
      ? "Couldn't reach the server."
      : null;

  return (
    <form onSubmit={handleSubmit}>
      <Input
        label="E-post"
        name="email"
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fieldErrors.email}
        required
      />
      <Input
        label="Lösenord"
        name="password"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fieldErrors.password}
        required
      />

      {message && Object.keys(fieldErrors).length === 0 && (
        <p role="alert">{message}</p>
      )}
      <div className={styles.btnContainer}>
      <Button className={styles.loginBtn} buttonStyle="icon-text" type="submit" variant="primary" icon="chevron" disabled={isPending}>
        {isPending ? "Loggar in…" : "Logga in"}
      </Button>
      </div>
    </form>
  );
}
