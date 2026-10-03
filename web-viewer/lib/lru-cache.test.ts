import { describe, expect, it } from "vitest";
import { LruCache } from "./lru-cache";

describe("LruCache", () => {
  it("returns what was stored until it expires", () => {
    let now = 0;
    const cache = new LruCache<string>(10, 1000, () => now);
    cache.set("a", "A");
    expect(cache.get("a")).toBe("A");
    now = 999;
    expect(cache.get("a")).toBe("A");
    now = 2000;
    expect(cache.get("a")).toBeUndefined();
    expect(cache.size).toBe(0);
  });

  it("evicts the least recently used entry when full", () => {
    const cache = new LruCache<number>(2, 60_000);
    cache.set("a", 1);
    cache.set("b", 2);
    cache.get("a"); // a is now the most recent
    cache.set("c", 3);
    expect(cache.get("b")).toBeUndefined();
    expect(cache.get("a")).toBe(1);
    expect(cache.get("c")).toBe(3);
    expect(cache.size).toBe(2);
  });

  it("overwrites an existing key without growing", () => {
    const cache = new LruCache<number>(2, 60_000);
    cache.set("a", 1);
    cache.set("a", 2);
    expect(cache.get("a")).toBe(2);
    expect(cache.size).toBe(1);
  });
});
