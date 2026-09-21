import { z } from "zod";

export const auditApiEntrySchema = z.object({
  id: z.number(),
  action: z.string(),
  entityType: z.string(),
  entityId: z.number(),
  description: z.string(),
  status: z.string().nullable(),
  reference: z.string().nullable(),
  amount: z.number().nullable(),
  currency: z.string().nullable(),
  createdAt: z.string(),
  userName: z.string(),
});

export const auditApiEntryListSchema = z.array(auditApiEntrySchema);

export type AuditApiEntry = z.infer<typeof auditApiEntrySchema>;

export const myPaymentStatusSchema = z.object({
  paymentId: z.number(),
  reference: z.string().nullable(),
  amount: z.number().nullable(),
  currency: z.string().nullable(),
  toIban: z.string().nullable(),
  status: z.string(),
  createdAt: z.string().nullable(),
  executedAt: z.string().nullable(),
});

export const myPaymentStatusListSchema = z.array(myPaymentStatusSchema);

export type MyPaymentStatus = z.infer<typeof myPaymentStatusSchema>;

export const paymentAuditTimelineEntrySchema = z.object({
  order: z.number(),
  sequence: z.string(),
  actor: z.string(),
  eventType: z.string(),
  description: z.string(),
  timestamp: z.string().nullable(),
  stepNumber: z.number().nullable(),
  status: z.string(),
  reference: z.string().nullable(),
  amount: z.number().nullable(),
  currency: z.string().nullable(),
  toIban: z.string().nullable(),
});

export const paymentAuditTimelineEntryListSchema = z.array(
  paymentAuditTimelineEntrySchema,
);

export type PaymentAuditTimelineEntry = z.infer<
  typeof paymentAuditTimelineEntrySchema
>;
