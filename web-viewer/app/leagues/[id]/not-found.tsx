export default function LeagueNotFound() {
  return (
    <div style={{ maxWidth: 560, margin: "80px auto", padding: "0 16px", textAlign: "left" }}>
      <h1 style={{ fontFamily: "var(--font-display)", fontSize: "2rem", fontWeight: 700 }}>League not found</h1>
      <p style={{ color: "var(--ink-dim)", marginTop: 8 }}>
        This link doesn&rsquo;t match a league we know about &mdash; it may have been removed, or the link may be mistyped.
      </p>
    </div>
  );
}
