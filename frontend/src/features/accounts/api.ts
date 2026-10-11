import { z } from "zod";
import { queryOptions } from "@tanstack/react-query";
import { api } from "../../lib/api";
const accountsSchema = z.array(z.object({
  id: z.number().int().positive(), name: z.string(), iban: z.string(),
  balance: z.number(), currency: z.string(),
}));
export function getAccounts() { return api("/api/accounts", {}, accountsSchema); }
export const accountsQueryOptions = queryOptions({ queryKey: ["accounts"], queryFn: getAccounts });
