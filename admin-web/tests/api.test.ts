import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, onSessionEnded, qs } from "@/lib/api";
import { safeNext, tokenEmail } from "@/lib/signin";

afterEach(() => vi.restoreAllMocks());

describe("api", () => {
  it("sends credentials always, and the admin header only on changes", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response("{}", { status: 200 }));
    await api("GET", "/api/v1/admin/reports");
    await api("POST", "/api/v1/admin/reports/x/dismiss", { note: "n" });
    const getInit = fetchMock.mock.calls[0][1];
    const postInit = fetchMock.mock.calls[1][1];
    expect(getInit?.credentials).toBe("include");
    expect((getInit?.headers as Record<string, string>)["X-Crichere-Admin"]).toBeUndefined();
    expect((postInit?.headers as Record<string, string>)["X-Crichere-Admin"]).toBe("1");
    expect(postInit?.body).toBe('{"note":"n"}');
  });

  it("turns problems into ApiError and tells the shell when the session ends", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response('{"code":"ADMIN_SESSION_REQUIRED","detail":"gone"}', { status: 401 }));
    const ended = vi.fn();
    const off = onSessionEnded(ended);
    await expect(api("GET", "/api/v1/admin/reports")).rejects.toMatchObject({ status: 401, code: "ADMIN_SESSION_REQUIRED", sessionEnded: true });
    expect(ended).toHaveBeenCalledTimes(1);
    off();
  });

  it("reports an unreachable backend as status 0", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));
    const error = await api("GET", "/x").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).unreachable).toBe(true);
  });

  it("builds query strings from set values only", () => {
    expect(qs({ a: "x y", b: "", c: null, d: false, e: true, f: 0 })).toBe("?a=x%20y&e=true&f=0");
    expect(qs({})).toBe("");
  });
});

describe("sign-in helpers", () => {
  it("only follows same-site paths after sign-in", () => {
    expect(safeNext("/reports/view/?id=1")).toBe("/reports/view/?id=1");
    expect(safeNext("//evil.example.com")).toBe("/");
    expect(safeNext("https://evil.example.com")).toBe("/");
    expect(safeNext("/\\evil.example.com")).toBe("/");
    expect(safeNext("/\t/evil.example.com")).toBe("/");
    expect(safeNext("/\n/evil.example.com")).toBe("/");
    expect(safeNext("javascript:alert(1)")).toBe("/");
    expect(safeNext(null)).toBe("/");
  });

  it("reads the email from a Google token to name the refused account", () => {
    const payload = btoa(JSON.stringify({ email: "someone@example.com" })).replace(/=+$/, "");
    expect(tokenEmail(`h.${payload}.s`)).toBe("someone@example.com");
    expect(tokenEmail("junk")).toBeNull();
  });
});
