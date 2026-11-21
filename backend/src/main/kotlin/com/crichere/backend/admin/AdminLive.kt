package com.crichere.backend.admin

import com.crichere.backend.auction.AuctionBroadcastService
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/*
 * Live data and activity for the admin web (docs/PHASE20.md section 4, Step 6). Everything here is
 * in memory on the one backend instance: viewing the dashboard never queries the database. Moving
 * to several instances needs a shared store (section 4, "Limit").
 */

val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/**
 * Something that happened in the app, published by the service that did it and counted only after
 * its transaction commits. [actorId] is the account behind it; internal accounts are skipped.
 */
sealed interface LiveEvent {
    val actorId: UUID?

    data class SignedUp(override val actorId: UUID) : LiveEvent
    data class OtpSent(override val actorId: UUID? = null) : LiveEvent
    data class LeagueCreated(override val actorId: UUID, val district: String?) : LiveEvent
    data class PlayerJoined(override val actorId: UUID, val district: String?) : LiveEvent
    data class ReportFiled(override val actorId: UUID) : LiveEvent
    data class BidPlaced(override val actorId: UUID) : LiveEvent

    /** Any auction state save; [live] is whether it is now IN_PROGRESS. */
    data class AuctionState(override val actorId: UUID, val leagueId: UUID, val live: Boolean, val completed: Boolean, val district: String?) : LiveEvent
}

/** Published by [AdminSessionService] on sign-out, so open live streams of those sessions close at once. */
data class AdminSessionsEnded(val sessionId: UUID? = null, val email: String? = null)

/** One feed line: what happened and where, never who. */
data class FeedItem(val type: String, val district: String?, val at: Instant)

data class TodayCounts(
    val signUps: Long,
    val leagues: Long,
    val joins: Long,
    val reports: Long,
    val otpSends: Long,
    val bids: Long,
    val activeUsers: Long,
)

data class LiveNow(val liveAuctions: Int, val bidsLastMinute: Int, val auctionViewers: Int)

data class LiveSnapshot(val day: LocalDate, val today: TodayCounts, val now: LiveNow, val feed: List<FeedItem>)

// ---------------------------------------------------------------------------------------- activity

/**
 * Which accounts used the app today (decided 2026-11-21: any successful signed-in app request).
 * Ids are kept in memory and written as one `INSERT ... ON CONFLICT DO NOTHING` every 5 minutes,
 * so a busy day costs a handful of writes. Only the day is stored, never what was done.
 */
@Component
class ActivityTracker(private val jdbc: NamedParameterJdbcTemplate, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile private var day: LocalDate = LocalDate.now(clock.withZone(IST))
    @Volatile private var seenToday: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val pending = ConcurrentHashMap<LocalDate, MutableSet<UUID>>()

    fun markActive(userId: UUID) {
        val today = rollIfNewDay()
        if (seenToday.add(userId)) pending.computeIfAbsent(today) { ConcurrentHashMap.newKeySet() }.add(userId)
    }

    /** Snapshot of today's active ids (the live counter filters internal ones out). */
    fun todayIds(): Set<UUID> {
        rollIfNewDay()
        return seenToday
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    fun scheduledFlush() = flush()

    @PreDestroy
    fun flush() {
        pending.keys.forEach { day ->
            val ids = pending.remove(day)?.toList().orEmpty()
            if (ids.isEmpty()) return@forEach
            try {
                // Joined to users so an id erased in the meantime is skipped instead of failing the batch.
                jdbc.update(
                    """
                    insert into user_activity_days (user_id, day)
                    select u.id, :day from users u where u.id in (:ids)
                    on conflict do nothing
                    """,
                    MapSqlParameterSource("day", day).addValue("ids", ids),
                )
            } catch (e: Exception) {
                log.error("Activity flush failed for {} ids on {}; will retry", ids.size, day, e)
                pending.computeIfAbsent(day) { ConcurrentHashMap.newKeySet() }.addAll(ids)
            }
        }
    }

    /** Account erase: the rows and the in-memory trace (docs/PHASE20.md Step 5 follow-up). */
    fun erase(userId: UUID) {
        seenToday.remove(userId)
        pending.values.forEach { it.remove(userId) }
        jdbc.update("delete from user_activity_days where user_id = :id", MapSqlParameterSource("id", userId))
    }

    /** After a restart, today's ids come back from the table so the active count doesn't drop. */
    @EventListener(ApplicationReadyEvent::class)
    fun reload() {
        val today = rollIfNewDay()
        val ids = jdbc.queryForList(
            "select user_id from user_activity_days where day = :day",
            MapSqlParameterSource("day", today),
            UUID::class.java,
        ).filterNotNull()
        seenToday.addAll(ids)
    }

    private fun rollIfNewDay(): LocalDate {
        val today = LocalDate.now(clock.withZone(IST))
        if (today != day) {
            synchronized(this) {
                if (today != day) {
                    seenToday = ConcurrentHashMap.newKeySet()
                    day = today
                }
            }
        }
        return today
    }
}

// ---------------------------------------------------------------------------------------- counters

/**
 * Today's counters since 00:00 IST, the last 60 seconds of bids, live auctions and a ring buffer of
 * the last [FEED_SIZE] events. Bumped after commit, reseeded by one query per table at startup and at
 * midnight. Internal accounts (users.is_internal) are left out, matching the nightly history.
 */
@Component
class LiveMetrics(
    private val jdbc: NamedParameterJdbcTemplate,
    private val activity: ActivityTracker,
    private val broadcasts: AuctionBroadcastService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile private var day: LocalDate = LocalDate.now(clock.withZone(IST))
    private val signUps = AtomicLong()
    private val leagues = AtomicLong()
    private val joins = AtomicLong()
    private val reports = AtomicLong()
    private val otpSends = AtomicLong()
    private val bids = AtomicLong()
    private val recentBids = ConcurrentLinkedDeque<Instant>()
    private val liveAuctions: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val feed = ArrayDeque<FeedItem>()

    @Volatile private var internalIds: Set<UUID> = emptySet()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: LiveEvent) {
        try {
            record(event)
        } catch (e: Exception) {
            // Counting must never disturb the request that caused it.
            log.warn("Live metrics could not count {}", event::class.simpleName, e)
        }
    }

    private fun record(event: LiveEvent) {
        rollIfNewDay()
        if (event.actorId != null && event.actorId in internalIds) return
        val now = clock.instant()
        when (event) {
            is LiveEvent.SignedUp -> { signUps.incrementAndGet(); feed("SIGN_UP", null, now) }
            is LiveEvent.OtpSent -> otpSends.incrementAndGet()
            is LiveEvent.LeagueCreated -> { leagues.incrementAndGet(); feed("LEAGUE_CREATED", event.district, now) }
            is LiveEvent.PlayerJoined -> { joins.incrementAndGet(); feed("PLAYER_JOINED", event.district, now) }
            is LiveEvent.ReportFiled -> { reports.incrementAndGet(); feed("REPORT_FILED", null, now) }
            is LiveEvent.BidPlaced -> {
                bids.incrementAndGet()
                recentBids.addLast(now)
                while (recentBids.size > MAX_RECENT_BIDS) recentBids.pollFirst()
            }
            is LiveEvent.AuctionState ->
                if (event.live) {
                    if (liveAuctions.add(event.leagueId)) feed("AUCTION_STARTED", event.district, now)
                } else if (liveAuctions.remove(event.leagueId) && event.completed) {
                    feed("AUCTION_FINISHED", event.district, now)
                }
        }
    }

    private fun feed(type: String, district: String?, at: Instant) = synchronized(feed) {
        feed.addFirst(FeedItem(type, district, at))
        while (feed.size > FEED_SIZE) feed.removeLast()
    }

    fun snapshot(): LiveSnapshot {
        rollIfNewDay()
        val cutoff = clock.instant().minusSeconds(60)
        while (recentBids.peekFirst()?.isBefore(cutoff) == true) recentBids.pollFirst()
        val internal = internalIds
        return LiveSnapshot(
            day = day,
            today = TodayCounts(
                signUps = signUps.get(),
                leagues = leagues.get(),
                joins = joins.get(),
                reports = reports.get(),
                otpSends = otpSends.get(),
                bids = bids.get(),
                activeUsers = activity.todayIds().count { it !in internal }.toLong(),
            ),
            now = LiveNow(liveAuctions.size, recentBids.size, broadcasts.subscriberCount()),
            feed = synchronized(feed) { feed.toList() },
        )
    }

    /** Step 9's internal flag calls this so today's numbers follow at once. */
    fun setInternal(userId: UUID, internal: Boolean) {
        internalIds = if (internal) internalIds + userId else internalIds - userId
    }

    @EventListener(ApplicationReadyEvent::class)
    fun reseedOnStart() = reseed()

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    fun reseedAtMidnight() = reseed()

    /** One count per table since 00:00 IST; a few cheap index reads, twice a day at most. */
    fun reseed() {
        val today = LocalDate.now(clock.withZone(IST))
        val since = Timestamp.from(today.atStartOfDay(IST).toInstant())
        val p = MapSqlParameterSource("since", since)
        fun count(sql: String): Long = jdbc.queryForObject(sql, p, Long::class.java) ?: 0L

        internalIds = jdbc.queryForList("select id from users where is_internal", p, UUID::class.java).filterNotNull().toSet()
        signUps.set(count("select count(*) from users where created_at >= :since and not is_internal"))
        leagues.set(
            count("select count(*) from leagues l join users u on u.id = l.organizer_user_id where l.created_at >= :since and not u.is_internal"),
        )
        joins.set(
            count("select count(*) from league_players p join users u on u.id = p.user_id where p.joined_at >= :since and not u.is_internal"),
        )
        reports.set(
            count(
                "select count(*) from reports r left join users u on u.id = r.reporter_id " +
                    "where r.created_at >= :since and not coalesce(u.is_internal, false)",
            ),
        )
        otpSends.set(count("select count(*) from otp_challenges where created_at >= :since"))
        bids.set(
            count(
                "select count(*) from auction_bids b join league_franchises f on f.id = b.franchise_id " +
                    "join users u on u.id = f.owner_user_id where b.placed_at >= :since and not u.is_internal",
            ),
        )
        val live = jdbc.queryForList(
            "select l.id from leagues l join users u on u.id = l.organizer_user_id where l.auction_status = 'IN_PROGRESS' and not u.is_internal",
            p,
            UUID::class.java,
        ).filterNotNull()
        liveAuctions.clear()
        liveAuctions.addAll(live)
        day = today
    }

    private fun rollIfNewDay() {
        val today = LocalDate.now(clock.withZone(IST))
        if (today != day) {
            synchronized(this) {
                if (today != day) {
                    listOf(signUps, leagues, joins, reports, otpSends, bids).forEach { it.set(0) }
                    day = today
                }
            }
        }
    }

    companion object {
        const val FEED_SIZE = 50
        private const val MAX_RECENT_BIDS = 10_000
    }
}

// ---------------------------------------------------------------------------------------- stream

/**
 * `GET /api/v1/admin/live`: a snapshot every 5 s, only when it changed and only while someone is
 * watching. Each stream ends with its session: at expiry, or at once on sign-out.
 */
@Component
class AdminLiveStream(private val metrics: LiveMetrics, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Subscriber(val emitter: SseEmitter, val principal: AdminPrincipal)

    private val subscribers = CopyOnWriteArrayList<Subscriber>()

    @Volatile private var lastSent: LiveSnapshot? = null
    private var quietTicks = 0

    private fun createEmitter(timeoutMillis: Long): SseEmitter = SseEmitter(timeoutMillis)

    /** @return null when this session already has [MAX_PER_SESSION] streams open. */
    fun subscribe(principal: AdminPrincipal): SseEmitter? {
        if (subscribers.count { it.principal.sessionId == principal.sessionId } >= MAX_PER_SESSION) return null
        val remaining = Duration.between(clock.instant(), principal.expiresAt).toMillis().coerceAtLeast(1)
        val emitter = createEmitter(remaining)
        val subscriber = Subscriber(emitter, principal)
        subscribers += subscriber
        emitter.onCompletion { subscribers.remove(subscriber) }
        emitter.onTimeout { subscribers.remove(subscriber); emitter.complete() }
        emitter.onError { subscribers.remove(subscriber) }
        send(subscriber, metrics.snapshot())
        return emitter
    }

    fun subscriberCount(): Int = subscribers.size

    @Scheduled(fixedRate = 5_000)
    fun tick() {
        if (subscribers.isEmpty()) {
            lastSent = null
            return
        }
        val now = clock.instant()
        subscribers.filter { !it.principal.expiresAt.isAfter(now) }.forEach { end(it) }
        val snapshot = metrics.snapshot()
        if (snapshot != lastSent) {
            quietTicks = 0
            lastSent = snapshot
            subscribers.forEach { send(it, snapshot) }
        } else if (++quietTicks >= KEEP_ALIVE_TICKS) {
            // Keeps proxies from closing a quiet stream, and tells the site the stream is alive.
            quietTicks = 0
            subscribers.forEach { keepAlive(it) }
        }
    }

    @EventListener
    fun onSessionsEnded(event: AdminSessionsEnded) {
        subscribers.filter {
            (event.sessionId != null && it.principal.sessionId == event.sessionId) || (event.email != null && it.principal.email == event.email)
        }.forEach { end(it) }
    }

    private fun end(subscriber: Subscriber) {
        subscribers.remove(subscriber)
        runCatching { subscriber.emitter.complete() }
    }

    private fun send(subscriber: Subscriber, snapshot: LiveSnapshot) {
        try {
            subscriber.emitter.send(SseEmitter.event().name("snapshot").data(snapshot, MediaType.APPLICATION_JSON))
        } catch (e: Exception) {
            log.debug("Dropping a disconnected admin live stream", e)
            subscribers.remove(subscriber)
        }
    }

    private fun keepAlive(subscriber: Subscriber) {
        try {
            // A named event, not a comment: EventSource never surfaces comments, and the admin site
            // marks the stream stale after 15 s without any event.
            subscriber.emitter.send(SseEmitter.event().name("ping").data(clock.instant().toString()))
        } catch (e: Exception) {
            subscribers.remove(subscriber)
        }
    }

    companion object {
        const val MAX_PER_SESSION = 3
        private const val KEEP_ALIVE_TICKS = 2
    }
}

@RestController
class AdminLiveController(private val stream: AdminLiveStream) {
    @GetMapping("/api/v1/admin/live", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun live(@AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<SseEmitter> {
        val emitter = stream.subscribe(admin) ?: return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header("X-Accel-Buffering", "no")
            .body(emitter)
    }
}
