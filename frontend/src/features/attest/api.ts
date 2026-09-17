import { api } from "../../lib/api";
import type { AttestSteg, PendingApprovalCount } from "./types";

export async function getPendingApprovals(): Promise<AttestSteg[]> {
  return api<AttestSteg[]>("/api/approvals");
}

export async function approveStatus(stepId: number): Promise<void> {
  await api(`/api/approvals/${stepId}/approve`, { method: "POST" });
}

export async function getPendingApprovalCount(): Promise<number> {
  const response = await api<PendingApprovalCount>("/api/approvals/count");
  return response.count;
}
