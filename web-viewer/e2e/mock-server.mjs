/**
 * Stand-in for the real Spring Boot backend, used only by the e2e suite (see playwright.config.ts).
 * Serves the three public endpoints `lib/api.ts` calls, shaped exactly like the real DTOs
 * (docs/PHASE6.md), from the fixtures in `fixtures.mjs`. This is what lets the whole suite run
 * without a live backend or Postgres -- fast and deterministic instead of depending on
 * whatever state a real auction happens to be in.
 */
import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import { auctionStreams, leagues, results } from "./fixtures.mjs";

const PORT = 4310;

/** When league-flaky's stream last dropped its connection -- reconnects within 6s of that are refused (see the stream handler). */
let flakyDroppedAt = 0;

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
  // League logos for the share-card fixtures: a real PNG, and an SVG the card must refuse (-> monogram).
  if (url.pathname === "/mock-assets/logo.png") {
    res.writeHead(200, { "content-type": "image/png" });
    res.end(readFileSync(new URL("../public/crichere-icon.png", import.meta.url)));
    return;
  }
  if (url.pathname === "/mock-assets/logo.svg") {
    res.writeHead(200, { "content-type": "image/svg+xml" });
    res.end('<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"/>');
    return;
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
    const events = auctionStreams[id];
    if (!events) {
      res.writeHead(404).end();
      return;
    }
    // league-flaky: every connection drops right after its first event, and reconnects within
    // 6s of a drop are refused -- the browser's EventSource gives up (CLOSED), the page shows its
    // reconnecting state, and its own backoff retry gets through once the window has passed.
    if (id === "league-flaky" && Date.now() - flakyDroppedAt < 6000) {
      res.writeHead(503, { "access-control-allow-origin": "*" }).end();
      return;
    }
    res.writeHead(200, {
      "content-type": "text/event-stream",
      "cache-control": "no-cache",
      connection: "keep-alive",
      "access-control-allow-origin": "*",
    });
    // The scripted events in order, then the connection stays open (matching the real SSE
    // endpoint's shape) until the client disconnects -- Playwright's page teardown closes it.
    const timers = events.map(({ afterMs, state }, index) =>
      setTimeout(() => {
        // Bid times are re-stamped relative to now, so the ticker reads "just now", "7s ago"...
        const fresh = { ...state, recentBids: state.recentBids.map((bid, i) => ({ ...bid, placedAt: new Date(Date.now() - i * 7000).toISOString() })) };
        res.write(`event: auction-state
data: ${JSON.stringify(fresh)}

`);
        if (id === "league-flaky" && index === events.length - 1) {
          setTimeout(() => {
            flakyDroppedAt = Date.now();
            res.end();
          }, 300);
        }
      }, afterMs),
    );
    req.on("close", () => {
      timers.forEach(clearTimeout);
      res.end();
    });
    return;
  }

  const league = leagues[id];
  if (!league) return send(res, 404, { message: "not found" });
  return send(res, 200, league);
});

server.listen(PORT, () => {
  console.log(`mock backend listening on http://localhost:${PORT}`);
});
