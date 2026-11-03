import type { z } from "zod";

export function zodIssuesToFieldErrors<T extends string>(
  error: z.ZodError,
): Partial<Record<T, string>> {
  const fieldErrors: Partial<Record<T, string>> = {};

  for (const issue of error.issues) {
    const key = issue.path[0];

    if (typeof key === "string" && !(key in fieldErrors)) {
      fieldErrors[key as T] = issue.message;
    }
  }

  return fieldErrors;
}
