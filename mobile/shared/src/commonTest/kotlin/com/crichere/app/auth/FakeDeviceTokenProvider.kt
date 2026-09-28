package com.crichere.app.auth

import com.crichere.app.notification.DeviceTokenProvider

/** In-memory [DeviceTokenProvider] test double -- see `AuthRepositoryTest`. */
class FakeDeviceTokenProvider(private val token: String?) : DeviceTokenProvider {
    override suspend fun currentToken(): String? = token
}
