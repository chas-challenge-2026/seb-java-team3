import AuditGrid from "./AuditGrid";
import { mockAuditData } from "./mockAuditData";
import { toAuditEntry } from "./types";
import Container from "../../components/ui/layout/Container";

export default function AuditPage() {
    return (
        <Container maxWidth="xl">
        <AuditGrid entries={mockAuditData.map(toAuditEntry)} />
        </Container>
    );
}
