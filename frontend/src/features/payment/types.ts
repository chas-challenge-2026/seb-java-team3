export type { CreatePaymentRequest, PaymentResponse } from "./schema";

export interface PaymentFormData {
  account: string;
  recipientIban: string;
  amount: string;
  reference: string;
}

export interface PaymentFormErrors {
  account?: string;
  recipientIban?: string;
  amount?: string;
  reference?: string;
}
