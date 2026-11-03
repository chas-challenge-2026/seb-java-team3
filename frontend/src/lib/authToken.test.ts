import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// authToken håller token i modulens minne. Varje test läser därför in modulen på nytt, så att tillståndet
// (och rensningen av en gammal localStorage-token som sker när modulen läses in) börjar om.
describe("authToken", () => {
  beforeEach(() => {
    vi.resetModules();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("starts without a token", async () => {
    const { getToken } = await import("./authToken");

    expect(getToken()).toBeNull();
  });

  it("keeps the token in memory and never writes it to localStorage", async () => {
    const { getToken, setToken } = await import("./authToken");

    setToken("secret-token");

    expect(getToken()).toBe("secret-token");
    expect(localStorage.length).toBe(0);
  });

  it("forgets the token when it is cleared", async () => {
    const { clearToken, getToken, setToken } = await import("./authToken");
    setToken("secret-token");

    clearToken();

    expect(getToken()).toBeNull();
  });

  it("removes a token an earlier version left in localStorage when the module loads", async () => {
    localStorage.setItem("authToken", "old-token-from-an-earlier-version");

    const { getToken } = await import("./authToken");

    expect(localStorage.getItem("authToken")).toBeNull();
    // Den gamla token används inte heller: den ska aldrig bli en inloggning
    expect(getToken()).toBeNull();
  });

  it("does not fail when localStorage is blocked", async () => {
    vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new Error("blocked");
    });

    await expect(import("./authToken")).resolves.toBeDefined();
  });
});
