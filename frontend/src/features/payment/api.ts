import type { PaymentFormData } from "./types";
import {
  createPaymentRequestSchema,
  paymentConfigSchema,
  paymentResponseSchema,
} from "./schema";
import { api } from "../../lib/api";
import { getTokenUserId } from "../../lib/authToken";
import { ApiError } from "../../error/api.error";
import { paymentAttempt } from "./paymentAttempt";

export async function createPayment(payment: PaymentFormData) {
  const request = createPaymentRequestSchema.parse({
    fromAccountId: Number(payment.account),
    toIban: payment.recipientIban.replace(/\s/g, "").toUpperCase(),
    amount: Number(payment.amount),
    reference: payment.reference,
  });

  const userId = getTokenUserId();
  if (userId === null) throw new ApiError(401, "Logga in igen innan du skickar betalningen.");
  const body = JSON.stringify(request);
  const attempt = await paymentAttempt(userId, body);
  const response = await api(
    "/api/payments",
    {
      method: "POST",
      body,
      headers: { "Idempotency-Key": attempt.key },
    },
    paymentResponseSchema,
  );
  attempt.confirmed();
  return response;
}

export async function getPaymentConfig() {
  return api("/api/payments/config", {}, paymentConfigSchema);
}
