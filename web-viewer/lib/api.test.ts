import { afterEach, describe, expect, it, vi } from "vitest";

/** `serverFetch` reads its env at import time, so each case sets env, then imports fresh. */
async function load(env: Record<string, string | undefined>) {
  vi.resetModules();
  for (const [key, value] of Object.entries(env)) vi.stubEnv(key, value as string);
  return import("./api");
}

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe("serverFetch", () => {
  it("uses the private network when configured", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("ok"));
    vi.stubGlobal("fetch", fetchMock);
    const { serverFetch } = await load({ NEXT_PUBLIC_API_BASE_URL: "https://api.example", API_INTERNAL_BASE_URL: "http://backend.internal:8080" });

    await serverFetch("/api/v1/x");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("http://backend.internal:8080/api/v1/x");
  });

  it("falls back to the public URL when the private call can't connect", async () => {
    const fetchMock = vi.fn().mockRejectedValueOnce(new TypeError("fetch failed")).mockResolvedValue(new Response("ok"));
    vi.stubGlobal("fetch", fetchMock);
    const { serverFetch } = await load({ NEXT_PUBLIC_API_BASE_URL: "https://api.example", API_INTERNAL_BASE_URL: "http://backend.internal:8080" });

    const response = await serverFetch("/api/v1/x");

    expect(await response.text()).toBe("ok");
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual(["http://backend.internal:8080/api/v1/x", "https://api.example/api/v1/x"]);
  });

  it("does not retry an HTTP error from the private network", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("nope", { status: 500 }));
    vi.stubGlobal("fetch", fetchMock);
    const { serverFetch } = await load({ NEXT_PUBLIC_API_BASE_URL: "https://api.example", API_INTERNAL_BASE_URL: "http://backend.internal:8080" });

    const response = await serverFetch("/api/v1/x");

    expect(response.status).toBe(500);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("uses the public URL when no private one is set", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("ok"));
    vi.stubGlobal("fetch", fetchMock);
    const { serverFetch } = await load({ NEXT_PUBLIC_API_BASE_URL: "https://api.example", API_INTERNAL_BASE_URL: "" });

    await serverFetch("/api/v1/x");

    expect(fetchMock.mock.calls[0][0]).toBe("https://api.example/api/v1/x");
  });
});
