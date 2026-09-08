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

