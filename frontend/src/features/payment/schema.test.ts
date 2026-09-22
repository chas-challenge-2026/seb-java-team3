import { describe, expect, it } from "vitest";
import {
  createPaymentRequestSchema,
  ibanSchema,
  paymentAmountSchema,
  paymentFormSchema,
  paymentResponseSchema,
} from "./schema";

describe("ibanSchema", () => {
  it("accepts a valid, spaced Swedish IBAN", () => {
    const result = ibanSchema.safeParse("SE45 5000 0000 0583 9825 7466");
    expect(result.success).toBe(true);
  });

  it("accepts a valid German IBAN", () => {
    const result = ibanSchema.safeParse("DE89370400440532013000");
    expect(result.success).toBe(true);
  });

  it("rejects an IBAN with a bad checksum (single mistyped digit)", () => {
    const result = ibanSchema.safeParse("SE45 5000 0000 0583 9825 7467");
    expect(result.success).toBe(false);
  });

  it("rejects an empty value", () => {
    const result = ibanSchema.safeParse("");
    expect(result.success).toBe(false);
    expect(result.error?.issues[0].message).toBe("Ange mottagarens IBAN.");
  });

  it("rejects garbage input", () => {
    const result = ibanSchema.safeParse("not an iban");
    expect(result.success).toBe(false);
  });
});

describe("paymentAmountSchema", () => {
  it.each(["1000", "1000.5", "1000.50", "0.01"])(
    "accepts %s as a valid amount",
    (value) => {
      expect(paymentAmountSchema.safeParse(value).success).toBe(true);
    },
  );

  it("rejects zero", () => {
    expect(paymentAmountSchema.safeParse("0").success).toBe(false);
  });

  it("rejects negative amounts", () => {
    expect(paymentAmountSchema.safeParse("-5").success).toBe(false);
  });

  it("rejects more than two decimals", () => {
    expect(paymentAmountSchema.safeParse("10.123").success).toBe(false);
  });

  it("rejects an empty amount", () => {
    const result = paymentAmountSchema.safeParse("");
    expect(result.success).toBe(false);
    expect(result.error?.issues[0].message).toBe("Ange ett belopp.");
  });
});

describe("paymentFormSchema", () => {
  const validForm = {
    account: "driftkonto",
    recipientIban: "SE45 5000 0000 0583 9825 7466",
    amount: "1000.00",
    reference: "Faktura #1234",
  };

  it("accepts a fully valid form", () => {
    expect(paymentFormSchema.safeParse(validForm).success).toBe(true);
  });

  it("rejects an account outside the known list", () => {
    const result = paymentFormSchema.safeParse({ ...validForm, account: "" });
    expect(result.success).toBe(false);
  });

  it("allows an empty reference", () => {
    const result = paymentFormSchema.safeParse({ ...validForm, reference: "" });
    expect(result.success).toBe(true);
  });

  it("rejects a reference over 140 characters", () => {
    const result = paymentFormSchema.safeParse({
      ...validForm,
      reference: "x".repeat(141),
    });
    expect(result.success).toBe(false);
  });
});

describe("paymentResponseSchema", () => {
  it("accepts a newly created payment before the database timestamp is refreshed", () => {
    const result = paymentResponseSchema.safeParse({
      id: 1,
      amount: 4999,
      toIban: "DE10535600287154123082",
      status: "COMPLETED",
      createdAt: null,
      currentStepNumber: null,
      remainingApprovals: 0,
    });

    expect(result.success).toBe(true);
  });
});

describe("createPaymentRequestSchema", () => {
  it("accepts a valid outgoing request", () => {
    const result = createPaymentRequestSchema.safeParse({
      fromAccountId: 1,
      toIban: "SE4550000000058398257466",
      amount: 1000,
      reference: "Faktura #1234",
    });

    expect(result.success).toBe(true);
  });

  it("rejects a non-positive amount", () => {
    const result = createPaymentRequestSchema.safeParse({
      fromAccountId: 1,
      toIban: "SE4550000000058398257466",
      amount: 0,
      reference: "",
    });

    expect(result.success).toBe(false);
  });
});
