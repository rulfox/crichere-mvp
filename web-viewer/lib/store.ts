/**
 * App-store links (docs/PHASE11.md D8). The app isn't published yet, so both are normally unset --
 * an unset URL renders that badge as "Coming soon" (not a link), and the QR card and the mobile
 * "Open in app" banner stay hidden until the Play Store URL exists. Inlined at build time.
 */
export const PLAY_STORE_URL: string | null = process.env.NEXT_PUBLIC_PLAY_STORE_URL || null;
export const APP_STORE_URL: string | null = process.env.NEXT_PUBLIC_APP_STORE_URL || null;
