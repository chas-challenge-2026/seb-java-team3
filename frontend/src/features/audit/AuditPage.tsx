import { useQuery } from "@tanstack/react-query";
import AuditGrid from "./AuditGrid";
import { auditQueryOptions } from "./auditQueryOptions";
import { toAuditEntry } from "./types";
import Container from "../../components/ui/layout/Container";

export default function AuditPage() {
    const { data, isPending, isError } = useQuery(auditQueryOptions);

    return (
        <Container maxWidth="xl">
            {isPending ? (
                <p>Laddar händelser…</p>
            ) : isError ? (
                <p role="alert">Kunde inte hämta händelser. Försök igen senare.</p>
            ) : data.length === 0 ? (
                <p>Inga händelser än.</p>
            ) : (
                <AuditGrid entries={data.map(toAuditEntry)} />
            )}
        </Container>
    );
}
