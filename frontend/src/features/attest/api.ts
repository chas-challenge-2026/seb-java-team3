import { api } from "../../lib/api";
import { attestStegListSchema, pendingApprovalCountSchema } from "./schema";

export async function getPendingApprovals() {
  return api("/api/approvals", {}, attestStegListSchema);
}

export async function approveStatus(stepId: number): Promise<void> {
  await api(`/api/approvals/${stepId}/approve`, { method: "POST" });
}

// Avvisar betalningen. Kommentaren (högst 255 tecken) sparas på steget och syns i auditloggen.
export async function rejectStatus({ stepId, comment }: { stepId: number; comment: string }): Promise<void> {
  await api(`/api/approvals/${stepId}/reject`, {
    method: "POST",
    body: JSON.stringify({ comment: comment.trim() || null }),
  });
}

export async function getPendingApprovalCount(): Promise<number> {
  const response = await api("/api/approvals/count", {}, pendingApprovalCountSchema);
  return response.count;
}
