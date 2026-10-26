import { describe, expect, it, vi } from "vitest";
import { api } from "../../lib/api";
import { createPayment } from "./api";
vi.mock("../../lib/api", () => ({ api: vi.fn() }));
describe("createPayment", () => {
  it("sends the selected backend account ID rather than a fixed mapping", async () => {
    await createPayment({ account: "42", recipientIban: "SE45 5000 0000 0583 9825 7466", amount: "1000", reference: "" });
    expect(api).toHaveBeenCalledWith("/api/payments", {
      method: "POST", body: JSON.stringify({ fromAccountId: 42, toIban: "SE4550000000058398257466", amount: 1000, reference: "" }),
    }, expect.anything());
  });
});
