import type { UserRole } from "../auth/types";

export const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: "Administratör",
  ATTESTANT: "Attestant",
  INITIATOR: "Initiator",
};

export const CREATABLE_ROLE_OPTIONS = [
  { value: "ATTESTANT", label: ROLE_LABELS.ATTESTANT },
  { value: "INITIATOR", label: ROLE_LABELS.INITIATOR },
];
