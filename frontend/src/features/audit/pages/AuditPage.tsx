import { useQuery } from "@tanstack/react-query";
import AuditGrid from "../components/AuditGrid";
import { auditQueryOptions } from "../queries/auditQueryOptions";
import { latestAuditEntryByPayment, toAuditEntry } from "../utils/mappers";
import Container from "../../../components/ui/layout/Container";

export default function AuditPage() {
    const { data, isPending, isError } = useQuery(auditQueryOptions);

    return (
        <Container maxWidth="xl" variant="white" style={{ marginTop: "2rem" }} >
            {isPending ? (
                <p>Laddar händelser…</p>
            ) : isError ? (
                <p role="alert">Kunde inte hämta händelser. Försök igen senare.</p>
            ) : data.length === 0 ? (
                <p>Inga händelser än.</p>
            ) : (
                <AuditGrid entries={latestAuditEntryByPayment(data).map(toAuditEntry)} />
            )}
        </Container>
    );
}
