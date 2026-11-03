import { useState } from "react";
import { useRouter, type ErrorComponentProps } from "@tanstack/react-router";
import Container from "../../components/ui/layout/Container";
import LoadErrorMessage from "../../components/ui/feedback/LoadErrorMessage";
import { ApiError } from "../../error/api.error";

// Felvy för de inloggade sidorna. Visas bland annat när sessionen inte gick att kontrollera för att servern
// inte svarade (requireAuth): användaren skickas då inte till inloggningen, utan kan försöka igen.
export function AuthErrorView({ error, reset }: ErrorComponentProps) {
  const router = useRouter();
  const [isRetrying, setIsRetrying] = useState(false);

  const handleRetry = async () => {
    setIsRetrying(true);
    reset();
    await router.invalidate();
    setIsRetrying(false);
  };

  return (
    <Container>
      <LoadErrorMessage
        title="Sidan kunde inte visas"
        message={error instanceof ApiError ? error.message : undefined}
        onRetry={handleRetry}
        isRetrying={isRetrying}
      />
    </Container>
  );
}
