package com.crichere.backend.admin

import com.crichere.backend.common.ObjectDeleter
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID

class AdminNotFoundException(val what: String) : RuntimeException("$what not found")

/** A refused action, with a stable code the admin site can explain. */
class AdminConflictException(val code: String, val detail: String) : RuntimeException(code)

data class AdminPage<T>(val items: List<T>, val nextCursor: String?)

data class ReportListItem(
    val id: UUID,
    val reason: String,
    val status: String,
    val targetType: String,
    val targetId: UUID?,
    val targetName: String?,
    val detailsPreview: String?,
    val createdAt: Instant,
    val openOnTarget: Long,
)

data class AuditEntry(
    val id: UUID,
    val at: Instant,
    val action: String,
    val adminEmail: String?,
    val reportId: UUID?,
    val userId: UUID?,
    val leagueId: UUID?,
    val note: String?,
)

data class ReportTarget(
    val kind: String,
    val id: UUID?,
    val name: String?,
    val district: String?,
    val joinedAt: Instant?,
    val suspended: Boolean,
    val deleted: Boolean,
    val openReports: Long,
    val pastActions: Long,
    val organizerId: UUID?,
    val organizerName: String?,
)

/** Only ever returned on report detail, to admins (docs/PHASE18.md S3). */
data class ReportReporter(val id: UUID, val name: String?, val district: String?, val reportsFiled: Long)

data class ReportDetail(
    val id: UUID,
    val reason: String,
    val status: String,
    val createdAt: Instant,
    val resolvedAt: Instant?,
    val details: String?,
    val contextLeagueId: UUID?,
    val evidence: Map<String, Any?>,
    val target: ReportTarget,
    val reporter: ReportReporter?,
    val history: List<AuditEntry>,
)

enum class ImageKind(
    val table: String,
    val idColumn: String,
    val urlColumn: String,
    val removedColumn: String,
    val ownerColumn: String,
    val nameColumn: String?,
    val noticeKind: String,
) {
    PHOTO("profiles", "user_id", "photo_url", "photo_removed", "user_id", null, "PHOTO"),
    LEAGUE_LOGO("leagues", "id", "logo_url", "logo_removed", "organizer_user_id", "name", "LEAGUE_LOGO"),
    LEAGUE_BANNER("leagues", "id", "banner_url", "banner_removed", "organizer_user_id", "name", "LEAGUE_BANNER"),
    FRANCHISE_LOGO("league_franchises", "id", "logo_url", "logo_removed", "owner_user_id", "name", "FRANCHISE_LOGO"),
}

/** What happened to the stored file after an image was removed from the app. */
enum class StoredFileOutcome { DELETED, KEPT_STILL_USED, KEPT_NOT_OWNED, KEPT_UNKNOWN_LOCATION, DELETE_FAILED }

data class RemoveImageResult(val ownerId: UUID?, val storedFile: StoredFileOutcome)

data class SuspendedAccount(
    val userId: UUID,
    val name: String?,
    val district: String?,
    val suspendedAt: Instant,
    val by: String?,
    val note: String?,
    val reportId: UUID?,
)

data class BlockedNumber(val hash: String, val since: Instant)

/**
 * The moderation actions of `backend/tools/moderation/moderation.py`, for the admin web
 * (docs/PHASE20.md Step 3). Same SQL and the same rules, plus the admin's email on every audit row
 * and a required note. Reports never trigger anything automatically; every action here is a
 * person's decision.
 */
@Service
class AdminModerationService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val audit: AdminAuditWriter,
    private val deleter: ObjectDeleter,
    private val objectMapper: ObjectMapper,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ------------------------------------------------------------------ reports

    /** OPEN lists oldest first (the 24-hour target is about waiting); any other view newest first. */
    fun listReports(status: String, reason: String?, cursor: String?, limit: Int): AdminPage<ReportListItem> {
        require(status in REPORT_VIEWS)
        val ascending = status == "OPEN"
        val params = MapSqlParameterSource("limit", limit + 1)
        val where = mutableListOf<String>()
        if (status != "ALL") where += "r.status = :status".also { params.addValue("status", status) }
        if (reason != null) where += "r.reason = :reason".also { params.addValue("reason", reason) }
        decodeCursor(cursor)?.let { (at, id) ->
            where += "(r.created_at, r.id) ${if (ascending) ">" else "<"} (:cAt, :cId)"
            params.addValue("cAt", Timestamp.from(at)).addValue("cId", id)
        }
        val dir = if (ascending) "asc" else "desc"
        val rows = jdbc.query(
            """
            select r.id, r.reason, r.status, r.target_type, coalesce(r.target_user_id, r.target_league_id) as target_id,
                   r.details, r.created_at,
                   case when r.target_type = 'USER' then p.name else l.name end as target_name,
                   (select count(*) from reports o where o.status = 'OPEN'
                      and ((r.target_user_id is not null and o.target_user_id = r.target_user_id)
                        or (r.target_league_id is not null and o.target_league_id = r.target_league_id))) as open_on_target
            from reports r
            left join profiles p on p.user_id = r.target_user_id
            left join leagues l on l.id = r.target_league_id
            ${if (where.isEmpty()) "" else "where " + where.joinToString(" and ")}
            order by r.created_at $dir, r.id $dir
            limit :limit
            """,
            params,
        ) { rs, _ ->
            ReportListItem(
                id = rs.uuid("id")!!,
                reason = rs.getString("reason"),
                status = rs.getString("status"),
                targetType = rs.getString("target_type"),
                targetId = rs.uuid("target_id"),
                targetName = rs.getString("target_name"),
                detailsPreview = rs.getString("details")?.take(PREVIEW),
                createdAt = rs.instant("created_at")!!,
                openOnTarget = rs.getLong("open_on_target"),
            )
        }
        return page(rows, limit) { encodeCursor(it.createdAt, it.id) }
    }

    fun reportDetail(id: UUID): ReportDetail {
        val p = MapSqlParameterSource("id", id)
        val row = jdbc.query(
            """
            select id, reason, status, created_at, resolved_at, details, snapshot, target_type, target_user_id,
                   target_league_id, context_league_id, reporter_id
            from reports where id = :id
            """,
            p,
        ) { rs, _ -> rs.toMap() }.firstOrNull() ?: throw AdminNotFoundException("Report")

        val target = when (row["target_type"]) {
            "USER" -> userTarget(row["target_user_id"] as UUID?)
            else -> leagueTarget(row["target_league_id"] as UUID?)
        }
        val reporter = (row["reporter_id"] as UUID?)?.let { reporterId ->
            jdbc.query(
                """
                select u.id, p.name, p.district, (select count(*) from reports x where x.reporter_id = u.id) as filed
                from users u left join profiles p on p.user_id = u.id where u.id = :id
                """,
                MapSqlParameterSource("id", reporterId),
            ) { rs, _ -> ReportReporter(rs.uuid("id")!!, rs.getString("name"), rs.getString("district"), rs.getLong("filed")) }
                .firstOrNull()
        }
        @Suppress("UNCHECKED_CAST")
        val evidence = runCatching { objectMapper.readValue(row["snapshot"] as String, Map::class.java) as Map<String, Any?> }
            .getOrDefault(emptyMap())
        return ReportDetail(
            id = id,
            reason = row["reason"] as String,
            status = row["status"] as String,
            createdAt = row["created_at"] as Instant,
            resolvedAt = row["resolved_at"] as Instant?,
            details = row["details"] as String?,
            contextLeagueId = row["context_league_id"] as UUID?,
            evidence = evidence,
            target = target,
            reporter = reporter,
            history = auditRows("where report_id = :id", p, "asc", HISTORY_LIMIT),
        )
    }

    fun dismiss(reportId: UUID, adminEmail: String, note: String) = closeReport(reportId, "DISMISSED", "DISMISS_REPORT", adminEmail, note)

    fun resolve(reportId: UUID, adminEmail: String, note: String) = closeReport(reportId, "RESOLVED", "RESOLVE_REPORT", adminEmail, note)

    private fun closeReport(reportId: UUID, status: String, action: String, adminEmail: String, note: String) {
        tx.executeWithoutResult {
            val changed = jdbc.update(
                "update reports set status = :status, resolved_at = now() where id = :id and status = 'OPEN'",
                MapSqlParameterSource("status", status).addValue("id", reportId),
            )
            if (changed == 0) {
                if (!exists("reports", "id", reportId)) throw AdminNotFoundException("Report")
                throw AdminConflictException("REPORT_NOT_OPEN", "This report is already closed.")
            }
            audit.record(action, adminEmail, reportId = reportId, note = note)
        }
    }

    // ------------------------------------------------------------------ images

    /**
     * Clears the image for everyone, tells the owner once, then deletes the stored file only when
     * nothing else uses it and it sits under this row's own prefix (moderation.py remove-image).
     * The app change commits first, so a failed S3 delete never leaves the image visible.
     */
    fun removeImage(kind: ImageKind, targetId: UUID, reportId: UUID?, adminEmail: String, note: String): RemoveImageResult {
        data class Removed(val url: String, val owner: UUID?, val stillUsed: Boolean, val ownedPrefix: String)

        val removed = tx.execute {
            val p = MapSqlParameterSource("id", targetId)
            val row = jdbc.query(
                "select ${kind.urlColumn} as url, ${kind.ownerColumn} as owner, ${kind.nameColumn ?: "null"} as name " +
                    "from ${kind.table} where ${kind.idColumn} = :id for update",
                p,
            ) { rs, _ -> Triple(rs.getString("url"), rs.uuid("owner"), rs.getString("name")) }.firstOrNull()
                ?: throw AdminNotFoundException("Image owner")
            val (url, owner, subject) = row
            if (url == null) throw AdminConflictException("NO_IMAGE", "There is no image to remove.")

            jdbc.update("update ${kind.table} set ${kind.urlColumn} = null, ${kind.removedColumn} = true where ${kind.idColumn} = :id", p)
            val stillUsed = (jdbc.queryForObject(
                """
                select count(*) from (
                  select photo_url u from profiles union all select logo_url from leagues
                  union all select banner_url from leagues union all select logo_url from league_franchises
                ) refs where split_part(u, '?', 1) = split_part(:url, '?', 1)
                """,
                MapSqlParameterSource("url", url),
                Long::class.java,
            ) ?: 0L) > 0
            if (owner != null) {
                jdbc.update(
                    "insert into moderation_notices (id, user_id, kind, subject_name) values (:id, :user, :kind, :subject)",
                    MapSqlParameterSource("id", UUID.randomUUID()).addValue("user", owner)
                        .addValue("kind", kind.noticeKind).addValue("subject", subject),
                )
            }
            audit.record("REMOVE_IMAGE", adminEmail, reportId = reportId, userId = owner, note = "$note ($kind $url)")
            Removed(url, owner, stillUsed, ownedPrefix(kind, targetId))
        }!!

        val key = objectKey(removed.url)
        val outcome = when {
            removed.stillUsed -> StoredFileOutcome.KEPT_STILL_USED
            key == null -> StoredFileOutcome.KEPT_UNKNOWN_LOCATION
            !key.startsWith(removed.ownedPrefix) -> StoredFileOutcome.KEPT_NOT_OWNED
            else -> try {
                deleter.delete(listOf(key))
                StoredFileOutcome.DELETED
            } catch (e: Exception) {
                log.error("Removed image's stored file could not be deleted: {}", key, e)
                StoredFileOutcome.DELETE_FAILED
            }
        }
        return RemoveImageResult(removed.owner, outcome)
    }

    private fun ownedPrefix(kind: ImageKind, targetId: UUID): String = when (kind) {
        ImageKind.PHOTO -> "users/$targetId/"
        ImageKind.FRANCHISE_LOGO -> {
            val leagueId = jdbc.queryForObject(
                "select league_id from league_franchises where id = :id",
                MapSqlParameterSource("id", targetId),
                UUID::class.java,
            )
            "leagues/$leagueId/franchise-logos/"
        }
        else -> "leagues/$targetId/"
    }

    // ------------------------------------------------------------------ suspension

    /**
     * Signs the person out everywhere within 30 seconds (SuspensionChecker's cache) and blocks
     * sign-in. Refused while they organize a live auction unless [duringAuction] is set, because
     * suspending them stalls it (runbook rule).
     */
    fun suspend(userId: UUID, reportId: UUID?, duringAuction: Boolean, adminEmail: String, note: String) {
        tx.executeWithoutResult {
            val p = MapSqlParameterSource("id", userId)
            val live = jdbc.queryForList(
                "select name from leagues where organizer_user_id = :id and auction_status = 'IN_PROGRESS'",
                p,
                String::class.java,
            )
            if (live.isNotEmpty() && !duringAuction) {
                throw AdminConflictException("ORGANIZER_OF_LIVE_AUCTION", "They organize a live auction: ${live.joinToString()}.")
            }
            // Same row lock a token refresh takes, so an in-flight refresh can't outrun the suspension.
            val found = jdbc.query("select id from users where id = :id and deleted_at is null for update", p) { rs, _ -> rs.uuid("id") }
            if (found.isEmpty()) throw AdminNotFoundException("Account")
            val changed = jdbc.update("update users set suspended_at = now() where id = :id and suspended_at is null", p)
            if (changed == 0) throw AdminConflictException("ALREADY_SUSPENDED", "This account is already suspended.")
            jdbc.update("update refresh_tokens set revoked_at = now() where user_id = :id and revoked_at is null", p)
            jdbc.update("delete from device_tokens where user_id = :id", p)
            audit.record("SUSPEND", adminEmail, reportId = reportId, userId = userId, note = note)
        }
    }

    fun unsuspend(userId: UUID, adminEmail: String, note: String) {
        tx.executeWithoutResult {
            val changed = jdbc.update(
                "update users set suspended_at = null where id = :id and suspended_at is not null",
                MapSqlParameterSource("id", userId),
            )
            if (changed == 0) {
                if (!exists("users", "id", userId)) throw AdminNotFoundException("Account")
                throw AdminConflictException("NOT_SUSPENDED", "This account isn't suspended.")
            }
            audit.record("UNSUSPEND", adminEmail, userId = userId, note = note)
        }
    }

    fun suspendedAccounts(): List<SuspendedAccount> =
        jdbc.query(
            """
            select u.id, p.name, p.district, u.suspended_at, a.admin_email, a.note, a.report_id
            from users u
            left join profiles p on p.user_id = u.id
            left join lateral (
              select admin_email, note, report_id from moderation_actions m
              where m.user_id = u.id and m.action = 'SUSPEND' order by m.created_at desc limit 1
            ) a on true
            where u.suspended_at is not null and u.deleted_at is null
            order by u.suspended_at desc
            """,
            MapSqlParameterSource(),
        ) { rs, _ ->
            SuspendedAccount(
                userId = rs.uuid("id")!!,
                name = rs.getString("name"),
                district = rs.getString("district"),
                suspendedAt = rs.instant("suspended_at")!!,
                by = rs.getString("admin_email") ?: "script",
                note = rs.getString("note"),
                reportId = rs.uuid("report_id"),
            )
        }

    fun blockedNumbers(): List<BlockedNumber> =
        jdbc.query(
            "select phone_lookup_hash, created_at from suspended_phone_hashes order by created_at desc",
            MapSqlParameterSource(),
        ) { rs, _ -> BlockedNumber(rs.getString("phone_lookup_hash"), rs.instant("created_at")!!) }

    /** Lets a number whose suspended account was deleted sign up again (runbook "Unsuspending"). */
    fun releaseNumber(hash: String, adminEmail: String, note: String) {
        tx.executeWithoutResult {
            val changed = jdbc.update(
                "delete from suspended_phone_hashes where phone_lookup_hash = :h",
                MapSqlParameterSource("h", hash),
            )
            if (changed == 0) throw AdminNotFoundException("Blocked number")
            audit.record("RELEASE_NUMBER", adminEmail, note = note)
        }
    }

    // ------------------------------------------------------------------ audit log

    fun auditLog(action: String?, adminEmail: String?, cursor: String?, limit: Int): AdminPage<AuditEntry> {
        val params = MapSqlParameterSource()
        val where = mutableListOf<String>()
        if (action != null) where += "action = :action".also { params.addValue("action", action) }
        if (adminEmail != null) where += "coalesce(admin_email, 'script') = :admin".also { params.addValue("admin", adminEmail.lowercase()) }
        decodeCursor(cursor)?.let { (at, id) ->
            where += "(created_at, id) < (:cAt, :cId)"
            params.addValue("cAt", Timestamp.from(at)).addValue("cId", id)
        }
        val rows = auditRows(if (where.isEmpty()) "" else "where " + where.joinToString(" and "), params, "desc", limit + 1)
        return page(rows, limit) { encodeCursor(it.at, it.id) }
    }

    // ------------------------------------------------------------------ helpers

    private fun userTarget(id: UUID?): ReportTarget {
        if (id == null) return ReportTarget("USER", null, null, null, null, false, true, 0, 0, null, null)
        return jdbc.query(
            """
            select u.id, p.name, p.district, u.created_at, u.suspended_at, u.deleted_at,
                   (select count(*) from reports r where r.target_user_id = u.id and r.status = 'OPEN') as open_reports,
                   (select count(*) from moderation_actions m where m.user_id = u.id
                      and m.action in ('REMOVE_IMAGE', 'SUSPEND')) as past_actions
            from users u left join profiles p on p.user_id = u.id where u.id = :id
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ ->
            ReportTarget(
                "USER", id, rs.getString("name"), rs.getString("district"), rs.instant("created_at"),
                rs.getTimestamp("suspended_at") != null, rs.getTimestamp("deleted_at") != null,
                rs.getLong("open_reports"), rs.getLong("past_actions"), null, null,
            )
        }.firstOrNull() ?: ReportTarget("USER", id, null, null, null, false, true, 0, 0, null, null)
    }

    private fun leagueTarget(id: UUID?): ReportTarget {
        if (id == null) return ReportTarget("LEAGUE", null, null, null, null, false, true, 0, 0, null, null)
        return jdbc.query(
            """
            select l.id, l.name, l.district, l.created_at, l.organizer_user_id, p.name as organizer_name,
                   (select count(*) from reports r where r.target_league_id = l.id and r.status = 'OPEN') as open_reports,
                   (select count(*) from moderation_actions m where m.league_id = l.id) as past_actions
            from leagues l left join profiles p on p.user_id = l.organizer_user_id where l.id = :id
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ ->
            ReportTarget(
                "LEAGUE", id, rs.getString("name"), rs.getString("district"), rs.instant("created_at"), false, false,
                rs.getLong("open_reports"), rs.getLong("past_actions"), rs.uuid("organizer_user_id"), rs.getString("organizer_name"),
            )
        }.firstOrNull() ?: ReportTarget("LEAGUE", id, null, null, null, false, true, 0, 0, null, null)
    }

    private fun auditRows(where: String, params: MapSqlParameterSource, dir: String, limit: Int): List<AuditEntry> =
        jdbc.query(
            """
            select id, created_at, action, coalesce(admin_email, 'script') as admin_email, report_id, user_id, league_id, note
            from moderation_actions $where order by created_at $dir, id $dir limit $limit
            """,
            params,
        ) { rs, _ ->
            AuditEntry(
                rs.uuid("id")!!, rs.instant("created_at")!!, rs.getString("action"), rs.getString("admin_email"),
                rs.uuid("report_id"), rs.uuid("user_id"), rs.uuid("league_id"), rs.getString("note"),
            )
        }

    private fun exists(table: String, column: String, id: UUID): Boolean =
        jdbc.queryForObject("select exists(select 1 from $table where $column = :id)", MapSqlParameterSource("id", id), Boolean::class.java) == true

    private fun <T> page(rows: List<T>, limit: Int, cursorOf: (T) -> String): AdminPage<T> =
        if (rows.size > limit) AdminPage(rows.take(limit), cursorOf(rows[limit - 1])) else AdminPage(rows, null)

    private fun encodeCursor(at: Instant, id: UUID): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${at.toEpochMilli()}_${at.nano}_$id".toByteArray())

    private fun decodeCursor(cursor: String?): Pair<Instant, UUID>? {
        if (cursor.isNullOrBlank()) return null
        return runCatching {
            val (millis, nano, id) = String(Base64.getUrlDecoder().decode(cursor)).split("_")
            Instant.ofEpochSecond(Math.floorDiv(millis.toLong(), 1000L), nano.toLong()) to UUID.fromString(id)
        }.getOrElse { throw AdminConflictException("BAD_CURSOR", "This page link is no longer valid.") }
    }

    private fun ResultSet.uuid(column: String): UUID? = getObject(column) as UUID?

    private fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()

    private fun ResultSet.toMap(): Map<String, Any?> {
        val meta = metaData
        return (1..meta.columnCount).associate { i ->
            val value = getObject(i)
            meta.getColumnLabel(i) to if (value is Timestamp) value.toInstant() else value
        }
    }

    companion object {
        val REPORT_VIEWS = setOf("OPEN", "RESOLVED", "DISMISSED", "ALL")
        private const val PREVIEW = 140
        private const val HISTORY_LIMIT = 100

        /** The S3 key in one of our image URLs (same parsing as AccountPurgeService and MediaSweep). */
        fun objectKey(url: String?): String? =
            url?.substringAfter(".amazonaws.com/", "")?.substringBefore('?')?.ifBlank { null }
    }
}
