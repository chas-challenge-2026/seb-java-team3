import { z } from "zod";

const IBAN_FORMAT = /^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/;

function isValidIban(raw: string): boolean {
  const iban = raw.replace(/\s+/g, "").toUpperCase();

  if (!IBAN_FORMAT.test(iban)) {
    return false;
  }

  const rearranged = iban.slice(4) + iban.slice(0, 4);
  const numeric = rearranged.replace(/[A-Z]/g, (letter) =>
    String(letter.charCodeAt(0) - 55),
  );

  let remainder = 0;
  for (let i = 0; i < numeric.length; i += 7) {
    remainder = Number(`${remainder}${numeric.slice(i, i + 7)}`) % 97;
  }

  return remainder === 1;
}

export const paymentAccountSchema = z.enum(
  ["driftkonto", "lönekonto", "projektkonto"],
  { error: "Välj vilket konto betalningen ska dras från." },
);

export const ibanSchema = z
  .string()
  .trim()
  .min(1, "Ange mottagarens IBAN.")
  .refine(isValidIban, "Ange en giltig IBAN.");

export const paymentAmountSchema = z
  .string()
  .trim()
  .min(1, "Ange ett belopp.")
  .regex(/^\d+(\.\d{1,2})?$/, "Ange ett giltigt belopp med maximalt två decimaler.")
  .refine((value) => Number(value) > 0, "Beloppet måste vara större än 0.");

export const paymentReferenceSchema = z
  .string()
  .max(140, "Referensen får vara högst 140 tecken.");

export const paymentFormSchema = z.object({
  account: paymentAccountSchema,
  recipientIban: ibanSchema,
  amount: paymentAmountSchema,
  reference: paymentReferenceSchema,
});

export type PaymentFormValues = z.infer<typeof paymentFormSchema>;

export const createPaymentRequestSchema = z.object({
  fromAccountId: z.number().int().positive(),
  toIban: z.string().refine(isValidIban, "Ogiltig IBAN."),
  amount: z.number().positive(),
  reference: z.string().max(140),
});

export type CreatePaymentRequest = z.infer<typeof createPaymentRequestSchema>;

export const paymentResponseSchema = z.object({
  id: z.number(),
  amount: z.number(),
  toIban: z.string(),
  status: z.string(),
  createdAt: z.string().nullable(),
});

export type PaymentResponse = z.infer<typeof paymentResponseSchema>;
