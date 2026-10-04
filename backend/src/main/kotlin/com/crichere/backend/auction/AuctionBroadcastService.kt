package com.crichere.backend.auction

import com.crichere.backend.auction.dto.AuctionStateResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Broadcasts live auction state to every client connected to a league's SSE stream. In-process
 * registry, same `ConcurrentHashMap` concurrency primitive [com.crichere.backend.common.ContentRateLimiter]
 * already uses, and the same documented single-instance-deployment limitation -- move to a shared
 * pub/sub (Redis) if this API is ever scaled horizontally (see docs/PHASE5.md's Security section).
 */
@Service
class AuctionBroadcastService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    /**
     * The latest state sent for each league with a live stream -- what [heartbeat] re-sends. Always
     * current: every state change goes through [broadcast], and a fresh subscriber's own state seeds it.
     */
    private val lastState = ConcurrentHashMap<UUID, AuctionStateResponse>()

    /** Overridable so a test can capture what is sent. */
    internal open fun createEmitter(): SseEmitter = SseEmitter(0L)

    /** No timeout -- a live auction can run for hours; the client/proxy decides when to give up, not us. */
    fun subscribe(leagueId: UUID, initialState: AuctionStateResponse): SseEmitter {
        val emitter = createEmitter()
        val list = emitters.computeIfAbsent(leagueId) { CopyOnWriteArrayList() }
        list.add(emitter)

        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter) }
        emitter.onError { list.remove(emitter) }

        lastState[leagueId] = initialState
        send(emitter, initialState)
        return emitter
    }

    fun broadcast(leagueId: UUID, state: AuctionStateResponse) {
        val list = emitters[leagueId] ?: return
        lastState[leagueId] = state
        list.forEach { send(it, state) }
    }

    private fun send(emitter: SseEmitter, state: AuctionStateResponse) {
        try {
            emitter.send(SseEmitter.event().name("auction-state").data(state, MediaType.APPLICATION_JSON))
        } catch (exception: Exception) {
            log.debug("Dropping a disconnected auction SSE emitter", exception)
            emitters.values.forEach { it.remove(emitter) }
        }
    }

    /**
     * Every 15s re-sends each league's latest state to its subscribers. That keeps the connection alive
     * through proxies and load balancers like a comment line would -- and unlike a comment, it reaches
     * the client as a real `auction-state` event, which is how the mobile app tells a quiet auction from
     * a dead connection (its SSE client does not surface comment-only frames). Clients already treat a
     * repeated state as a no-op, so nothing visible changes.
     */
    @Scheduled(fixedRate = 15_000)
    fun heartbeat() {
        emitters.forEach { (leagueId, list) ->
            if (list.isEmpty()) {
                // The (empty) list stays in the map: removing it could race with a subscriber about to add to it.
                lastState.remove(leagueId)
                return@forEach
            }
            val state = lastState[leagueId]
            list.forEach { emitter ->
                if (state != null) send(emitter, state) else sendKeepAliveComment(emitter)
            }
        }
    }

    private fun sendKeepAliveComment(emitter: SseEmitter) {
        try {
            emitter.send(SseEmitter.event().comment("keep-alive"))
        } catch (exception: Exception) {
            log.debug("Dropping a disconnected auction SSE emitter during heartbeat", exception)
            emitters.values.forEach { it.remove(emitter) }
        }
    }
}
