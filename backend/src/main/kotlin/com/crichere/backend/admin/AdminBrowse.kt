package com.crichere.backend.admin

import com.crichere.backend.auth.PhoneCryptoService
import com.crichere.backend.auth.PhoneNumberNormalizer
import com.crichere.backend.common.PaymentScreenshotUrlSigner
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
 * Read-only league and user browsers (docs/PHASE20.md sections 6 and 7, Step 9). Nothing here edits
 * app data except the internal flag; changes to people go through moderation actions. Phone numbers
 * are never returned. Deleted accounts show as "Deleted user"; purged ones are not listed.
 */

data class Total(val count: Long, val capped: Boolean)

data class BrowsePage<T>(val items: List<T>, val nextCursor: String?, val total: Total?)

data class LeagueRow(
    val id: UUID,
    val name: String,
    val logoUrl: String?,
    val organizerId: UUID,
    val organizerName: String,
    val state: String,
    val district: String,
    val startsOn: LocalDate,
    val status: String,
    val franchises: Long,
    val franchisesRequired: Int?,
    val players: Long,
    val playersRequired: Int?,
    val openReports: Long,
    val createdAt: Instant,
    val internal: Boolean,
)

data class UserRow(
    val id: UUID,
    val name: String,
    val photoUrl: String?,
    val playingRole: String?,
    val state: String?,
    val district: String?,
    val joinedAt: Instant,
    val lastActive: LocalDate?,
    val organizes: Long,
    val owns: Long,
    val plays: Long,
    val status: String,
    val openReports: Long,
    val internal: Boolean,
)

data class PersonRef(val id: UUID?, val name: String)

data class LeagueFilters(
    val q: String? = null,
    val state: String? = null,
    val district: String? = null,
    val status: String? = null,
    val createdFrom: LocalDate? = null,
    val createdTo: LocalDate? = null,
    val hasOpenReports: Boolean = false,
    val includeInternal: Boolean = false,
    val sort: String = "newest",
    val id: UUID? = null,
)

data class UserFilters(
    val q: String? = null,
    val state: String? = null,
    val district: String? = null,
    val status: String? = null,
    val joinedFrom: LocalDate? = null,
    val joinedTo: LocalDate? = null,
    val hasOpenReports: Boolean = false,
    val includeInternal: Boolean = false,
    val sort: String = "newest",
    val id: UUID? = null,
)

data class SearchResult(val leagues: List<LeagueRow>, val users: List<UserRow>)

data class PaymentProof(val url: String?, val leagueId: UUID, val payer: PersonRef, val kind: String, val fee: BigDecimal?, val uploadedAt: Instant?)

@Service
class AdminBrowseService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val tx: TransactionTemplate,
    private val audit: AdminAuditWriter,
    private val phoneCrypto: PhoneCryptoService,
    private val screenshots: PaymentScreenshotUrlSigner,
    private val liveMetrics: LiveMetrics,
) {
    private val lookupBuckets = ConcurrentHashMap<String, Bucket>()

    // ------------------------------------------------------------------ leagues

    fun leagues(filters: LeagueFilters, cursor: String?, limit: Int): BrowsePage<LeagueRow> {
        val sort = LEAGUE_SORTS[filters.sort] ?: throw AdminConflictException("BAD_FILTER", "Unknown sort.")
        val p = MapSqlParameterSource()
        val where = mutableListOf("true")
        if (!filters.includeInternal) where += "not ou.is_internal"
        filters.id?.let { where += "l.id = :fid"; p.addValue("fid", it) }
        filters.q?.trim()?.takeIf { it.isNotEmpty() }?.let {
            where += "(l.name ilike :q escape '\\' or op.name ilike :q escape '\\')"
            p.addValue("q", like(it))
        }
        filters.state?.let { where += "l.state = :state"; p.addValue("state", it) }
        filters.district?.let { where += "l.district = :district"; p.addValue("district", it) }
        filters.status?.let {
            if (it !in LEAGUE_STATUSES) throw AdminConflictException("BAD_FILTER", "Unknown status.")
            where += "$LEAGUE_STATUS = :status"
            p.addValue("status", it)
        }
        filters.createdFrom?.let { where += "l.created_at >= :cFrom"; p.addValue("cFrom", startOf(it)) }
        filters.createdTo?.let { where += "l.created_at < :cTo"; p.addValue("cTo", startOf(it.plusDays(1))) }
        if (filters.hasOpenReports) where += "exists (select 1 from reports r where r.target_league_id = l.id and r.status = 'OPEN')"

        val base = "from leagues l join users ou on ou.id = l.organizer_user_id left join profiles op on op.user_id = l.organizer_user_id where ${where.joinToString(" and ")}"
        val total = if (cursor == null) cappedTotal(base, p) else null
        val pageWhere = keyset(sort, cursor, p)
        val page = jdbc.query(
            """
            select l.id, l.name, case when l.logo_removed then null else l.logo_url end as logo_url, l.organizer_user_id,
                   case when ou.deleted_at is not null then 'Deleted user' else coalesce(op.name, 'No name yet') end as org_name,
                   l.state, l.district, l.starts_on, l.franchises_required, l.players_required, l.created_at, ou.is_internal,
                   $LEAGUE_STATUS as status, ${sort.valueText} as sort_value
            $base $pageWhere
            order by ${sort.order}
            limit :limit
            """,
            p.addValue("limit", limit + 1),
        ) { rs, _ ->
            Pair(
                LeagueRow(
                    id = rs.uuid("id")!!, name = rs.getString("name"), logoUrl = rs.getString("logo_url"),
                    organizerId = rs.uuid("organizer_user_id")!!, organizerName = rs.getString("org_name"),
                    state = rs.getString("state"), district = rs.getString("district"),
                    startsOn = rs.getDate("starts_on").toLocalDate(), status = rs.getString("status"),
                    franchises = 0, franchisesRequired = rs.getObject("franchises_required") as Int?,
                    players = 0, playersRequired = rs.getObject("players_required") as Int?,
                    openReports = 0, createdAt = rs.getTimestamp("created_at").toInstant(), internal = rs.getBoolean("is_internal"),
                ),
                rs.getString("sort_value"),
            )
        }
        val hasMore = page.size > limit
        val items = page.take(limit)
        val counts = leagueCounts(items.map { it.first.id })
        val withCounts = items.map { (row, _) ->
            val c = counts[row.id]
            row.copy(franchises = c?.get(0) ?: 0, players = c?.get(1) ?: 0, openReports = c?.get(2) ?: 0)
        }
        val next = if (hasMore) items.last().let { encodeCursor(filters.sort, it.second, it.first.id) } else null
        return BrowsePage(withCounts, next, total)
    }

    private fun leagueCounts(ids: List<UUID>): Map<UUID, List<Long>> {
        if (ids.isEmpty()) return emptyMap()
        return jdbc.query(
            """
            select l.id,
                   (select count(*) from league_franchises f where f.league_id = l.id and f.removed_at is null) as franchises,
                   (select count(*) from league_players pl where pl.league_id = l.id and pl.removed_at is null) as players,
                   (select count(*) from reports r where r.target_league_id = l.id and r.status = 'OPEN') as open_reports
            from leagues l where l.id in (:ids)
            """,
            MapSqlParameterSource("ids", ids),
        ) { rs, _ -> rs.uuid("id")!! to listOf(rs.getLong("franchises"), rs.getLong("players"), rs.getLong("open_reports")) }.toMap()
    }

    fun leagueOverview(id: UUID): Map<String, Any?> {
        val league = jdbc.query(
            """
            select l.*, $LEAGUE_STATUS as status, g.name as ground_name, g.latitude, g.longitude, g.district as ground_district,
                   (select count(*) from league_follows f where f.league_id = l.id) as followers
            from leagues l left join grounds g on g.id = l.ground_id where l.id = :id
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ -> rs.toMap() }.firstOrNull() ?: throw AdminNotFoundException("League")
        val coOrganizers = jdbc.query(
            """
            select r.user_id, r.granted_at, r.revoked_at, u.deleted_at, p.name from league_roles r
            join users u on u.id = r.user_id left join profiles p on p.user_id = r.user_id
            where r.league_id = :id order by r.granted_at
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ ->
            mapOf(
                "person" to person(rs, "user_id"),
                "grantedAt" to rs.instant("granted_at"),
                "revokedAt" to rs.instant("revoked_at"),
            )
        }
        val organizer = personById(league["organizer_user_id"] as UUID)
        return league.filterKeys { it !in HIDDEN_LEAGUE_COLUMNS }.mapKeys { camel(it.key) } +
            mapOf("organizer" to organizer, "coOrganizers" to coOrganizers)
    }

    fun leagueFranchises(id: UUID): List<Map<String, Any?>> {
        requireLeague(id)
        return jdbc.query(
            """
            select f.id, f.name, case when f.logo_removed then null else f.logo_url end as logo_url, f.logo_removed, f.owner_user_id,
                   u.deleted_at, p.name, f.joined_at, f.leave_requested_at, f.removed_at, f.payment_screenshot_url is not null as has_proof,
                   l.franchise_fee, l.auction_purse,
                   (select count(*) from league_players pl where pl.sold_to_franchise_id = f.id) as players_bought,
                   (select coalesce(sum(pl.sold_price), 0) from league_players pl where pl.sold_to_franchise_id = f.id) as purse_used
            from league_franchises f join leagues l on l.id = f.league_id
            left join users u on u.id = f.owner_user_id left join profiles p on p.user_id = f.owner_user_id
            where f.league_id = :id order by f.joined_at
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ ->
            val purse = rs.getBigDecimal("auction_purse")
            val used = rs.getBigDecimal("purse_used")
            mapOf(
                "id" to rs.uuid("id"), "name" to rs.getString("name"), "logoUrl" to rs.getString("logo_url"),
                "logoRemoved" to rs.getBoolean("logo_removed"), "owner" to person(rs, "owner_user_id"),
                "joinedAt" to rs.instant("joined_at"), "leaveRequestedAt" to rs.instant("leave_requested_at"),
                "removedAt" to rs.instant("removed_at"),
                "fee" to feeState(rs.getBigDecimal("franchise_fee"), rs.getBoolean("has_proof")),
                "playersBought" to rs.getLong("players_bought"), "purseUsed" to used,
                "purseLeft" to purse?.subtract(used),
            )
        }
    }

    fun leaguePlayers(id: UUID): List<Map<String, Any?>> {
        requireLeague(id)
        return jdbc.query(
            """
            select pl.id, pl.user_id, u.deleted_at, p.name, p.playing_role, pl.joined_at, pl.leave_requested_at, pl.removed_at,
                   pl.payment_screenshot_url is not null as has_proof, l.player_fee, pl.auction_outcome, pl.sold_price, f.name as franchise_name
            from league_players pl join leagues l on l.id = pl.league_id
            left join users u on u.id = pl.user_id left join profiles p on p.user_id = pl.user_id
            left join league_franchises f on f.id = pl.sold_to_franchise_id
            where pl.league_id = :id order by pl.joined_at
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ ->
            mapOf(
                "id" to rs.uuid("id"), "person" to person(rs, "user_id"), "playingRole" to rs.getString("playing_role"),
                "joinedAt" to rs.instant("joined_at"), "leaveRequestedAt" to rs.instant("leave_requested_at"),
                "removedAt" to rs.instant("removed_at"),
                "fee" to feeState(rs.getBigDecimal("player_fee"), rs.getBoolean("has_proof")),
                "auctionOutcome" to rs.getString("auction_outcome"), "soldPrice" to rs.getBigDecimal("sold_price"),
                "boughtBy" to rs.getString("franchise_name"),
            )
        }
    }

    fun leagueAuction(id: UUID): Map<String, Any?> {
        val settings = jdbc.query(
            """
            select auction_status, auction_base_price, auction_purse, auction_squad_min, auction_squad_max, auction_bid_increment, auction_bid_ranges,
                   auction_allow_exceed_purse, auction_scheduled_at, auction_completed_at, auction_lot_counter
            from leagues where id = :id
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ -> rs.toMap().mapKeys { camel(it.key) } }.firstOrNull() ?: throw AdminNotFoundException("League")
        val bids = jdbc.query(
            """
            select b.player_id, b.amount, b.placed_at, f.name as franchise from auction_bids b
            left join league_franchises f on f.id = b.franchise_id where b.league_id = :id order by b.placed_at desc limit 2000
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ -> mapOf("playerId" to rs.uuid("player_id"), "amount" to rs.getBigDecimal("amount"), "at" to rs.instant("placed_at"), "franchise" to rs.getString("franchise")) }
        return settings + mapOf("bids" to bids)
    }

    fun leagueAwards(id: UUID): List<Map<String, Any?>> {
        requireLeague(id)
        return jdbc.query(
            "select name, cash_amount, has_trophy from league_awards where league_id = :id order by display_order",
            MapSqlParameterSource("id", id),
        ) { rs, _ -> mapOf("name" to rs.getString("name"), "cashAmount" to rs.getBigDecimal("cash_amount"), "hasTrophy" to rs.getBoolean("has_trophy")) }
    }

    fun leagueModeration(id: UUID): Map<String, Any?> {
        requireLeague(id)
        val p = MapSqlParameterSource("id", id)
        val reports = jdbc.query(
            """
            select r.id, r.reason, r.status, r.created_at, r.target_type from reports r
            where r.target_league_id = :id or r.context_league_id = :id order by r.created_at desc limit 200
            """,
            p,
        ) { rs, _ -> mapOf("id" to rs.uuid("id"), "reason" to rs.getString("reason"), "status" to rs.getString("status"), "createdAt" to rs.instant("created_at"), "targetType" to rs.getString("target_type")) }
        val actions = jdbc.query(
            "select action, coalesce(admin_email, 'script') as by, note, created_at from moderation_actions where league_id = :id order by created_at desc limit 200",
            p,
        ) { rs, _ -> mapOf("action" to rs.getString("action"), "by" to rs.getString("by"), "note" to rs.getString("note"), "at" to rs.instant("created_at")) }
        return mapOf("reports" to reports, "actions" to actions)
    }

    /** Key events from stored timestamps only; no new tracking. */
    fun leagueTimeline(id: UUID): List<Map<String, Any?>> {
        requireLeague(id)
        return jdbc.query(
            """
            select at, kind from (
              select created_at as at, 'LEAGUE_CREATED' as kind from leagues where id = :id
              union all select updated_at, 'LEAGUE_UPDATED' from leagues where id = :id and updated_at > created_at
              union all select auction_scheduled_at, 'AUCTION_SCHEDULED_FOR' from leagues where id = :id and auction_scheduled_at is not null
              union all select min(placed_at), 'FIRST_BID' from auction_bids where league_id = :id having min(placed_at) is not null
              union all select auction_completed_at, 'AUCTION_COMPLETED' from leagues where id = :id and auction_completed_at is not null
              union all select completed_at, 'LEAGUE_COMPLETED' from leagues where id = :id and completed_at is not null
              union all select organizer_handover_at, 'ORGANIZER_HANDOVER' from leagues where id = :id and organizer_handover_at is not null
              union all select granted_at, 'CO_ORGANIZER_ADDED' from league_roles where league_id = :id
              union all select joined_at, 'FRANCHISE_JOINED' from league_franchises where league_id = :id
              union all select joined_at, 'PLAYER_JOINED' from league_players where league_id = :id
              union all select removed_at, 'PLAYER_LEFT_OR_REMOVED' from league_players where league_id = :id and removed_at is not null
            ) t order by at desc limit 300
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ -> mapOf("at" to rs.instant("at"), "kind" to rs.getString("kind")) }
    }

    /** Opening a payment screenshot is audited (section 6). */
    fun paymentProof(kind: String, rowId: UUID, adminEmail: String): PaymentProof {
        val (table, payerColumn, feeColumn) = when (kind) {
            "PLAYER" -> Triple("league_players", "user_id", "player_fee")
            "FRANCHISE" -> Triple("league_franchises", "owner_user_id", "franchise_fee")
            else -> throw AdminConflictException("BAD_FILTER", "Unknown kind.")
        }
        val row = jdbc.query(
            """
            select x.league_id, x.$payerColumn as payer, x.payment_screenshot_url as url, x.joined_at, l.$feeColumn as fee
            from $table x join leagues l on l.id = x.league_id where x.id = :id
            """,
            MapSqlParameterSource("id", rowId),
        ) { rs, _ -> listOf(rs.uuid("league_id"), rs.uuid("payer"), rs.getString("url"), rs.instant("joined_at"), rs.getBigDecimal("fee")) }
            .firstOrNull() ?: throw AdminNotFoundException("Payment proof")
        val leagueId = row[0] as UUID
        val payer = row[1] as UUID?
        val stored = row[2] as String? ?: throw AdminNotFoundException("Payment proof")
        audit.record("VIEW_PAYMENT_PROOF", adminEmail, "$kind $rowId", userId = payer, leagueId = leagueId)
        val url = payer?.let { screenshots.readUrl(stored, leagueId, it) }
        return PaymentProof(url, leagueId, payer?.let { personById(it) } ?: PersonRef(null, DELETED), kind, row[4] as BigDecimal?, row[3] as Instant?)
    }

    // ------------------------------------------------------------------ users

    fun users(filters: UserFilters, cursor: String?, limit: Int): BrowsePage<UserRow> {
        val sort = USER_SORTS[filters.sort] ?: throw AdminConflictException("BAD_FILTER", "Unknown sort.")
        val p = MapSqlParameterSource()
        val where = mutableListOf("u.deleted_at is null")
        if (!filters.includeInternal) where += "not u.is_internal"
        filters.id?.let { where += "u.id = :fid"; p.addValue("fid", it) }
        filters.q?.trim()?.takeIf { it.isNotEmpty() }?.let { where += "p.name ilike :q escape '\\'"; p.addValue("q", like(it)) }
        filters.state?.let { where += "p.state = :state"; p.addValue("state", it) }
        filters.district?.let { where += "p.district = :district"; p.addValue("district", it) }
        filters.status?.let {
            if (it !in USER_STATUSES) throw AdminConflictException("BAD_FILTER", "Unknown status.")
            where += "$USER_STATUS = :status"
            p.addValue("status", it)
        }
        filters.joinedFrom?.let { where += "u.created_at >= :jFrom"; p.addValue("jFrom", startOf(it)) }
        filters.joinedTo?.let { where += "u.created_at < :jTo"; p.addValue("jTo", startOf(it.plusDays(1))) }
        if (filters.hasOpenReports) where += "exists (select 1 from reports r where r.target_user_id = u.id and r.status = 'OPEN')"

        val base = "from users u left join profiles p on p.user_id = u.id where ${where.joinToString(" and ")}"
        val total = if (cursor == null) cappedTotal(base, p) else null
        val pageWhere = keyset(sort, cursor, p)
        val page = jdbc.query(
            """
            select u.id, coalesce(p.name, 'No name yet') as name, case when p.photo_removed then null else p.photo_url end as photo_url,
                   p.playing_role, p.state, p.district, u.created_at, u.is_internal, $USER_STATUS as status, ${sort.valueText} as sort_value
            $base $pageWhere
            order by ${sort.order}
            limit :limit
            """,
            p.addValue("limit", limit + 1),
        ) { rs, _ ->
            Pair(
                UserRow(
                    id = rs.uuid("id")!!, name = rs.getString("name"), photoUrl = rs.getString("photo_url"),
                    playingRole = rs.getString("playing_role"), state = rs.getString("state"), district = rs.getString("district"),
                    joinedAt = rs.getTimestamp("created_at").toInstant(), lastActive = null, organizes = 0, owns = 0, plays = 0,
                    status = rs.getString("status"), openReports = 0, internal = rs.getBoolean("is_internal"),
                ),
                rs.getString("sort_value"),
            )
        }
        val hasMore = page.size > limit
        val items = page.take(limit)
        val counts = userCounts(items.map { it.first.id })
        val rows = items.map { (row, _) ->
            val c = counts[row.id]
            if (c == null) row else row.copy(lastActive = c.first, organizes = c.second[0], owns = c.second[1], plays = c.second[2], openReports = c.second[3])
        }
        val next = if (hasMore) items.last().let { encodeCursor(filters.sort, it.second, it.first.id) } else null
        return BrowsePage(rows, next, total)
    }

    private fun userCounts(ids: List<UUID>): Map<UUID, Pair<LocalDate?, List<Long>>> {
        if (ids.isEmpty()) return emptyMap()
        return jdbc.query(
            """
            select u.id,
                   (select max(a.day) from user_activity_days a where a.user_id = u.id) as last_active,
                   (select count(*) from leagues l where l.organizer_user_id = u.id) as organizes,
                   (select count(*) from league_franchises f where f.owner_user_id = u.id and f.removed_at is null) as owns,
                   (select count(*) from league_players pl where pl.user_id = u.id and pl.removed_at is null) as plays,
                   (select count(*) from reports r where r.target_user_id = u.id and r.status = 'OPEN') as open_reports
            from users u where u.id in (:ids)
            """,
            MapSqlParameterSource("ids", ids),
        ) { rs, _ ->
            rs.uuid("id")!! to Pair(
                rs.getDate("last_active")?.toLocalDate(),
                listOf(rs.getLong("organizes"), rs.getLong("owns"), rs.getLong("plays"), rs.getLong("open_reports")),
            )
        }.toMap()
    }

    fun userProfile(id: UUID): Map<String, Any?> {
        val row = jdbc.query(
            """
            select u.id, u.created_at, u.terms_accepted_at, u.terms_version, u.suspended_at, u.deletion_requested_at, u.deleted_at,
                   u.is_internal, $USER_STATUS as status, p.name, p.photo_url, p.photo_removed, p.state, p.district, p.playing_role,
                   p.batting_style, p.bowling_style,
                   (select max(a.day) from user_activity_days a where a.user_id = u.id) as last_active
            from users u left join profiles p on p.user_id = u.id where u.id = :id
            """,
            MapSqlParameterSource("id", id),
        ) { rs, _ -> rs.toMap() }.firstOrNull() ?: throw AdminNotFoundException("Account")
        if (row["deleted_at"] != null) {
            return mapOf("id" to id, "name" to DELETED, "status" to "deleted", "deletedAt" to row["deleted_at"])
        }
        return row.mapKeys { camel(it.key) }
    }

    fun userLeagues(id: UUID): Map<String, Any?> {
        requireUser(id)
        val p = MapSqlParameterSource("id", id)
        fun leagues(sql: String) = jdbc.query(sql, p) { rs, _ ->
            mapOf("leagueId" to rs.uuid("league_id"), "name" to rs.getString("name"), "status" to rs.getString("status"), "at" to rs.instant("at"), "detail" to rs.getString("detail"))
        }
        return mapOf(
            "organizes" to leagues("select l.id as league_id, l.name, $LEAGUE_STATUS as status, l.created_at as at, null as detail from leagues l where l.organizer_user_id = :id order by l.created_at desc"),
            "coOrganizes" to leagues("select l.id as league_id, l.name, $LEAGUE_STATUS as status, r.granted_at as at, case when r.revoked_at is null then null else 'revoked' end as detail from league_roles r join leagues l on l.id = r.league_id where r.user_id = :id order by r.granted_at desc"),
            "owns" to leagues("select l.id as league_id, l.name, $LEAGUE_STATUS as status, f.joined_at as at, f.name || case when f.removed_at is null then '' else ' (removed)' end as detail from league_franchises f join leagues l on l.id = f.league_id where f.owner_user_id = :id order by f.joined_at desc"),
            "plays" to leagues("select l.id as league_id, l.name, $LEAGUE_STATUS as status, pl.joined_at as at, case when pl.removed_at is null then coalesce(pl.auction_outcome, '') else 'left or removed' end as detail from league_players pl join leagues l on l.id = pl.league_id where pl.user_id = :id order by pl.joined_at desc"),
            "follows" to leagues("select l.id as league_id, l.name, $LEAGUE_STATUS as status, f.followed_at as at, null as detail from league_follows f join leagues l on l.id = f.league_id where f.user_id = :id order by f.followed_at desc"),
        )
    }

    fun userAuction(id: UUID): Map<String, Any?> {
        requireUser(id)
        val p = MapSqlParameterSource("id", id)
        val bought = jdbc.query(
            """
            select l.name as league, f.name as franchise, pp.name as player, pl.sold_price from league_franchises f
            join leagues l on l.id = f.league_id join league_players pl on pl.sold_to_franchise_id = f.id
            left join profiles pp on pp.user_id = pl.user_id where f.owner_user_id = :id order by l.created_at desc
            """,
            p,
        ) { rs, _ -> mapOf("league" to rs.getString("league"), "franchise" to rs.getString("franchise"), "player" to (rs.getString("player") ?: DELETED), "price" to rs.getBigDecimal("sold_price")) }
        val sales = jdbc.query(
            """
            select l.name as league, pl.auction_outcome, pl.sold_price, f.name as franchise from league_players pl
            join leagues l on l.id = pl.league_id left join league_franchises f on f.id = pl.sold_to_franchise_id
            where pl.user_id = :id and pl.auction_outcome is not null order by l.created_at desc
            """,
            p,
        ) { rs, _ -> mapOf("league" to rs.getString("league"), "outcome" to rs.getString("auction_outcome"), "price" to rs.getBigDecimal("sold_price"), "franchise" to rs.getString("franchise")) }
        return mapOf("boughtAsOwner" to bought, "ownSales" to sales)
    }

    /** Days active within the 13 months the policy keeps them. */
    fun userActivity(id: UUID): List<LocalDate> {
        requireUser(id)
        return jdbc.query(
            "select day from user_activity_days where user_id = :id order by day",
            MapSqlParameterSource("id", id),
        ) { rs, _ -> rs.getDate("day").toLocalDate() }
    }

    fun userModeration(id: UUID): Map<String, Any?> {
        requireUser(id)
        val p = MapSqlParameterSource("id", id)
        fun reports(column: String) = jdbc.query(
            "select id, reason, status, created_at from reports where $column = :id order by created_at desc limit 200",
            p,
        ) { rs, _ -> mapOf("id" to rs.uuid("id"), "reason" to rs.getString("reason"), "status" to rs.getString("status"), "createdAt" to rs.instant("created_at")) }
        val actions = jdbc.query(
            "select action, coalesce(admin_email, 'script') as by, note, created_at from moderation_actions where user_id = :id order by created_at desc limit 200",
            p,
        ) { rs, _ -> mapOf("action" to rs.getString("action"), "by" to rs.getString("by"), "note" to rs.getString("note"), "at" to rs.instant("created_at")) }
        val notices = jdbc.query(
            "select kind, subject_name, created_at, dismissed_at from moderation_notices where user_id = :id order by created_at desc",
            p,
        ) { rs, _ -> mapOf("kind" to rs.getString("kind"), "subject" to rs.getString("subject_name"), "at" to rs.instant("created_at"), "seenAt" to rs.instant("dismissed_at")) }
        val blocks = jdbc.queryForMap(
            """
            select (select count(*) from user_blocks where blocker_id = :id) as blocked_by_them,
                   (select count(*) from user_blocks where blocked_id = :id) as blocking_them
            """,
            p,
        )
        return mapOf(
            "reportsAbout" to reports("target_user_id"), "reportsFiled" to reports("reporter_id"),
            "actions" to actions, "notices" to notices,
            "blocks" to mapOf("blockedByThem" to blocks["blocked_by_them"], "blockingThem" to blocks["blocking_them"]),
        )
    }

    fun userAccount(id: UUID): Map<String, Any?> {
        requireUser(id)
        return jdbc.queryForMap(
            """
            select u.suspended_at, u.deletion_requested_at, u.is_internal,
                   (select count(*) from device_tokens d where d.user_id = u.id) as push_devices,
                   (select count(*) from refresh_tokens t where t.user_id = u.id and t.revoked_at is null and t.expires_at > now()) as app_sessions
            from users u where u.id = :id
            """,
            MapSqlParameterSource("id", id),
        ).mapKeys { camel(it.key) }
    }

    /**
     * Exact match only: the typed number is hashed like a sign-in and compared with the stored
     * fingerprint (section 7). The number is never stored or logged; each lookup is audited.
     */
    fun lookupByPhone(phone: String, adminEmail: String): List<UserRow> {
        val bucket = lookupBuckets.computeIfAbsent(adminEmail) {
            Bucket.builder().addLimit(Bandwidth.builder().capacity(LOOKUPS_PER_HOUR).refillGreedy(LOOKUPS_PER_HOUR, Duration.ofHours(1)).build()).build()
        }
        if (!bucket.tryConsume(1)) throw AdminConflictException("RATE_LIMITED", "Too many lookups. Try again later.")
        val e164 = PhoneNumberNormalizer.toIndianE164(phone)
        val hash = e164?.let { phoneCrypto.hmacLookupHash(it) }
        val ids = if (hash == null) {
            emptyList()
        } else {
            jdbc.queryForList(
                "select id from users where phone_lookup_hash = :h and deleted_at is null",
                MapSqlParameterSource("h", hash),
                UUID::class.java,
            ).filterNotNull()
        }
        audit.record("PHONE_LOOKUP", adminEmail, "${ids.size} match${if (ids.size == 1) "" else "es"}", userId = ids.firstOrNull())
        return ids.mapNotNull { userRowById(it) }
    }

    /** Leaves the account (and its leagues) out of every count and chart; changes nothing in the app. */
    fun setInternal(id: UUID, internal: Boolean, adminEmail: String) {
        tx.executeWithoutResult {
            val changed = jdbc.update(
                "update users set is_internal = :v where id = :id and deleted_at is null",
                MapSqlParameterSource("v", internal).addValue("id", id),
            )
            if (changed == 0) throw AdminNotFoundException("Account")
            audit.record(if (internal) "MARK_INTERNAL" else "UNMARK_INTERNAL", adminEmail, userId = id)
        }
        liveMetrics.setInternal(id, internal)
    }

    // ------------------------------------------------------------------ search

    /** Name (contains) or an exact id; five of each. */
    fun search(q: String): SearchResult {
        val text = q.trim()
        if (text.length < 2) return SearchResult(emptyList(), emptyList())
        val id = runCatching { UUID.fromString(text) }.getOrNull()
        if (id != null) {
            return SearchResult(listOfNotNull(leagueRowById(id)), listOfNotNull(userRowById(id)))
        }
        return SearchResult(
            leagues(LeagueFilters(q = text, includeInternal = true), null, SEARCH_LIMIT).items,
            users(UserFilters(q = text, includeInternal = true), null, SEARCH_LIMIT).items,
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun leagueRowById(id: UUID): LeagueRow? =
        leagues(LeagueFilters(includeInternal = true, id = id), null, 1).items.firstOrNull()

    private fun userRowById(id: UUID): UserRow? =
        users(UserFilters(includeInternal = true, id = id), null, 1).items.firstOrNull()

    private fun requireLeague(id: UUID) {
        if (jdbc.queryForObject("select exists(select 1 from leagues where id = :id)", MapSqlParameterSource("id", id), Boolean::class.java) != true) {
            throw AdminNotFoundException("League")
        }
    }

    private fun requireUser(id: UUID) {
        val ok = jdbc.queryForObject(
            "select exists(select 1 from users where id = :id and deleted_at is null)",
            MapSqlParameterSource("id", id),
            Boolean::class.java,
        )
        if (ok != true) throw AdminNotFoundException("Account")
    }

    private fun personById(id: UUID): PersonRef =
        jdbc.query(
            "select u.id as user_id, u.deleted_at, p.name from users u left join profiles p on p.user_id = u.id where u.id = :id",
            MapSqlParameterSource("id", id),
        ) { rs, _ -> person(rs, "user_id") }.firstOrNull() ?: PersonRef(null, DELETED)

    /** Expects `deleted_at` and `name` columns next to [idColumn]. */
    private fun person(rs: ResultSet, idColumn: String): PersonRef {
        val id = rs.uuid(idColumn) ?: return PersonRef(null, DELETED)
        if (rs.getTimestamp("deleted_at") != null) return PersonRef(id, DELETED)
        return PersonRef(id, rs.getString("name") ?: "No name yet")
    }

    private fun feeState(fee: BigDecimal?, hasProof: Boolean): Map<String, Any?> =
        mapOf("amount" to fee, "state" to when {
            fee == null -> "NO_FEE"
            hasProof -> "PROOF_UPLOADED"
            else -> "WAITING_FOR_PROOF"
        })

    private fun cappedTotal(base: String, p: MapSqlParameterSource): Total {
        val n = jdbc.queryForObject("select count(*) from (select 1 $base limit ${TOTAL_CAP + 1}) t", p, Long::class.java) ?: 0L
        return Total(minOf(n, TOTAL_CAP), n > TOTAL_CAP)
    }

    private data class Sort(val expr: String, val order: String, val ascending: Boolean, val type: String) {
        /** Timestamps travel as epoch microseconds (Postgres precision), so the cursor is exact. */
        val valueText: String
            get() = if (type == "timestamp") "(extract(epoch from $expr) * 1000000)::bigint::text" else "($expr)::text"
    }

    /** Adds the "after this row" condition for [cursor] and returns it. */
    private fun keyset(sort: Sort, cursor: String?, p: MapSqlParameterSource): String {
        if (cursor.isNullOrBlank()) return ""
        val (_, value, id) = decodeCursor(cursor)
        val cmp = if (sort.ascending) ">" else "<"
        p.addValue("cId", id)
        when (sort.type) {
            "timestamp" -> {
                val micros = value.toLong()
                p.addValue("cV", Timestamp.from(Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1000)))
            }
            "date" -> p.addValue("cV", Date.valueOf(value))
            else -> p.addValue("cV", value)
        }
        val idColumn = sort.order.substringAfterLast(",").trim().substringBefore(" ")
        return "and (${sort.expr}, $idColumn) $cmp (:cV, :cId)"
    }

    private fun encodeCursor(sort: String, value: String, id: UUID): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$sort\u0000$value\u0000$id".toByteArray())

    private fun decodeCursor(cursor: String): Triple<String, String, UUID> = runCatching {
        val parts = String(Base64.getUrlDecoder().decode(cursor)).split('\u0000')
        Triple(parts[0], parts[1], UUID.fromString(parts[2]))
    }.getOrElse { throw AdminConflictException("BAD_CURSOR", "This page link is no longer valid.") }

    private fun like(text: String) = "%" + text.take(100).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private fun startOf(day: LocalDate) = Timestamp.from(day.atStartOfDay(IST).toInstant())

    private fun ResultSet.uuid(column: String): UUID? = getObject(column) as UUID?

    private fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()

    private fun ResultSet.toMap(): Map<String, Any?> {
        val meta = metaData
        return (1..meta.columnCount).associate { i ->
            val value = getObject(i)
            meta.getColumnLabel(i) to when (value) {
                is Timestamp -> value.toInstant()
                is Date -> value.toLocalDate()
                else -> value
            }
        }
    }

    private fun camel(snake: String) = snake.split('_').mapIndexed { i, s -> if (i == 0) s else s.replaceFirstChar { it.uppercase() } }.joinToString("")

    companion object {
        const val MAX_PAGE = 100
        private const val SEARCH_LIMIT = 5
        private const val TOTAL_CAP = 1000L
        private const val LOOKUPS_PER_HOUR = 30L
        const val DELETED = "Deleted user"

        val LEAGUE_STATUSES = setOf("open", "auction_live", "auction_done", "completed")
        val USER_STATUSES = setOf("active", "profile_incomplete", "suspended", "deletion_pending")

        private const val LEAGUE_STATUS = """(case when l.completed_at is not null then 'completed'
            when l.auction_status = 'IN_PROGRESS' then 'auction_live'
            when l.auction_status = 'COMPLETED' then 'auction_done' else 'open' end)"""

        private val USER_STATUS = """(case when u.suspended_at is not null then 'suspended'
            when u.deletion_requested_at is not null then 'deletion_pending'
            when not (p.user_id is not null ${AdminMetricsService.PROFILE_DONE.replace("pr.", "p.")}) then 'profile_incomplete'
            else 'active' end)"""

        private val LEAGUE_SORTS = mapOf(
            "newest" to Sort("l.created_at", "l.created_at desc, l.id desc", false, "timestamp"),
            "starts" to Sort("l.starts_on", "l.starts_on asc, l.id asc", true, "date"),
            "name" to Sort("lower(l.name)", "lower(l.name) asc, l.id asc", true, "text"),
        )

        private val USER_SORTS = mapOf(
            "newest" to Sort("u.created_at", "u.created_at desc, u.id desc", false, "timestamp"),
            "name" to Sort("lower(coalesce(p.name, ''))", "lower(coalesce(p.name, '')) asc, u.id asc", true, "text"),
            "last_active" to Sort(
                "coalesce((select max(a.day) from user_activity_days a where a.user_id = u.id), date '1900-01-01')",
                "coalesce((select max(a.day) from user_activity_days a where a.user_id = u.id), date '1900-01-01') desc, u.id desc",
                false,
                "date",
            ),
        )

        private val HIDDEN_LEAGUE_COLUMNS = setOf("auction_current_player_id", "auction_last_action_type", "auction_last_action_bid_id", "auction_last_action_player_id")
    }
}
