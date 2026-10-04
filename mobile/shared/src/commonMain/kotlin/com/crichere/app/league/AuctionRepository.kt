package com.crichere.app.league

import com.crichere.app.network.problemCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/** Thrown by every mutating [AuctionRepository] call for anything other than a clean 2xx -- same posture as [LeagueSaveFailedException]. */
class AuctionActionFailedException(message: String, val code: String? = null) : Exception(message)

/**
 * `commonMain` live-auction use cases (see docs/PHASE5.md) -- follows [LeagueRepository]'s
 * established interface+`Ktor*Impl` pattern. `results` is public; every mutation needs the
 * default authenticated client (bid placement is franchise-owner-scoped server-side, every other
 * mutation is organizer-scoped server-side -- this repository doesn't duplicate those checks,
 * same posture as every other write in this app).
 */
interface AuctionRepository {
    suspend fun start(leagueId: String): AuctionStateDto

    suspend fun nextPlayer(leagueId: String): AuctionStateDto

    suspend fun placeBid(leagueId: String, request: PlaceBidRequestDto): AuctionStateDto

    suspend fun sold(leagueId: String): AuctionStateDto

    suspend fun unsold(leagueId: String): AuctionStateDto

    suspend fun undo(leagueId: String): AuctionStateDto

    suspend fun toggleExceedPurse(leagueId: String, allow: Boolean): AuctionStateDto

    suspend fun end(leagueId: String): AuctionStateDto

    suspend fun getResults(leagueId: String): AuctionResultsDto

    /**
     * `GET /leagues/{id}/auction/stream` -- an unbounded flow of every state change (see
     * docs/PHASE5.md). Collecting it suspends for the stream's lifetime; cancel the collecting
     * coroutine (e.g. `viewModelScope` clearing) to disconnect.
     */
    fun streamAuctionState(leagueId: String): Flow<AuctionStateDto>
}

/**
 * How long the stream may stay silent before it is treated as dead. The server re-sends the current state
 * every 15s (`AuctionBroadcastService.heartbeat`), so 40s of silence means the connection is gone (signal
 * lost, NAT dropped) even though the socket never errored; the flow then fails and the ViewModel reconnects.
 * A comment-only keep-alive wouldn't do: the SSE client doesn't surface those.
 */
private val STREAM_IDLE_TIMEOUT = 40.seconds

internal class KtorAuctionRepository(private val httpClient: HttpClient) : AuctionRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun start(leagueId: String): AuctionStateDto = postAction(leagueId, "start")

    override suspend fun nextPlayer(leagueId: String): AuctionStateDto = postAction(leagueId, "next-player")

    override suspend fun placeBid(leagueId: String, request: PlaceBidRequestDto): AuctionStateDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/auction/bids") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) throw AuctionActionFailedException("Bid failed with status ${response.status}", response.problemCode())
        return response.body()
    }

    override suspend fun sold(leagueId: String): AuctionStateDto = postAction(leagueId, "sold")

    override suspend fun unsold(leagueId: String): AuctionStateDto = postAction(leagueId, "unsold")

    override suspend fun undo(leagueId: String): AuctionStateDto = postAction(leagueId, "undo")

    override suspend fun toggleExceedPurse(leagueId: String, allow: Boolean): AuctionStateDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/auction/toggle-exceed-purse") {
            contentType(ContentType.Application.Json)
            setBody(ToggleExceedPurseRequestDto(allow))
        }
        if (!response.status.isSuccess()) throw AuctionActionFailedException("Toggle exceed-purse failed with status ${response.status}", response.problemCode())
        return response.body()
    }

    override suspend fun end(leagueId: String): AuctionStateDto = postAction(leagueId, "end")

    override suspend fun getResults(leagueId: String): AuctionResultsDto =
        httpClient.get("/api/v1/leagues/$leagueId/auction/results").body()

    @OptIn(FlowPreview::class)
    override fun streamAuctionState(leagueId: String): Flow<AuctionStateDto> = flow {
        httpClient.sse(
            "/api/v1/leagues/$leagueId/auction/stream",
            // The client-wide 15s request timeout (HttpClientFactory) would cut a stream that is meant
            // to stay open for the whole auction, so this request has none.
            request = {
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            },
        ) {
            incoming.timeout(STREAM_IDLE_TIMEOUT).collect { event ->
                event.data?.let { data -> emit(json.decodeFromString(AuctionStateDto.serializer(), data)) }
            }
        }
    }

    private suspend fun postAction(leagueId: String, action: String): AuctionStateDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/auction/$action")
        if (!response.status.isSuccess()) throw AuctionActionFailedException("$action failed with status ${response.status}", response.problemCode())
        return response.body()
    }
}
