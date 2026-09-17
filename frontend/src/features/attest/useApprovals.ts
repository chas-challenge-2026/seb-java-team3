import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  approveStatus,
  getPendingApprovalCount,
  getPendingApprovals,
} from "./api";

export function useApprovals() {
  const qc = useQueryClient();

  const query = useQuery({
    queryKey: ["approvals"],
    queryFn: getPendingApprovals,
  });

  const approve = useMutation({
    mutationFn: approveStatus,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["approvals"] });
      qc.invalidateQueries({ queryKey: ["audit"] });
    },
  });

  return { ...query, approve };
}

export function usePendingApprovalCount(enabled: boolean) {
  return useQuery({
    queryKey: ["approvals", "count"],
    queryFn: getPendingApprovalCount,
    enabled,
  });
}
