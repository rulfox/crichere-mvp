import { spawnSync } from 'node:child_process'

export const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? 'backend-postgres-1'

/** Runs SQL in the local docker Postgres; returns rows as arrays of strings (psql -At, `|` separated). */
export function sql(statement) {
  const result = spawnSync(
    'docker',
    ['exec', '-i', DB_CONTAINER, 'psql', '-U', 'crichere', '-d', 'crichere_dev', '-v', 'ON_ERROR_STOP=1', '-At', '-F', '|'],
    { input: statement, encoding: 'utf8' },
  )
  if (result.status !== 0) throw new Error(`psql failed: ${result.stderr || result.error}`)
  return result.stdout
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.split('|'))
}

export const quote = (value) => `'${String(value).replaceAll("'", "''")}'`
