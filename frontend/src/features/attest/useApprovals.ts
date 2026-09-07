import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getPendingApprovals, approveStatus } from "./api";

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
