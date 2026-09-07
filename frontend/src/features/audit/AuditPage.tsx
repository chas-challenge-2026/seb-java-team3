import AuditGrid from "./AuditGrid";
import { mockAuditData } from "./mockAuditData";
import { toAuditEntry } from "./types";

export default function AuditPage() {
    return (
        <AuditGrid entries={mockAuditData.map(toAuditEntry)} />
    );
}
