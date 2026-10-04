// Re-implements the backend's PhoneCryptoService and JwtService with node:crypto so the e2e scripts can
// seed users (phone hash + encrypted phone) and mint access tokens without any backend code change.
// Secrets are the committed `local`-profile fallbacks in application.yml -- NOT SECRET, dev machine only.
import { createCipheriv, createHmac, randomBytes, randomUUID } from 'node:crypto'

const JWT_SECRET = process.env.JWT_SECRET ?? 'bG9jYWwtZGV2LWp3dC1zZWNyZXQtbm90LWZvci1kZXBsb3ltZW50IQ=='
const PHONE_SECRET =
  process.env.PHONE_CRYPTO_SECRET ?? 'bG9jYWwtZGV2LXBob25lLWNyeXB0by1zZWNyZXQtbm90LXJlYWwhIQ=='

// Same rule as the backend: Base64 if it decodes to >= 32 bytes, otherwise the raw text.
function keyBytes(secret) {
  const decoded = Buffer.from(secret, 'base64')
  return decoded.length >= 32 && decoded.toString('base64') === secret ? decoded : Buffer.from(secret, 'utf8')
}

const hmac = (key, data) => createHmac('sha256', key).update(data).digest()
const phoneMaster = keyBytes(PHONE_SECRET)
const lookupKey = hmac(phoneMaster, 'crichere/phone-lookup-hmac/v1')
const encryptionKey = hmac(phoneMaster, 'crichere/phone-encryption/v1')

const normalise = (phone) => [...phone].filter((c) => /\d/.test(c) || c === '+').join('')

export const phoneLookupHash = (phone) => hmac(lookupKey, normalise(phone)).toString('hex')

export function encryptPhone(phone) {
  const iv = randomBytes(12)
  const cipher = createCipheriv('aes-256-gcm', encryptionKey, iv)
  const sealed = Buffer.concat([cipher.update(normalise(phone), 'utf8'), cipher.final()])
  return Buffer.concat([iv, sealed, cipher.getAuthTag()]).toString('base64')
}

const b64url = (buf) => Buffer.from(buf).toString('base64url')

export function mintAccessToken(userId, ttlSeconds = 3600) {
  const now = Math.floor(Date.now() / 1000)
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const claims = b64url(
    JSON.stringify({
      iss: 'crichere',
      sub: userId,
      iat: now,
      exp: now + ttlSeconds,
      jti: randomUUID(),
      token_use: 'access',
    }),
  )
  const signature = b64url(hmac(keyBytes(JWT_SECRET), `${header}.${claims}`))
  return `${header}.${claims}.${signature}`
}
