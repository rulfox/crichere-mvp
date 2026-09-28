package com.crichere.app.notification

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
internal data class RegisterDeviceTokenRequestDto(val token: String, val platform: String)

@Serializable
internal data class UnregisterDeviceTokenRequestDto(val token: String)

/**
 * `commonMain` use cases for `POST /me/device-tokens[/unregister]` (see docs/PHASE8.md). Both
 * calls are deliberately best-effort from every caller's side (see [com.crichere.app.auth.AuthRepository])
 * -- a failed registration/unregistration is never allowed to block sign-in or sign-out, so this
 * interface reports success/failure via the return value rather than throwing.
 */
interface DeviceTokenRepository {
    /** `true` on a clean 2xx. */
    suspend fun register(token: String, platform: String): Boolean

    /** `true` on a clean 2xx. */
    suspend fun unregister(token: String): Boolean
}

internal class KtorDeviceTokenRepository(
    private val httpClient: HttpClient,
) : DeviceTokenRepository {

    override suspend fun register(token: String, platform: String): Boolean =
        runCatching {
            httpClient.post("/api/v1/me/device-tokens") {
                contentType(ContentType.Application.Json)
                setBody(RegisterDeviceTokenRequestDto(token, platform))
            }.status.value in 200..299
        }.getOrDefault(false)

    override suspend fun unregister(token: String): Boolean =
        runCatching {
            httpClient.post("/api/v1/me/device-tokens/unregister") {
                contentType(ContentType.Application.Json)
                setBody(UnregisterDeviceTokenRequestDto(token))
            }.status.value in 200..299
        }.getOrDefault(false)
}
