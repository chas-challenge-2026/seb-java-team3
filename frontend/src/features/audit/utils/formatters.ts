import type { MyPaymentStatus } from "../schema";

export { formatDateTime, getDateTimeSortValue } from "../../../lib/dateTime";

// Vem betalningen hänger på, bredvid statusen: den som ska attestera nu, den som avvisade, eller de som
// godkände. Om en betalning fastnar ser initiatören vem som ska kontaktas (UX-feedback).
export function formatAttestant(payment: MyPaymentStatus): string {
  const steps = [...payment.approvalSteps].sort((a, b) => a.stepNumber - b.stepNumber);

  const rejected = steps.find((step) => step.status === "REJECTED");
  if (rejected) {
    return rejected.attestantName;
  }

  const pending = steps.find((step) => step.status === "PENDING");
  if (pending && payment.status === "PENDING_APPROVAL") {
    return pending.attestantName;
  }

  const approvers = steps.filter((step) => step.status === "APPROVED").map((step) => step.attestantName);
  return approvers.length > 0 ? approvers.join(", ") : "-";
}

export function formatAuditStatus(status: string | null): string {
  switch (status) {
    case "PENDING_APPROVAL":
      return "Väntar attest";
    case "COMPLETED":
      return "Genomförd";
    case "REJECTED":
      return "Avvisad";
    default:
      return "-";
  }
}

export function formatAmount(amount: number | null, currency: string | null): string {
  if (amount === null) {
    return "-";
  }

  const formattedAmount = amount.toLocaleString("sv-SE", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  return currency ? `${formattedAmount} ${currency}` : formattedAmount;
}

export function formatEventType(eventType: string): string {
  switch (eventType) {
    case "CREATE_PAYMENT":
      return "Betalning skapad";
    case "APPROVE_PAYMENT":
      return "Betalning godkänd";
    case "REJECT_PAYMENT":
      return "Betalning avvisad";
    case "APPROVAL_STEP_PENDING":
      return "Atteststeg väntar";
    case "APPROVAL_STEP_APPROVED":
      return "Atteststeg godkänt";
    case "APPROVAL_STEP_REJECTED":
      return "Atteststeg avvisat";
    default:
      return eventType;
  }
}
