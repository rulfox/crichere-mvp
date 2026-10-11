package com.crichere.app.account

/** In-memory [AccountDeletionRepository] test double. */
class FakeAccountDeletionRepository(
    var nextStatus: () -> AccountDeletionStatusDto = { error("nextStatus not stubbed") },
    var nextRequest: () -> AccountDeletionResult = { error("nextRequest not stubbed") },
) : AccountDeletionRepository {

    var statusCallCount = 0
        private set
    var requestCallCount = 0
        private set

    override suspend fun status(): AccountDeletionStatusDto {
        statusCallCount++
        return nextStatus()
    }

    override suspend fun request(): AccountDeletionResult {
        requestCallCount++
        return nextRequest()
    }
}
