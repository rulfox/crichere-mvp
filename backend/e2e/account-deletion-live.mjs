// Live check of the account deletion API (docs/PHASE16.md) against the local backend: fake OTP 123456,
// +919000000097..99 allowlisted, and the purge cron set to every minute
// (CRICHERE_ACCOUNTDELETION_PURGECRON="0 * * * * *"). Run: node backend/e2e/account-deletion-live.mjs
import { sql, quote } from './lib/db.mjs'

const BASE = 'http://localhost:8080/api/v1'
let failures = 0
const check = (label, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${extra ? '  ' + extra : ''}`)
  if (!ok) failures++
}
async function call(method, path, token, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let json = null
  try { json = text ? JSON.parse(text) : null } catch {}
  return { status: res.status, body: json }
}
async function signIn(phone) {
  const send = await call('POST', '/auth/otp/send', null, { phoneNumber: phone })
  if (send.status !== 200) throw new Error(`send ${phone}: ${send.status} ${JSON.stringify(send.body)}`)
  const v = await call('POST', '/auth/otp/verify', null, { challengeId: send.body.challengeId, code: '123456' })
  if (v.status !== 200) throw new Error(`verify ${phone}: ${v.status} ${JSON.stringify(v.body)}`)
  return v.body
}
const one = (q) => sql(q)[0]?.[0]
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const ORG = '+919000000097', CO = '+919000000098', PLAYER = '+919000000099'
const org = await signIn(ORG)
const co = await signIn(CO)
const player = await signIn(PLAYER)
for (const [s, n] of [[org, 'Live Organizer'], [co, 'Live CoOrganizer']]) {
  const r = await call('PUT', '/profiles/me', s.accessToken, { name: n })
  check(`profile saved for ${n}`, r.status === 200, `status ${r.status} ${r.status !== 200 ? JSON.stringify(r.body) : ''}`)
}

const groundId = one(`SELECT id FROM grounds WHERE name = 'E2E Test Ground' LIMIT 1`)
const league = await call('POST', '/leagues', org.accessToken, {
  name: `Live Handover ${Date.now() % 100000}`, state: 'Kerala', district: 'Kottayam', groundId, startsOn: '2026-12-01',
})
check('organizer creates league', league.status === 200 || league.status === 201, `status ${league.status}`)
const leagueId = league.body.id

// No co-organizer yet: blocked with logo field present.
let st = await call('GET', '/me/deletion', org.accessToken)
check('blocked NO_CO_ORGANIZER before a co-organizer', st.body.blockers?.[0]?.reason === 'NO_CO_ORGANIZER' && 'leagueLogoUrl' in st.body.blockers[0])
check('GET has erasesOn ~14 days out', Math.abs(new Date(st.body.erasesOn) - Date.now() - 14 * 864e5) < 6e4, st.body.erasesOn)

await call('POST', `/leagues/${leagueId}/roles`, org.accessToken, { userId: co.userId })
st = await call('GET', '/me/deletion', org.accessToken)
check('no blockers once co-organizer added', st.body.blockers.length === 0)
check('handover lists the co-organizer by name', st.body.handovers?.[0]?.newOrganizerName === 'Live CoOrganizer' && st.body.handovers[0].leagueId === leagueId, JSON.stringify(st.body.handovers))

// Player joins (open league -> withdrawn at purge, so use a completed-auction league for history instead).
const histLeague = await call('POST', '/leagues', co.accessToken, { name: `Live History ${Date.now() % 100000}`, state: 'Kerala', district: 'Kottayam', groundId, startsOn: '2026-12-01' })
await call('POST', `/leagues/${histLeague.body.id}/players`, org.accessToken, {})
sql(`UPDATE leagues SET auction_status = 'COMPLETED' WHERE id = ${quote(histLeague.body.id)}`)

// Request, then backdate so the per-minute test cron purges it.
const req = await call('POST', '/me/deletion', org.accessToken, { confirm: 'DELETE' })
check('request accepted 202', req.status === 202, JSON.stringify(req.body))
const pending = await call('GET', '/me/deletion', org.accessToken)
check('GET while pending keeps the original erase date', pending.status === 200 && pending.body.erasesOn?.slice(0, 16) === req.body.purgeAfter?.slice(0, 16), `${pending.body?.erasesOn} vs ${req.body.purgeAfter}`)
sql(`UPDATE users SET deletion_requested_at = now() - interval '15 days' WHERE id = ${quote(org.userId)}`)
let purged = false
for (let i = 0; i < 15 && !purged; i++) {
  await sleep(10_000)
  purged = one(`SELECT deleted_at IS NOT NULL FROM users WHERE id = ${quote(org.userId)}`) === 't'
}
check('purge ran (per-minute test cron)', purged)

let l = await call('GET', `/leagues/${leagueId}`, co.accessToken)
check('co-organizer is now organizer', l.body.organizerUserId === co.userId)
check('showHandoverNotice true for new organizer', l.body.showHandoverNotice === true)
const anon = await call('GET', `/leagues/${leagueId}`)
check('showHandoverNotice false for anonymous', anon.body.showHandoverNotice === false)
const other = await call('POST', `/leagues/${leagueId}/handover-notice/dismiss`, player.accessToken)
check('dismiss by non-organizer is 403', other.status === 403, `status ${other.status}`)
const d1 = await call('POST', `/leagues/${leagueId}/handover-notice/dismiss`, co.accessToken)
const d2 = await call('POST', `/leagues/${leagueId}/handover-notice/dismiss`, co.accessToken)
check('dismiss 204, idempotent', d1.status === 204 && d2.status === 204, `${d1.status}/${d2.status}`)
l = await call('GET', `/leagues/${leagueId}`, co.accessToken)
check('notice stays dismissed', l.body.showHandoverNotice === false)

const h = await call('GET', `/leagues/${histLeague.body.id}`, co.accessToken)
const row = h.body.players.find((p) => p.userId === org.userId)
check('history row shows Deleted user with deleted=true', row?.name === 'Deleted user' && row?.deleted === true, JSON.stringify(row))
check('profile flag set in DB', one(`SELECT account_deleted FROM profiles WHERE user_id = ${quote(org.userId)}`) === 't')

// Restore path on the player: request, sign in again -> accountRestored.
await call('POST', '/me/deletion', player.accessToken, { confirm: 'DELETE' })
const back = await signIn(PLAYER)
check('sign-in after request returns accountRestored', back.accountRestored === true)

console.log(`\n${failures === 0 ? 'ALL PASS' : failures + ' FAILURE(S)'}  org=${org.userId} league=${leagueId}`)
process.exit(failures ? 1 : 0)
