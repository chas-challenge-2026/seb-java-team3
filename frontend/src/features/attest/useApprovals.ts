import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { isApiError } from "../../error/api.error";
import {
  approveStatus,
  rejectStatus,
  getPendingApprovalCount,
  getPendingApprovals,
} from "./api";

export function useApprovals() {
  const qc = useQueryClient();

  // 409: någon annan hann godkänna eller avvisa. Hämta om listan så att raden försvinner av sig själv.
  const refreshIfAlreadyDecided = (error: unknown) => {
    if (isApiError(error) && error.status === 409) {
      qc.invalidateQueries({ queryKey: ["approvals"] });
    }
  };

  const query = useQuery({
    queryKey: ["approvals"],
    queryFn: getPendingApprovals,
  });

  const approve = useMutation({
    mutationFn: approveStatus,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approvals"] });
      qc.invalidateQueries({ queryKey: ["audit"] });
      qc.invalidateQueries({ queryKey: ["accounts"] });
    },
    onError: refreshIfAlreadyDecided,
  });

  // En avvisad betalning släpper sin reservation, så saldot och historiken ändras också
  const reject = useMutation({
    mutationFn: rejectStatus,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approvals"] });
      qc.invalidateQueries({ queryKey: ["audit"] });
      qc.invalidateQueries({ queryKey: ["accounts"] });
    },
    onError: refreshIfAlreadyDecided,
  });

  return { ...query, approve, reject };
}

export function usePendingApprovalCount(enabled: boolean) {
  return useQuery({
    queryKey: ["approvals", "count"],
    queryFn: getPendingApprovalCount,
    enabled,
  });
}
