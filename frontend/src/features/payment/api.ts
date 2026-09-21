import type { PaymentFormData } from "./types";
import { createPaymentRequestSchema, paymentResponseSchema } from "./schema";
import { api } from "../../lib/api";

export async function createPayment(payment: PaymentFormData) {
  const request = createPaymentRequestSchema.parse({
    fromAccountId: getAccountId(payment.account),
    toIban: payment.recipientIban.replaceAll(" ", ""),
    amount: Number(payment.amount),
    reference: payment.reference,
  });

  return api(
    "/api/payments",
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    paymentResponseSchema,
  );
}

function getAccountId(account: string): number {
  switch (account) {
    case "driftkonto":
      return 1;

    case "lönekonto":
      return 2;

    case "projektkonto":
      return 3;

    default:
      throw new Error("Ogiltigt konto.");
  }
}
