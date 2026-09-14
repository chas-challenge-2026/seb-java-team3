import type {
  CreatePaymentRequest,
  PaymentFormData,
  PaymentResponse,
} from "./types";
import { api } from "../../lib/api";

export async function createPayment(
  payment: PaymentFormData
): Promise<PaymentResponse> {
  const request: CreatePaymentRequest = {
    fromAccountId: getAccountId(payment.account),
    toIban: payment.recipientIban.replaceAll(" ", ""),
    amount: Number(payment.amount),
    reference: payment.reference,
  };

  return api<PaymentResponse>("/api/payments", {
    method: "POST",
    body: JSON.stringify(request),
  });
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
