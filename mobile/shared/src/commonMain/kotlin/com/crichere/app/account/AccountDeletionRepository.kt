package com.crichere.app.account

import com.crichere.app.network.problemCode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirrors the backend's `DeletionBlockReason` (docs/PHASE16.md). */
@Serializable
enum class DeletionBlockReason { AUCTION_LIVE, NO_CO_ORGANIZER }

/** One league that stops the account from being deleted right now (design D4). */
@Serializable
data class DeletionBlockerDto(
    val leagueId: String,
    val leagueName: String,
    val reason: DeletionBlockReason,
    val leagueLogoUrl: String? = null,
)

/** A league that passes to [newOrganizerName] when the account is erased (design D3b). */
@Serializable
data class DeletionHandoverDto(
    val leagueId: String,
    val leagueName: String,
    val leagueLogoUrl: String? = null,
    val newOrganizerUserId: String,
    val newOrganizerName: String? = null,
)

/** Mirrors `GET /api/v1/me/deletion`. [erasesOn] is an ISO-8601 instant. */
@Serializable
data class AccountDeletionStatusDto(
    val blockers: List<DeletionBlockerDto> = emptyList(),
    val gracePeriodDays: Int = 14,
    val erasesOn: String,
    val handovers: List<DeletionHandoverDto> = emptyList(),
)

@Serializable
internal data class AccountDeletionRequestDto(val confirm: String)

@Serializable
internal data class AccountDeletionScheduledDto(val purgeAfter: String)

@Serializable
private data class BlockedProblemDto(val blockers: List<DeletionBlockerDto> = emptyList())

/** Outcome of [AccountDeletionRepository.request]. */
sealed interface AccountDeletionResult {
    /** Accepted: every session is revoked; the account is erased at [erasesOn] unless the user signs in again. */
    data class Scheduled(val erasesOn: String) : AccountDeletionResult

    /** The server re-checked and something now blocks it (409 `ACCOUNT_DELETION_BLOCKED`). */
    data class Blocked(val blockers: List<DeletionBlockerDto>) : AccountDeletionResult
}

/** Thrown for a failed check or request that isn't a "blocked" answer -- network, 5xx, rate limit. */
class AccountDeletionFailedException(message: String, val code: String? = null) : Exception(message)

/** Account deletion (docs/PHASE16.md). Follows the interface + `Ktor*` pattern of the other repositories. */
interface AccountDeletionRepository {
    suspend fun status(): AccountDeletionStatusDto

    suspend fun request(): AccountDeletionResult
}

internal class KtorAccountDeletionRepository(
    private val httpClient: HttpClient,
) : AccountDeletionRepository {

    private val lenientJson = Json { ignoreUnknownKeys = true }

    override suspend fun status(): AccountDeletionStatusDto {
        val response = httpClient.get("/api/v1/me/deletion")
        if (!response.status.isSuccess()) throw AccountDeletionFailedException("Deletion check failed with status ${response.status}", response.problemCode())
        return response.body()
    }

    override suspend fun request(): AccountDeletionResult {
        val response = httpClient.post("/api/v1/me/deletion") {
            contentType(ContentType.Application.Json)
            setBody(AccountDeletionRequestDto(confirm = CONFIRMATION))
        }
        if (response.status == HttpStatusCode.Conflict) {
            val text = response.bodyAsText()
            val blockers = runCatching { lenientJson.decodeFromString<BlockedProblemDto>(text).blockers }.getOrDefault(emptyList())
            if (blockers.isNotEmpty()) return AccountDeletionResult.Blocked(blockers)
        }
        if (!response.status.isSuccess()) throw AccountDeletionFailedException("Deletion request failed with status ${response.status}", response.problemCode())
        return AccountDeletionResult.Scheduled(response.body<AccountDeletionScheduledDto>().purgeAfter)
    }

    companion object {
        /** The typed confirmation the backend requires; the dialog only enables Confirm once the user typed it. */
        const val CONFIRMATION = "DELETE"
    }
}
