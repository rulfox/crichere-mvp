// Shapes of the admin API (backend/src/main/kotlin/com/crichere/backend/admin). Instants are ISO strings.

export type Session = { email: string; expiresAt: string; liveSessions?: number | null };

export type Page<T> = { items: T[]; nextCursor: string | null; total?: { count: number; capped: boolean } | null };

export type FeedItem = { type: string; district: string | null; at: string };

export type LiveSnapshot = {
  day: string;
  today: { signUps: number; leagues: number; joins: number; reports: number; otpSends: number; bids: number; activeUsers: number };
  now: { liveAuctions: number; bidsLastMinute: number; auctionViewers: number };
  feed: FeedItem[];
};

export type ModerationSummary = {
  openReports: number;
  oldestOpenAt: string | null;
  openByReason: Record<string, number>;
  suspendedAccounts: number;
  imagesRemoved30d: number;
};

export type Today = { live: LiveSnapshot; moderation: ModerationSummary };

export type MetricRow = { day: string; metric: string; dimension: string; value: number };

export type ReleaseMarker = { id: string; day: string; label: string; createdBy: string };

export type History = { from: string; to: string; rows: MetricRow[]; markers: ReleaseMarker[] };

export type Health = {
  checkedAt: string;
  nightly: { status: string | null; startedAt: string | null; finishedAt: string | null; detail: string | null };
  otp: { codesSentToday: number; expiredUnusedToday: number; failureRate: number | null };
  sms: { sentToday: number; dailyCap: number; resetsAt: string };
  msg91Balance: number | null;
  ses: { configured: boolean; deliveryAttempts14d: number | null; bounces14d: number | null; complaints14d: number | null };
};

export type ReportListItem = {
  id: string;
  reason: string;
  status: string;
  targetType: "USER" | "LEAGUE";
  targetId: string | null;
  targetName: string | null;
  detailsPreview: string | null;
  createdAt: string;
  openOnTarget: number;
};

export type AuditEntry = {
  id: string;
  at: string;
  action: string;
  adminEmail: string | null;
  reportId: string | null;
  userId: string | null;
  leagueId: string | null;
  note: string | null;
};

export type ReportDetail = {
  id: string;
  reason: string;
  status: string;
  createdAt: string;
  resolvedAt: string | null;
  details: string | null;
  contextLeagueId: string | null;
  evidence: Record<string, unknown>;
  target: {
    kind: "USER" | "LEAGUE";
    id: string | null;
    name: string | null;
    district: string | null;
    joinedAt: string | null;
    suspended: boolean;
    deleted: boolean;
    openReports: number;
    pastActions: number;
    organizerId: string | null;
    organizerName: string | null;
  };
  reporter: { id: string; name: string | null; district: string | null; reportsFiled: number } | null;
  history: AuditEntry[];
};

export type SuspendedOverview = {
  accounts: { userId: string; name: string | null; district: string | null; suspendedAt: string; by: string | null; note: string | null; reportId: string | null }[];
  blockedNumbers: { hash: string; since: string }[];
};

export type LeagueRow = {
  id: string;
  name: string;
  logoUrl: string | null;
  organizerId: string;
  organizerName: string;
  state: string;
  district: string;
  startsOn: string;
  status: LeagueStatus;
  franchises: number;
  franchisesRequired: number | null;
  players: number;
  playersRequired: number | null;
  openReports: number;
  createdAt: string;
  internal: boolean;
};

export type LeagueStatus = "open" | "auction_live" | "auction_done" | "completed";

export type UserStatus = "active" | "profile_incomplete" | "suspended" | "deletion_pending" | "deleted";

export type UserRow = {
  id: string;
  name: string;
  photoUrl: string | null;
  playingRole: string | null;
  state: string | null;
  district: string | null;
  joinedAt: string;
  lastActive: string | null;
  organizes: number;
  owns: number;
  plays: number;
  status: UserStatus;
  openReports: number;
  internal: boolean;
};

export type PersonRef = { id: string | null; name: string };

export type Fee = { amount: number | null; state: "NO_FEE" | "PROOF_UPLOADED" | "WAITING_FOR_PROOF" };

export type PaymentProof = { url: string | null; leagueId: string; payer: PersonRef; kind: string; fee: number | null; uploadedAt: string | null };

export type SearchResult = { leagues: LeagueRow[]; users: UserRow[] };

export type ApiProblem = { status: number; code: string; detail: string };
