import type { PaymentFormData } from "./types";
import {
  createPaymentRequestSchema,
  paymentConfigSchema,
  paymentResponseSchema,
} from "./schema";
import { api } from "../../lib/api";

export async function createPayment(payment: PaymentFormData) {
  const request = createPaymentRequestSchema.parse({
    fromAccountId: Number(payment.account),
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

export async function getPaymentConfig() {
  return api("/api/payments/config", {}, paymentConfigSchema);
}
