import { api } from "../../lib/api";
import type { AttestSteg } from "./types";

export async function getPendingApprovals(): Promise<AttestSteg[]> {
  return api<AttestSteg[]>('/api/approvals');
}

export async function approveStatus(stepId: number): Promise<void> {
  await api(`/api/approvals/${stepId}/approve`, { method: 'POST' });
}
