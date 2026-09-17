export function formatDateTime(timestamp: string | null): string {
  if (!timestamp) {
    return "Ej utförd";
  }

  return new Date(timestamp).toLocaleString("sv-SE", {
    dateStyle: "short",
    timeStyle: "short",
  });
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
