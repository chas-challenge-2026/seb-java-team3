/// <reference types="node" />
import { beforeEach, describe, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { api } from "../../lib/api";
import { getTokenUserId } from "../../lib/authToken";
vi.mock("../../lib/api", () => ({ api: vi.fn() }));
vi.mock("../../lib/authToken", () => ({ getTokenUserId: vi.fn(() => 1) }));

const payment = { account: "42", recipientIban: "SE45 5000 0000 0583 9825 7466", amount: "1000", reference: "" };

describe("createPayment", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.mocked(api).mockReset();
    vi.mocked(getTokenUserId).mockReturnValue(1);
    vi.stubGlobal("crypto", webcrypto);
    sessionStorage.clear();
  });

  it("sends the selected backend account ID rather than a fixed mapping", async () => {
    const { createPayment } = await import("./api");
    await createPayment(payment);
    expect(api).toHaveBeenCalledWith("/api/payments", {
      method: "POST", body: JSON.stringify({ fromAccountId: 42, toIban: "SE4550000000058398257466", amount: 1000, reference: "" }),
      headers: { "Idempotency-Key": expect.any(String) },
    }, expect.anything());
  });

  it("reuses an unconfirmed attempt after a reload but starts a new one after acknowledgement", async () => {
    const { createPayment } = await import("./api");
    vi.mocked(api).mockRejectedValueOnce(new Error("response lost after commit"));
    await expect(createPayment(payment)).rejects.toThrow("response lost");
    const first = vi.mocked(api).mock.calls[0][1]?.headers;
    expect(sessionStorage.length).toBe(1);
    // Återskapa modulerna som efter en omladdning. Bara sessionStorage finns kvar.
    vi.resetModules();
    const reloaded = await import("./api");
    vi.mocked(api).mockResolvedValue({ id: 5 });
    await reloaded.createPayment(payment);
    expect(vi.mocked(api).mock.calls[1][1]?.headers).toEqual(first);
    expect(sessionStorage.length).toBe(0);
    await reloaded.createPayment(payment);
    expect(vi.mocked(api).mock.calls[2][1]?.headers).not.toEqual(first);
  });

  it("keeps outstanding attempts separate for changed details and other users", async () => {
    const { createPayment } = await import("./api");
    vi.mocked(api).mockRejectedValue(new Error("offline"));
    await expect(createPayment(payment)).rejects.toThrow();
    await expect(createPayment({ ...payment, account: "43" })).rejects.toThrow();
    vi.mocked(getTokenUserId).mockReturnValue(2);
    await expect(createPayment(payment)).rejects.toThrow();
    vi.mocked(getTokenUserId).mockReturnValue(1);
    await expect(createPayment(payment)).rejects.toThrow();
    const headers = vi.mocked(api).mock.calls.map((call) => call[1]?.headers);
    expect(headers[0]).not.toEqual(headers[1]);
    expect(headers[0]).not.toEqual(headers[2]);
    expect(headers[0]).toEqual(headers[3]);
    expect(sessionStorage.length).toBe(3);
    for (let i = 0; i < sessionStorage.length; i++) {
      expect(sessionStorage.key(i)).not.toContain("SE4550");
    }
  });

  it("never sends an attempt without an authenticated user", async () => {
    const { createPayment } = await import("./api");
    vi.mocked(getTokenUserId).mockReturnValue(null);
    await expect(createPayment(payment)).rejects.toMatchObject({ status: 401 });
    expect(api).not.toHaveBeenCalled();
  });

  describe("importPayments", () => {
    it("sends the selected CSV file as multipart form data", async () => {
      const { importPayments } = await import("./api");
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
