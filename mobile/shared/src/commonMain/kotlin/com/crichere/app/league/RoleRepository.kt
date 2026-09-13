package com.crichere.app.league

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Thrown by [RoleRepository]'s grant/revoke calls for anything other than a clean 2xx -- also lookup, but only for a non-404 failure (see [RoleRepository.lookup]'s own doc). */
class RoleActionFailedException(message: String) : Exception(message)

@Serializable
internal data class GrantRoleRequestDto(val userId: String)

@Serializable
internal data class PhoneNumberLookupRequestDto(val phoneNumber: String)

/** Co-organizer role delegation (docs/PHASE7.md). Follows [LeagueRepository]'s established interface+`Ktor*Impl` pattern. */
interface RoleRepository {

    /**
     * `null` means no registered user matches [phoneNumber] -- an expected outcome for the caller
     * to show as "no user found," not an error. Any other non-2xx (including the rate limit)
     * throws [RoleActionFailedException].
     */
    suspend fun lookup(leagueId: String, phoneNumber: String): RoleLookupResultDto?

    suspend fun grant(leagueId: String, userId: String): LeagueDto

    suspend fun revoke(leagueId: String, roleId: String): LeagueDto
}

internal class KtorRoleRepository(
    private val httpClient: HttpClient,
) : RoleRepository {

    override suspend fun lookup(leagueId: String, phoneNumber: String): RoleLookupResultDto? {
        val response = httpClient.post("/api/v1/leagues/$leagueId/roles/lookup") {
            contentType(ContentType.Application.Json)
            setBody(PhoneNumberLookupRequestDto(phoneNumber))
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) throw RoleActionFailedException("Lookup failed with status ${response.status}")
        return response.body()
    }

    override suspend fun grant(leagueId: String, userId: String): LeagueDto {
        val response = httpClient.post("/api/v1/leagues/$leagueId/roles") {
            contentType(ContentType.Application.Json)
            setBody(GrantRoleRequestDto(userId))
        }
        if (!response.status.isSuccess()) throw RoleActionFailedException("Grant failed with status ${response.status}")
        return response.body()
    }

    override suspend fun revoke(leagueId: String, roleId: String): LeagueDto {
        val response = httpClient.delete("/api/v1/leagues/$leagueId/roles/$roleId")
        if (!response.status.isSuccess()) throw RoleActionFailedException("Revoke failed with status ${response.status}")
        return response.body()
    }
}
