import { TriangleAlert } from "lucide-react"
import Button from "../buttons/Button"
import IconMessage from "./IconMessage"

interface LoadErrorMessageProps {
    title: string;
    message?: string;
    onRetry: () => void;
    isRetrying: boolean;
}

// Felläge för när en hämtning misslyckats, med möjlighet att försöka igen utan att ladda om sidan
const LoadErrorMessage = ({
    title,
    message = "Något gick fel. Kontrollera din anslutning och försök igen.",
    onRetry,
    isRetrying,
}: LoadErrorMessageProps) => {
  return (
    <IconMessage
        title={title}
        message={message}
        icon={TriangleAlert}
        variant="error"
        actions={
            <Button variant="primary" onClick={onRetry} disabled={isRetrying}>
                {isRetrying ? "Försöker igen…" : "Försök igen"}
            </Button>
        }
    />
  )
}

export default LoadErrorMessage
