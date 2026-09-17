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
      queryClient.removeQueries();
      queryClient.setQueryData(["auth", "me"], {
        id: user.id,
        name: user.name,
        email: user.email,
        role: user.role,
      });
      navigate({ to: "/" });
    },
  });
}
