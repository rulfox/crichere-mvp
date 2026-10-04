// One auction action from the command line, to put the app into a given state while checking screens.
// Usage: node act.mjs "<league name fragment>" <action> [arg]
//   state                     print the live auction state
//   start | next | sold | unsold | undo | end
//   exceed on|off             Allow exceeding purse
//   bid <franchise name fragment> [amount]   bid as that franchise's owner (default: the minimum)
// The organizer is the league's organizer; bids use the owner's forged token (local profile only).
import { api, auctionStates } from './lib/api.mjs'
import { sql, quote } from './lib/db.mjs'

const [fragment, action, arg, amountArg] = process.argv.slice(2)
const [[leagueId]] = sql(`SELECT id FROM leagues WHERE name ILIKE ${quote(`%${fragment}%`)} ORDER BY created_at DESC LIMIT 1;`)
const league = (await api('GET', `/leagues/${leagueId}`)).body
const organizer = league.organizerUserId
const path = (p) => `/leagues/${leagueId}/auction${p}`
/** There is no GET for the live state: the stream's first event is it. */
async function currentState() {
  // Not aborted: aborting a streaming fetch crashes Node 24 on Windows; the process exits after printing.
  for await (const state of auctionStates(leagueId)) return state
}

let res
switch (action) {
  case 'state':
    res = { status: 200, body: await currentState() }
    break
  case 'start': case 'sold': case 'unsold': case 'undo': case 'end':
    res = await api('POST', path(`/${action}`), { as: organizer })
    break
  case 'next':
    res = await api('POST', path('/next-player'), { as: organizer })
    break
  case 'exceed':
    res = await api('POST', path('/toggle-exceed-purse'), { as: organizer, body: { allow: arg === 'on' } })
    break
  case 'bid': {
    const franchise = league.franchises.find((f) => f.name.toLowerCase().includes(arg.toLowerCase()))
    const state = await currentState()
    const amount = amountArg ? Number(amountArg)
      : state.currentBidAmount != null ? Number(state.currentBidAmount) + Number(league.auctionBidIncrement) : Number(league.auctionBasePrice)
    res = await api('POST', path('/bids'), { as: franchise.ownerUserId, body: { franchiseId: franchise.id, amount } })
    break
  }
  default:
    throw new Error(`unknown action ${action}`)
}
const b = res.body ?? {}
console.log(res.status, JSON.stringify({
  status: b.auctionStatus, player: b.currentPlayerName, bid: b.currentBidAmount, leader: b.currentLeadingFranchiseName,
  sold: b.playersSold, pending: b.playersPending, canAnyoneBid: b.canAnyoneBid, full: b.squadsFull, purse: b.purseBelowBase,
  total: b.franchisesTotal, exceed: b.allowExceedPurse, code: b.code,
}))
process.exit(0)
