// Seeds the local DB for the mobile e2e run (docs/PHASE15.md): complete user profiles + one ground.
// Idempotent. The organizer is the Phase-15 test login (+917293318484, OTP 123456 via LocalFakeOtpSender).
// Writes e2e/out/cast.json for join.mjs / bidder.mjs.
import { mkdirSync, writeFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { encryptPhone, phoneLookupHash } from './lib/crypto.mjs'
import { quote, sql } from './lib/db.mjs'

const ORGANIZER_PHONE = '+917293318484'

// role, batting, bowling (bowling only for BOWLER / ALL_ROUNDER -- ProfileCompletionService)
const cast = [
  { key: 'organizer', name: 'Rohan Organizer', phone: ORGANIZER_PHONE, role: 'BATSMAN', bat: 'RIGHT_HAND' },
  { key: 'coOrganizer', name: 'Meera Coorg', phone: '+919000000001', role: 'WICKETKEEPER', bat: 'LEFT_HAND' },
  { key: 'owner1', name: 'Vikram Owner One', phone: '+919000000011', role: 'BATSMAN', bat: 'RIGHT_HAND' },
  { key: 'owner2', name: 'Anita Owner Two', phone: '+919000000012', role: 'ALL_ROUNDER', bat: 'LEFT_HAND', bowl: 'LEFT_ARM_MEDIUM' },
  { key: 'outsider', name: 'Sameer Outsider', phone: '+919000000099', role: 'BOWLER', bat: 'RIGHT_HAND', bowl: 'RIGHT_ARM_FAST' },
]
const playerSpecs = [
  ['Arjun Patil', 'BATSMAN', 'RIGHT_HAND'],
  ['Kabir Shaikh', 'BOWLER', 'RIGHT_HAND', 'RIGHT_ARM_FAST'],
  ['Dev Kulkarni', 'ALL_ROUNDER', 'LEFT_HAND', 'LEFT_ARM_ORTHODOX'],
  ['Imran Khan', 'WICKETKEEPER', 'RIGHT_HAND'],
  ['Siddharth Joshi', 'BOWLER', 'LEFT_HAND', 'LEFT_ARM_FAST'],
  ['Rahul More', 'BATSMAN', 'LEFT_HAND'],
  ['Nikhil Deshmukh', 'ALL_ROUNDER', 'RIGHT_HAND', 'RIGHT_ARM_OFFBREAK'],
  ['Farhan Ansari', 'BOWLER', 'RIGHT_HAND', 'RIGHT_ARM_LEGBREAK'],
  ['Omkar Pawar', 'BATSMAN', 'RIGHT_HAND'],
  ['Yash Gaikwad', 'ALL_ROUNDER', 'RIGHT_HAND', 'RIGHT_ARM_MEDIUM'],
]
playerSpecs.forEach(([name, role, bat, bowl], i) =>
  cast.push({ key: `player${i + 1}`, name, phone: `+9190000001${String(i).padStart(2, '0')}`, role, bat, bowl }),
)

const result = {}
for (const person of cast) {
  const [[userId]] = sql(`
    INSERT INTO users (id, phone_lookup_hash, phone_encrypted)
    VALUES (${quote(randomUUID())}, ${quote(phoneLookupHash(person.phone))}, ${quote(encryptPhone(person.phone))})
    ON CONFLICT (phone_lookup_hash) DO UPDATE SET phone_lookup_hash = EXCLUDED.phone_lookup_hash
    RETURNING id;`)
  // A placeholder https photo: the profile is only "complete" with one, and S3 uploads don't exist locally.
  sql(`
    INSERT INTO profiles (user_id, name, photo_url, country, state, district, playing_role, batting_style, bowling_style)
    VALUES (${quote(userId)}, ${quote(person.name)}, ${quote(`https://example.com/e2e/${person.key}.jpg`)}, 'IN',
            'Maharashtra', 'Pune', ${quote(person.role)}, ${quote(person.bat)}, ${person.bowl ? quote(person.bowl) : 'NULL'})
    ON CONFLICT (user_id) DO UPDATE SET name = EXCLUDED.name, photo_url = EXCLUDED.photo_url, state = EXCLUDED.state,
      district = EXCLUDED.district, playing_role = EXCLUDED.playing_role,
      batting_style = EXCLUDED.batting_style, bowling_style = EXCLUDED.bowling_style;`)
  result[person.key] = { userId, name: person.name, phone: person.phone }
}

const groundName = 'E2E Test Ground'
sql(`
  INSERT INTO grounds (name, state, district, latitude, longitude, registered_by_user_id)
  SELECT ${quote(groundName)}, 'Maharashtra', 'Pune', 18.5204, 73.8567, ${quote(result.organizer.userId)}
  WHERE NOT EXISTS (SELECT 1 FROM grounds WHERE name = ${quote(groundName)});`)

mkdirSync(new URL('./out/', import.meta.url), { recursive: true })
writeFileSync(new URL('./out/cast.json', import.meta.url), JSON.stringify({ groundName, people: result }, null, 2))
console.log(`Seeded ${cast.length} users (organizer ${ORGANIZER_PHONE}) and ground "${groundName}". Cast: e2e/out/cast.json`)
