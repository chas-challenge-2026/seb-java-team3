import { describe, expect, it, vi } from "vitest";
import { api } from "../../lib/api";
import { createPayment, importPayments } from "./api";
vi.mock("../../lib/api", () => ({ api: vi.fn() }));
describe("createPayment", () => {
  it("sends the selected backend account ID rather than a fixed mapping", async () => {
    await createPayment({ account: "42", recipientIban: "SE45 5000 0000 0583 9825 7466", amount: "1000", reference: "" });
    expect(api).toHaveBeenCalledWith("/api/payments", {
      method: "POST", body: JSON.stringify({ fromAccountId: 42, toIban: "SE4550000000058398257466", amount: 1000, reference: "" }),
    }, expect.anything());
  });

  describe("importPayments", () => {
    it("sends the selected CSV file as multipart form data", async () => {
      const file = new File(["csv contents"], "payments.csv", { type: "text/csv" });

      await importPayments(file);

      const [path, options] = vi.mocked(api).mock.calls.at(-1)!;
      expect(path).toBe("/api/payments/import");
      expect(options?.method).toBe("POST");
      expect(options?.body).toBeInstanceOf(FormData);
      expect((options?.body as FormData).get("file")).toBe(file);
    });
  });
});
