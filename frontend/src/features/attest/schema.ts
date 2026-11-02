import { z } from "zod";

const statusSchema = z.enum(["Godkänd", "Väntar", "Pending"]);

export const attestStegSchema = z.object({
  id: z.number(),
  paymentId: z.number(),
  mottagare: z.string(),
  typ: z.string(),
  belopp: z.number(),
  currency: z.string(),
  reference: z.string().optional(),
  createdAt: z.string(),
  status: statusSchema,
  // Kontot som belastas och vem som skapade betalningen (och kan svara på frågor om den).
  // Null om de inte går att slå upp.
  fromAccountName: z.string().nullable().default(null),
  balanceAfterPayment: z.number().nullable().default(null),
  initiatorName: z.string().nullable().default(null),
  // Var i kedjan steget ligger. Betalningen genomförs först när sista steget är godkänt.
  stepNumber: z.number(),
  totalSteps: z.number(),
  // Vem som redan har godkänt, så att nästa attestant vet vem som granskat betalningen före
  previousApprovals: z.array(z.object({
    stepNumber: z.number(),
    approverName: z.string(),
  })),
});

export const attestStegListSchema = z.array(attestStegSchema);

export type AttestSteg = z.infer<typeof attestStegSchema>;

export const pendingApprovalCountSchema = z.object({
  count: z.number(),
});

export type PendingApprovalCount = z.infer<typeof pendingApprovalCountSchema>;
