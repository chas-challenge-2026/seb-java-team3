import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { fetchLoginUser } from "./api";

export function useLogin() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: fetchLoginUser,
    onSuccess: (user) => {
      queryClient.setQueryData(["auth", "me"], user);
      navigate({ to: "/" });
    },
  });
}
