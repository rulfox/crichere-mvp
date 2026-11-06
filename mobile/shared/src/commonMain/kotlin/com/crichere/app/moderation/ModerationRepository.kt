package com.crichere.app.moderation

import com.crichere.app.network.problemCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** Mirrors the backend's `ReportTargetType` (docs/PHASE18.md). */
@Serializable
enum class ReportTargetType { USER, LEAGUE }

/** Mirrors the backend's `ReportReason`: the rows on boards R4a and R4b. */
@Serializable
enum class ReportReason {
    INAPPROPRIATE_IMAGE,
    HATE_OR_HARASSMENT,
    IMPERSONATION,
    UNDER_18,
    SPAM_OR_MISLEADING,
    BETTING_OR_GAMBLING,
    FAKE_OR_SCAM_LEAGUE,
    SOMETHING_ELSE,
}

@Serializable
data class ReportRequest(
    val targetType: ReportTargetType,
    val targetUserId: String? = null,
    val targetLeagueId: String? = null,
    val contextLeagueId: String? = null,
    val reason: ReportReason,
    val details: String? = null,
)

@Serializable
data class BlockedPersonDto(val userId: String, val name: String? = null, val blockedAt: String)

/** Mirrors the backend's `ModerationNoticeKind`: boards R12b to R12e. */
@Serializable
enum class ModerationNoticeKind { PHOTO, LEAGUE_LOGO, LEAGUE_BANNER, FRANCHISE_LOGO }

@Serializable
data class ModerationNoticeDto(val id: String, val kind: ModerationNoticeKind, val subjectName: String? = null)

sealed interface ReportResult {
    data object Sent : ReportResult
    /** Board R6b: an open report from this user on this target already exists. */
    data object AlreadyReported : ReportResult
    data object Failed : ReportResult
}

/**
 * Reports, blocks and removed-image notices. [blockedIds] is what every screen hides against: the
 * auction stream is the same for all viewers, so hiding has to happen here on the client.
 */
interface ModerationRepository {
    val blockedIds: StateFlow<Set<String>>

    /** Loads the block list. Called after sign in; a failure keeps the last known set. */
    suspend fun refreshBlocks(): Result<List<BlockedPersonDto>>

    suspend fun block(userId: String): Result<Unit>

    suspend fun unblock(userId: String): Result<Unit>

    suspend fun report(request: ReportRequest): ReportResult

    suspend fun pendingNotices(): Result<List<ModerationNoticeDto>>

    suspend fun dismissNotice(id: String)

    /** Sign-out: the next account's list must not start from this one's. */
    fun clear()
}

class KtorModerationRepository(private val httpClient: HttpClient) : ModerationRepository {

    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    override suspend fun refreshBlocks(): Result<List<BlockedPersonDto>> = attempt {
        val response = httpClient.get("/api/v1/blocks")
        check(response.status.isSuccess()) { "Blocks failed with ${response.status}" }
        val people: List<BlockedPersonDto> = response.body()
        _blockedIds.value = people.map { it.userId }.toSet()
        people
    }

    override suspend fun block(userId: String): Result<Unit> = attempt {
        val response = httpClient.put("/api/v1/blocks/$userId")
        check(response.status.isSuccess()) { "Block failed with ${response.status}" }
        _blockedIds.update { it + userId }
    }

    override suspend fun unblock(userId: String): Result<Unit> = attempt {
        val response = httpClient.delete("/api/v1/blocks/$userId")
        check(response.status.isSuccess()) { "Unblock failed with ${response.status}" }
        _blockedIds.update { it - userId }
    }

    override suspend fun report(request: ReportRequest): ReportResult =
        attempt {
            val response = httpClient.post("/api/v1/reports") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when {
                response.status.isSuccess() -> ReportResult.Sent
                response.problemCode() == "REPORT_ALREADY_OPEN" -> ReportResult.AlreadyReported
                else -> ReportResult.Failed
            }
        }.getOrDefault(ReportResult.Failed)

    override suspend fun pendingNotices(): Result<List<ModerationNoticeDto>> = attempt {
        val response = httpClient.get("/api/v1/me/moderation-notices")
        check(response.status.isSuccess()) { "Notices failed with ${response.status}" }
        response.body()
    }

    override suspend fun dismissNotice(id: String) {
        attempt { httpClient.post("/api/v1/me/moderation-notices/$id/dismiss") }
    }

    override fun clear() {
        _blockedIds.value = emptySet()
    }

    private inline fun <T> attempt(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
