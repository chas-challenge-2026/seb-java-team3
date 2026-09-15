import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { fetchLoginUser } from "./api";
import { setToken } from "../../lib/authToken";

export function useLogin() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: fetchLoginUser,
    onSuccess: (user) => {
      setToken(user.token);
      queryClient.setQueryData(["auth", "me"], user);
      navigate({ to: "/" });
    },
  });
}
