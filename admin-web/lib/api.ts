import type { ApiProblem } from "./types";

/*
 * The only way this site talks to the backend. The session is an httpOnly cookie the backend sets
 * on its own host, so every call sends credentials; changes also send the admin-site header the
 * backend's guard requires (docs/PHASE20.md section 5). Nothing here stores a token.
 */

export const API_BASE = (process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080").replace(/\/$/, "");

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
  ) {
    super(detail);
  }

  /** Session expired, revoked, or never there: the shell shows "Your session ended". */
  get sessionEnded(): boolean {
    return this.status === 401;
  }

  /** The backend didn't answer at all (ADM-503 on the board). */
  get unreachable(): boolean {
    return this.status === 0;
  }
}

type Listener = (error: ApiError) => void;
const sessionListeners = new Set<Listener>();

/** The shell subscribes so any 401 anywhere opens the session-ended dialog once. */
export function onSessionEnded(listener: Listener): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

export type Method = "GET" | "POST" | "PUT" | "DELETE";

export async function api<T>(method: Method, path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method !== "GET") headers["X-Crichere-Admin"] = "1";
  if (body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "include",
      cache: "no-store",
      signal,
    });
  } catch (cause) {
    if ((cause as Error)?.name === "AbortError") throw cause;
    throw new ApiError(0, "UNREACHABLE", "The backend didn't answer.");
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  const json = text ? safeJson(text) : null;
  if (!response.ok) {
    const problem = (json ?? {}) as Partial<ApiProblem>;
    const error = new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail ?? response.statusText);
    if (error.sessionEnded && !path.startsWith("/api/v1/admin/session")) sessionListeners.forEach((l) => l(error));
    throw error;
  }
  return json as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** Query string from defined, non-empty values only. */
export function qs(params: Record<string, string | number | boolean | null | undefined>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "" && v !== false);
  if (entries.length === 0) return "";
  return "?" + entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join("&");
}

export const get = <T>(path: string, signal?: AbortSignal) => api<T>("GET", path, undefined, signal);
export const post = <T>(path: string, body?: unknown) => api<T>("POST", path, body);
export const put = <T>(path: string, body?: unknown) => api<T>("PUT", path, body);
export const del = <T>(path: string) => api<T>("DELETE", path);
