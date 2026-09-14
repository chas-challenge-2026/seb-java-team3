import type {
  CreatePaymentRequest,
  PaymentFormData,
} from "./types";

export async function createPayment(
  payment: PaymentFormData
): Promise<void> {
  const request: CreatePaymentRequest = {
    tenantId: 1,
    fromAccountId: getAccountId(payment.account),
    toIban: payment.recipientIban,
    amount: Number(payment.amount),
    reference: payment.reference,
    createdBy: 1,
  };

  const response = await fetch("/api/payments", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error("Kunde inte skapa betalningen.");
  }
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