// Plays the two franchise owners during the live auction the organizer runs on the phone. Watches the
// public SSE stream; whenever the organizer opens a player ("Next player") it places escalating bids
// alternately for each franchise. Every 4th lot nobody bids, so the organizer can test Unsold.
// Usage: node bidder.mjs [league name fragment]   (stops when the auction completes)
// Env: BID_PAUSE_MS (default 1500), SKIP_EVERY (default 4, 0 = bid on everything)
import { api, auctionStates } from './lib/api.mjs'
import { sql, quote } from './lib/db.mjs'

const fragment = process.argv[2]
const [[leagueId, leagueName]] = sql(
  `SELECT id, name FROM leagues ${fragment ? `WHERE name ILIKE ${quote(`%${fragment}%`)}` : ''} ORDER BY created_at DESC LIMIT 1;`,
)
const pause = Number(process.env.BID_PAUSE_MS ?? 1500)
const skipEvery = Number(process.env.SKIP_EVERY ?? 4)
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
console.log(`Bidding in "${leagueName}" (${leagueId}); waiting for the auction...`)

const league = (await api('GET', `/leagues/${leagueId}`)).body
const owners = league.franchises.map((f) => ({ franchiseId: f.id, userId: f.ownerUserId, name: f.name }))
if (owners.length < 2) throw new Error('Run join.mjs first: need 2 franchises')

let lastLot = null
const blocked = new Set() // franchises that can no longer bid (squad full / purse spent)
for await (const state of auctionStates(leagueId)) {
  if (state.auctionStatus === 'COMPLETED') {
    console.log('Auction completed.')
    break
  }
  if (state.auctionStatus !== 'IN_PROGRESS' || !state.currentPlayerId || state.currentLotNumber === lastLot) continue
  if (state.currentLeadingFranchiseId) continue // a bid already exists (undo / reopened lot) -- leave it to the organizer
  lastLot = state.currentLotNumber
  if (skipEvery > 0 && state.currentLotNumber % skipEvery === 0) {
    console.log(`Lot ${state.currentLotNumber} ${state.currentPlayerName}: skipping (for Unsold)`)
    continue
  }
  const fresh = (await api('GET', `/leagues/${leagueId}`)).body
  const base = Number(fresh.auctionBasePrice)
  const increment = Number(fresh.auctionBidIncrement)
  const rounds = 2 + Math.floor(Math.random() * 4)
  let amount = base
  let turn = 0
  for (let i = 0; i < rounds && blocked.size < owners.length; i++) {
    let owner
    do owner = owners[turn++ % owners.length]
    while (blocked.has(owner.franchiseId))
    await sleep(pause)
    const res = await api('POST', `/leagues/${leagueId}/auction/bids`, { as: owner.userId, body: { franchiseId: owner.franchiseId, amount } })
    console.log(`Lot ${state.currentLotNumber} ${state.currentPlayerName}: ${owner.name} bids ${amount} -> ${res.status}${res.status === 200 ? '' : ' ' + res.body?.code}`)
    if (res.status === 200) {
      amount += increment
    } else if (res.body?.code === 'SQUAD_FULL') {
      blocked.add(owner.franchiseId) // full for good -- the other franchise carries on
    } else if (res.body?.code === 'PURSE_EXCEEDED') {
      blocked.add(owner.franchiseId)
    } else {
      break // lot closed by the organizer, or something unexpected
    }
  }
}
