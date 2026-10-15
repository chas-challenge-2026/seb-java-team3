import { z } from "zod";
import { queryOptions } from "@tanstack/react-query";
import { api } from "../../lib/api";
// reserved: summan av betalningar från kontot som väntar på attest. availableBalance: saldo minus reserved,
// det som går att betala med just nu. Valfria så att äldre svar (och testdata) utan dem fortfarande fungerar.
const accountSchema = z.object({
  id: z.number().int().positive(), name: z.string(), iban: z.string(),
  balance: z.number(), currency: z.string(),
  reserved: z.number().optional(), availableBalance: z.number().optional(),
});
const accountsSchema = z.array(accountSchema);

export type Account = z.infer<typeof accountSchema>;

// Det som går att betala med: saldot minus det som är reserverat för betalningar som väntar på attest
export function availableBalanceOf(account: Account): number {
  return account.availableBalance ?? account.balance;
}

export function getAccounts() { return api("/api/accounts", {}, accountsSchema); }
export const accountsQueryOptions = queryOptions({ queryKey: ["accounts"], queryFn: getAccounts });
