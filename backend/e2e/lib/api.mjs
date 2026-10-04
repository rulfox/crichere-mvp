import { mintAccessToken } from './crypto.mjs'

export const BASE = process.env.E2E_API ?? 'http://localhost:8080/api/v1'

/** Calls the API as `userId` (forged token) or anonymously; returns { status, body }. */
export async function api(method, path, { as, body } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(as ? { Authorization: `Bearer ${mintAccessToken(as)}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let parsed = text
  try {
    parsed = text ? JSON.parse(text) : null
  } catch {}
  return { status: res.status, body: parsed }
}

/** Yields `auction-state` payloads from the public SSE stream until the caller stops iterating. */
export async function* auctionStates(leagueId, signal) {
  const res = await fetch(`${BASE}/leagues/${leagueId}/auction/stream`, { signal })
  const decoder = new TextDecoder()
  let buffer = ''
  for await (const chunk of res.body) {
    buffer += decoder.decode(chunk, { stream: true })
    let end
    while ((end = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, end)
      buffer = buffer.slice(end + 2)
      const event = frame.split('\n').find((l) => l.startsWith('event:'))?.slice(6).trim()
      const data = frame.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trim()).join('')
      if (event === 'auction-state' && data) yield JSON.parse(data)
    }
  }
}
