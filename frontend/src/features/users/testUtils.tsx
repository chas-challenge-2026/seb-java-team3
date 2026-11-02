import { vi } from "vitest";
import type { ReactElement } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { UserResponse } from "./types";

export const admin: UserResponse = { id: 1, name: "Anna Admin", email: "anna@foretag.se", role: "ADMIN" };
export const initiator: UserResponse = { id: 2, name: "Erik Eriksson", email: "erik@foretag.se", role: "INITIATOR" };
export const attestant: UserResponse = { id: 3, name: "Sara Svensson", email: "sara@foretag.se", role: "ATTESTANT" };

export function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const invalidate = vi.spyOn(queryClient, "invalidateQueries");

  return { queryClient, invalidate, ...render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>) };
}
