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

export interface CreatePaymentRequest {
  fromAccountId: number;
  toIban: string;
  amount: number;
  reference: string;
}

export interface PaymentResponse {
  id: number;
  amount: number;
  toIban: string;
  status: string;
  createdAt: string;
}
