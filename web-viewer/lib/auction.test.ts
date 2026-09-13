import { describe, expect, it } from "vitest";
import { formatInr, viewStateFor } from "./auction";

describe("formatInr", () => {
  it("formats with Indian digit grouping, not US grouping", () => {
    expect(formatInr(100000)).toBe("₹1,00,000");
  });

  it("accepts a numeric string, matching what the backend serializes BigDecimal as", () => {
    expect(formatInr("900")).toBe("₹900");
  });

  it("drops fractional paise for a whole-rupee display", () => {
    expect(formatInr("150.00")).toBe("₹150");
  });
});

describe("viewStateFor", () => {
  it("maps each backend auctionStatus to its view state", () => {
    expect(viewStateFor("NOT_STARTED")).toBe("not-started");
    expect(viewStateFor("IN_PROGRESS")).toBe("live");
    expect(viewStateFor("COMPLETED")).toBe("completed");
  });
});
