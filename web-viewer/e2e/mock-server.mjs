/**
 * Stand-in for the real Spring Boot backend, used only by the e2e suite (see playwright.config.ts).
 * Serves the three public endpoints `lib/api.ts` calls, shaped exactly like the real DTOs
 * (docs/PHASE6.md), from the fixtures in `fixtures.mjs`. This is what lets the whole suite run
 * without a live backend or Postgres -- fast and deterministic instead of depending on
 * whatever state a real auction happens to be in.
 */
import { createServer } from "node:http";
import { auctionStates, leagues, results } from "./fixtures.mjs";

const PORT = 4310;

function send(res, status, body) {
  res.writeHead(status, { "content-type": "application/json", "access-control-allow-origin": "*" });
  res.end(JSON.stringify(body));
}

const server = createServer((req, res) => {
  // The SSE stream is fetched from the browser (EventSource, cross-origin 3000 -> 4310), unlike
  // the plain JSON endpoints which Next's server components fetch from Node -- mirrors why the
  // real backend needs CORS at all (docs/PHASE6.md, WebViewerProperties/SecurityConfig).
  const url = new URL(req.url ?? "/", `http://localhost:${PORT}`);

  // GET /api/v1/auctions/live-now (docs/PHASE11.md D5) -- the live fixture, or 204 when
  // MOCK_NO_LIVE is set so the "nothing live" landing page can be exercised too.
  if (url.pathname === "/api/v1/auctions/live-now") {
    if (process.env.MOCK_NO_LIVE) {
      res.writeHead(204).end();
      return;
    }
    return send(res, 200, { leagueId: "league-live", leagueName: leagues["league-live"].name });
  }
  const match = url.pathname.match(/^\/api\/v1\/leagues\/([^/]+)(\/auction\/(results|stream))?$/);

  if (!match) {
    send(res, 404, { message: "not found" });
    return;
  }

  const [, id, , suffix] = match;

  if (suffix === "results") {
    const fixture = results[id];
    if (!fixture) return send(res, 404, { message: "not found" });
    return send(res, 200, fixture);
  }

  if (suffix === "stream") {
    const state = auctionStates[id];
    if (!state) {
      res.writeHead(404).end();
      return;
    }
    // A single event, then the connection stays open (matching the real SSE endpoint's shape)
    // until the client (EventSource) disconnects -- Playwright's page teardown closes it.
    res.writeHead(200, {
      "content-type": "text/event-stream",
      "cache-control": "no-cache",
      connection: "keep-alive",
      "access-control-allow-origin": "*",
    });
    res.write(`event: auction-state\ndata: ${JSON.stringify(state)}\n\n`);
    req.on("close", () => res.end());
    return;
  }

  const league = leagues[id];
  if (!league) return send(res, 404, { message: "not found" });
  return send(res, 200, league);
});

server.listen(PORT, () => {
  console.log(`mock backend listening on http://localhost:${PORT}`);
});
