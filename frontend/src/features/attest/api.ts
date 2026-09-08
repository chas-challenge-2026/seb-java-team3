import { api } from "../../lib/api";
import type { AttestSteg } from "./types";


const USE_MOCK = true;

export async function getPendingApprovals(): Promise<AttestSteg[]> {
  if (USE_MOCK) return mockList();
  return api<AttestSteg[]>('/api/approvals');
}

export async function approveStatus(stepId: number): Promise<void> {
  if (USE_MOCK) return mockApprove(stepId);
  await api(`/api/approvals/${stepId}/approve`, { method: 'POST' });
}

let mock: AttestSteg[] = [
  {
    id: 1,
    paymentId: 101,
    mottagare: "Malmö Bygg AB",
    typ: "Betalning",
    belopp: 250000,
    currency: "SEK",
    reference: "Faktura 99",
    createdAt: new Date().toISOString(),
    status: "Pending",
  },
  {
    id: 2,
    paymentId: 102,
    mottagare: "Nordic El",
    typ: "Betalning",
    belopp: 88000,
    currency: "SEK",
    createdAt: new Date().toISOString(),
    status: "Godkänd",
  },
];

const delay = (ms: number) => new Promise(r => setTimeout(r, ms));
async function mockList()  { await delay(300); return [...mock]; }
async function mockApprove(id: number) { await delay(300); mock = mock.filter(s => s.id !== id); }
