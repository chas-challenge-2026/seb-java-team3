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
});

export const attestStegListSchema = z.array(attestStegSchema);

export type AttestSteg = z.infer<typeof attestStegSchema>;

export const pendingApprovalCountSchema = z.object({
  count: z.number(),
});

export type PendingApprovalCount = z.infer<typeof pendingApprovalCountSchema>;
