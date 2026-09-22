import { api } from "../../lib/api";
import { attestStegListSchema, pendingApprovalCountSchema } from "./schema";

export async function getPendingApprovals() {
  return api("/api/approvals", {}, attestStegListSchema);
}

export async function approveStatus(stepId: number): Promise<void> {
  await api(`/api/approvals/${stepId}/approve`, { method: "POST" });
}

export async function getPendingApprovalCount(): Promise<number> {
  const response = await api("/api/approvals/count", {}, pendingApprovalCountSchema);
  return response.count;
}
