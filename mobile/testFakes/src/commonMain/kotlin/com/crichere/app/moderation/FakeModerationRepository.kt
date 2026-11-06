package com.crichere.app.moderation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [ModerationRepository] test double. */
class FakeModerationRepository(
    var nextReport: () -> ReportResult = { ReportResult.Sent },
    var nextBlock: () -> Result<Unit> = { Result.success(Unit) },
    var nextUnblock: () -> Result<Unit> = { Result.success(Unit) },
    var nextBlocks: () -> Result<List<BlockedPersonDto>> = { Result.success(emptyList()) },
    var nextNotices: () -> Result<List<ModerationNoticeDto>> = { Result.success(emptyList()) },
) : ModerationRepository {

    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    override val blockedIds: StateFlow<Set<String>> = _blockedIds

    val reports = mutableListOf<ReportRequest>()
    val dismissed = mutableListOf<String>()

    override suspend fun refreshBlocks(): Result<List<BlockedPersonDto>> =
        nextBlocks().onSuccess { people -> _blockedIds.value = people.map { it.userId }.toSet() }

    override suspend fun block(userId: String): Result<Unit> =
        nextBlock().onSuccess { _blockedIds.value = _blockedIds.value + userId }

    override suspend fun unblock(userId: String): Result<Unit> =
        nextUnblock().onSuccess { _blockedIds.value = _blockedIds.value - userId }

    override suspend fun report(request: ReportRequest): ReportResult {
        reports += request
        return nextReport()
    }

    override suspend fun pendingNotices(): Result<List<ModerationNoticeDto>> = nextNotices()

    override suspend fun dismissNotice(id: String) {
        dismissed += id
    }

    override fun clear() {
        _blockedIds.value = emptySet()
    }
}
