import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "@tanstack/react-router";
import { api } from "../lib/api";

export function Login() {
    const navigate = useNavigate();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError(null);
        setLoading(true);
        try {
            await api("/api/auth/login", {
                method: "POST",
                body: JSON.stringify({ email, password }),
            });
            await navigate({ to: "/" });
        } catch (loginError) {
            setError(loginError instanceof Error ? loginError.message : "Kunde inte logga in.");
        } finally {
            setLoading(false);
        }
    }

    return (
        <main>
            <h1>Logga in</h1>
            <form onSubmit={submit}>
                <label>
                    E-post
                    <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
                </label>
                <label>
                    Lösenord
                    <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
                </label>
                {error && <p role="alert">{error}</p>}
                <button type="submit" disabled={loading}>{loading ? "Loggar in..." : "Logga in"}</button>
            </form>
        </main>
    );
}