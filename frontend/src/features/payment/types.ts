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
  tenantId: number;
  fromAccountId: number;
  toIban: string;
  amount: number;
  reference: string;
  createdBy: number;
}