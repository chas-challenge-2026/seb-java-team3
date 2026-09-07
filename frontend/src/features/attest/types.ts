export type AttestSteg = {
  id: number;
  paymentId: number;
  mottagare: string;
  typ: string;
  belopp: number;
  currency: string;
  reference?: string;
  createdAt: string;
  status: Status;
};

type Status = "Godkänd" | "Väntar" | "Pending";

export const mock: AttestSteg[] = [
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
