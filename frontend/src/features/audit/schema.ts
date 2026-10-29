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
  balanceBeforePayment: z.number().nullable().default(null),
  balanceAfterPayment: z.number().nullable().default(null),
  // Kontot betalningen dras från. Null om det inte går att slå upp.
  fromAccountName: z.string().nullable().default(null),
  // Genomförd utan atteststeg, eftersom beloppet låg under tröskeln
  autoApproved: z.boolean().default(false),
  // Attestantens meddelande när händelsen är ett godkännande eller en avvisning
  comment: z.string().nullable().default(null),
});

export const auditApiEntryListSchema = z.array(auditApiEntrySchema);

export type AuditApiEntry = z.infer<typeof auditApiEntrySchema>;

export const myPaymentApprovalStepSchema = z.object({
  stepNumber: z.number(),
  attestantName: z.string(),
  status: z.string(),
  decidedAt: z.string().nullable(),
  // Attestantens motivering när steget avvisats
  comment: z.string().nullable().default(null),
});

export type MyPaymentApprovalStep = z.infer<typeof myPaymentApprovalStepSchema>;

export const myPaymentStatusSchema = z.object({
  paymentId: z.number(),
  reference: z.string().nullable(),
  amount: z.number().nullable(),
  currency: z.string().nullable(),
  // Kontot betalningen dras från. Null om kontot inte går att slå upp.
  fromAccountName: z.string().nullable().default(null),
  toIban: z.string().nullable(),
  status: z.string(),
  createdAt: z.string().nullable(),
  executedAt: z.string().nullable(),
  // Vem som ska attestera, i stegordning. Tom när betalningen låg under tröskeln.
  approvalSteps: z.array(myPaymentApprovalStepSchema).default([]),
});

export const myPaymentStatusListSchema = z.array(myPaymentStatusSchema);

export type MyPaymentStatus = z.infer<typeof myPaymentStatusSchema>;

export const paymentAuditTimelineEntrySchema = z.object({
  order: z.number(),
  sequence: z.string(),
  actor: z.string(),
  eventType: z.string(),
  description: z.string(),
  // Attestantens meddelande. Admin får det också på atteststeget, så samma text kommer två gånger; se
  // formatTimelineComment.
  comment: z.string().nullable().default(null),
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
