import { describe, expect, it } from "vitest";
import { formatDateTime, getDateTimeSortValue } from "./dateTime";

describe("formatDateTime", () => {
  it("formats backend LocalDateTime values as Swedish time", () => {
    expect(formatDateTime("2026-09-21T11:05:44.123456")).toBe(
      "2026-09-21 13:05",
    );
  });

  it("formats zoned timestamps in Swedish time", () => {
    expect(formatDateTime("2026-09-21T11:05:44Z")).toBe("2026-09-21 13:05");
  });

  it("uses the existing empty label for missing timestamps", () => {
    expect(formatDateTime(null)).toBe("Ej utförd");
  });
});

describe("getDateTimeSortValue", () => {
  it("sorts LocalDateTime values by their visible clock time", () => {
    expect(getDateTimeSortValue("2026-09-21T13:06:00")).toBeGreaterThan(
      getDateTimeSortValue("2026-09-21T13:05:00") ?? 0,
    );
  });
});
