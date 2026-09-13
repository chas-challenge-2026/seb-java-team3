import type { PaymentFormData } from "./types";

export async function createPayment(
    payment: PaymentFormData
): Promise<void> {
    // KOPPLA TILL BACKEND NÄR ENDPOINTS ÄR FÄRDIGA

    console.log("Payment:", {
        ...payment,
        amount: Number(payment.amount)
    });
}