// After the organizer has created the league on the phone: claim 2 franchises and join the 10 seeded
// players through the real API (forged tokens). Usage: node join.mjs ["League name fragment"]
import { readFileSync } from 'node:fs'
import { api } from './lib/api.mjs'
import { sql, quote } from './lib/db.mjs'

const { people } = JSON.parse(readFileSync(new URL('./out/cast.json', import.meta.url), 'utf8'))
const fragment = process.argv[2]
const rows = sql(
  `SELECT id, name FROM leagues ${fragment ? `WHERE name ILIKE ${quote(`%${fragment}%`)}` : ''} ORDER BY created_at DESC LIMIT 1;`,
)
if (!rows.length) throw new Error('No league found -- create it in the app first')
const [leagueId, leagueName] = rows[0]
console.log(`League: ${leagueName} (${leagueId})`)

const check = (label, res, ok = [200, 201]) => {
  console.log(`${ok.includes(res.status) ? 'ok  ' : 'FAIL'} ${label} -> ${res.status}${ok.includes(res.status) ? '' : ' ' + JSON.stringify(res.body)}`)
  return res
}

check('owner1 claims franchise', await api('POST', `/leagues/${leagueId}/franchises`, { as: people.owner1.userId, body: { name: 'Pune Panthers' } }))
check('owner2 claims franchise', await api('POST', `/leagues/${leagueId}/franchises`, { as: people.owner2.userId, body: { name: 'Mumbai Mavericks' } }))
for (let i = 1; i <= 10; i++) {
  check(`player${i} joins`, await api('POST', `/leagues/${leagueId}/players`, { as: people[`player${i}`].userId, body: {} }))
}
