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

    /** No timeout -- a live auction can run for hours; the client/proxy decides when to give up, not us. */
    fun subscribe(leagueId: UUID, initialState: AuctionStateResponse): SseEmitter {
        val emitter = SseEmitter(0L)
        val list = emitters.computeIfAbsent(leagueId) { CopyOnWriteArrayList() }
        list.add(emitter)

        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter) }
        emitter.onError { list.remove(emitter) }

        send(emitter, initialState)
        return emitter
    }

    fun broadcast(leagueId: UUID, state: AuctionStateResponse) {
        val list = emitters[leagueId] ?: return
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

    /** Keeps connections alive through proxies/load balancers that would otherwise time out an idle stream. */
    @Scheduled(fixedRate = 15_000)
    fun heartbeat() {
        emitters.values.flatten().forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"))
            } catch (exception: Exception) {
                log.debug("Dropping a disconnected auction SSE emitter during heartbeat", exception)
                emitters.values.forEach { it.remove(emitter) }
            }
        }
    }
}
