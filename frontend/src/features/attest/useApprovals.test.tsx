import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useApprovals } from "./useApprovals";
import { approveStatus, getPendingApprovals, rejectStatus } from "./api";
import { ApiError } from "../../error/api.error";

vi.mock("./api", () => ({
  approveStatus: vi.fn(),
  rejectStatus: vi.fn(),
  getPendingApprovals: vi.fn(),
  getPendingApprovalCount: vi.fn(),
}));

describe("useApprovals", () => {
  let queryClient: QueryClient;

  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }

  beforeEach(() => {
    vi.mocked(getPendingApprovals).mockResolvedValue([]);
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  // Någon annan hann godkänna eller avvisa: listan hämtas om så att raden försvinner av sig själv
  it("refreshes the list when the payment was already decided by someone else (409)", async () => {
    vi.mocked(approveStatus).mockRejectedValue(new ApiError(409, "Betalningen är redan behandlad."));
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    const { result } = renderHook(() => useApprovals(), { wrapper });

    result.current.approve.mutate(7);

    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ["approvals"] }));
  });

  it("does the same when a rejection comes too late", async () => {
    vi.mocked(rejectStatus).mockRejectedValue(new ApiError(409, "Betalningen är redan behandlad."));
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    const { result } = renderHook(() => useApprovals(), { wrapper });

    result.current.reject.mutate({ stepId: 7, comment: "" });

    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: ["approvals"] }));
  });

  it("keeps the list as it is for other errors, so the user can try again", async () => {
    vi.mocked(approveStatus).mockRejectedValue(new ApiError(400, "Beloppet överstiger tillgängligt saldo."));
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    const { result } = renderHook(() => useApprovals(), { wrapper });

    result.current.approve.mutate(7);

    await waitFor(() => expect(result.current.approve.isError).toBe(true));
    expect(invalidate).not.toHaveBeenCalled();
  });
});
