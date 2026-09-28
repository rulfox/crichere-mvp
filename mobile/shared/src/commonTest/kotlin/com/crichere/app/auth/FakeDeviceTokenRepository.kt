package com.crichere.app.auth

import com.crichere.app.notification.DeviceTokenRepository

/** Call-recording [DeviceTokenRepository] test double -- see `AuthRepositoryTest`. */
class FakeDeviceTokenRepository : DeviceTokenRepository {
    val registerCalls = mutableListOf<Pair<String, String>>()
    val unregisterCalls = mutableListOf<String>()

    override suspend fun register(token: String, platform: String): Boolean {
        registerCalls += token to platform
        return true
    }

    override suspend fun unregister(token: String): Boolean {
        unregisterCalls += token
        return true
    }
}
