import { useQuery } from "@tanstack/react-query";
import { History } from "lucide-react";
import AuditGrid from "../components/AuditGrid";
import { auditQueryOptions } from "../queries/auditQueryOptions";
import { latestAuditEntryByPayment, toAuditEntry } from "../utils/mappers";
import Container from "../../../components/ui/layout/Container";
import IconMessage from "../../../components/ui/feedback/IconMessage";
import LoadErrorMessage from "../../../components/ui/feedback/LoadErrorMessage";
import LoadingMessage from "../../../components/ui/feedback/LoadingMessage";

export default function AuditPage() {
    const { data, isPending, isError, isFetching, refetch } = useQuery(auditQueryOptions);

    return (
        <Container maxWidth="xl">
            {isPending ? (
                <LoadingMessage message="Laddar händelser…" />
            ) : isError ? (
                <LoadErrorMessage
                    title="Kunde inte hämta händelserna"
                    onRetry={() => refetch()}
                    isRetrying={isFetching}
                />
            ) : data.length === 0 ? (
                <IconMessage
                    title="Inga händelser än"
                    message="Här visas händelserna när betalningar skapas och attesteras."
                    icon={History}
                />
            ) : (
                <AuditGrid entries={latestAuditEntryByPayment(data).map(toAuditEntry)} />
            )}
        </Container>
    );
}
