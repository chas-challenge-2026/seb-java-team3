import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  changedPaymentCountQueryKey,
  useChangedPaymentCount,
  useMarkChangedPaymentsSeen,
} from "./useChangedPaymentCount";
import { fetchChangedPaymentCount, markChangedPaymentsSeen } from "./api/auditApi";

vi.mock("./api/auditApi", async (importOriginal) => ({
  ...await importOriginal<typeof import("./api/auditApi")>(),
  fetchChangedPaymentCount: vi.fn(),
  markChangedPaymentsSeen: vi.fn(),
}));

describe("useChangedPaymentCount", () => {
  let queryClient: QueryClient;

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  beforeEach(() => {
    vi.mocked(fetchChangedPaymentCount).mockReset();
    vi.mocked(markChangedPaymentsSeen).mockReset();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  it("returns the count and marks My payments as out of date when something has changed", async () => {
    vi.mocked(fetchChangedPaymentCount).mockResolvedValue(2);
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    const { result } = renderHook(() => useChangedPaymentCount(true), { wrapper });

    await waitFor(() => expect(result.current.data).toBe(2));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["audit", "my-payments"] });
  });

  it("leaves My payments alone when nothing has changed", async () => {
    vi.mocked(fetchChangedPaymentCount).mockResolvedValue(0);
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    const { result } = renderHook(() => useChangedPaymentCount(true), { wrapper });

    await waitFor(() => expect(result.current.data).toBe(0));
    expect(invalidate).not.toHaveBeenCalled();
  });

  it("does not call the endpoint when disabled", () => {
    renderHook(() => useChangedPaymentCount(false), { wrapper });

    expect(fetchChangedPaymentCount).not.toHaveBeenCalled();
  });

  it("clears the badge once the payments are marked as seen", async () => {
    vi.mocked(markChangedPaymentsSeen).mockResolvedValue();
    queryClient.setQueryData(changedPaymentCountQueryKey, 3);
    const { result } = renderHook(() => useMarkChangedPaymentsSeen(), { wrapper });

    result.current.mutate();

    await waitFor(() => expect(queryClient.getQueryData(changedPaymentCountQueryKey)).toBe(0));
  });

  it("keeps the badge if marking as seen fails", async () => {
    vi.mocked(markChangedPaymentsSeen).mockRejectedValue(new Error("nätverksfel"));
    queryClient.setQueryData(changedPaymentCountQueryKey, 3);
    const { result } = renderHook(() => useMarkChangedPaymentsSeen(), { wrapper });

    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(queryClient.getQueryData(changedPaymentCountQueryKey)).toBe(3);
  });
});
