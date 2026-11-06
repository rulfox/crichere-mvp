package com.crichere.app.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who or what a report is about. [name] is shown at the top of the sheet so nobody reports the wrong row. */
data class ReportSubject(
    val type: ReportTargetType,
    val id: String,
    val name: String,
    /** "Player in Kochi Premier League" (board R4a). */
    val context: String,
    val contextLeagueId: String? = null,
)

enum class ReportStep { REASON, DETAILS, SENT, ALREADY_REPORTED }

data class ReportState(
    val subject: ReportSubject? = null,
    val step: ReportStep = ReportStep.REASON,
    val reason: ReportReason? = null,
    val details: String = "",
    val alsoBlock: Boolean = false,
    val isSubmitting: Boolean = false,
    val failed: Boolean = false,
) {
    val reasons: List<ReportReason>
        get() = if (subject?.type == ReportTargetType.LEAGUE) LEAGUE_REASONS else PERSON_REASONS
    val detailsRequired: Boolean get() = reason == ReportReason.SOMETHING_ELSE
    val canSend: Boolean get() = !isSubmitting && reason != null && (!detailsRequired || details.isNotBlank())

    companion object {
        const val MAX_DETAILS = 500
        val PERSON_REASONS = listOf(
            ReportReason.INAPPROPRIATE_IMAGE,
            ReportReason.HATE_OR_HARASSMENT,
            ReportReason.IMPERSONATION,
            ReportReason.UNDER_18,
            ReportReason.SPAM_OR_MISLEADING,
            ReportReason.SOMETHING_ELSE,
        )
        val LEAGUE_REASONS = listOf(
            ReportReason.INAPPROPRIATE_IMAGE,
            ReportReason.HATE_OR_HARASSMENT,
            ReportReason.BETTING_OR_GAMBLING,
            ReportReason.FAKE_OR_SCAM_LEAGUE,
            ReportReason.SOMETHING_ELSE,
        )
    }
}

/** The report sheet, boards R4 to R6. The draft survives a failed send (R5f). */
class ReportViewModel(private val repository: ModerationRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    fun open(subject: ReportSubject) {
        _state.value = ReportState(subject = subject)
    }

    fun close() {
        if (!_state.value.isSubmitting) _state.value = ReportState()
    }

    fun selectReason(reason: ReportReason) {
        _state.update { it.copy(reason = reason) }
    }

    fun next() {
        _state.update { if (it.reason != null) it.copy(step = ReportStep.DETAILS) else it }
    }

    fun back() {
        _state.update { if (it.isSubmitting) it else it.copy(step = ReportStep.REASON, failed = false) }
    }

    /** Input stops at the limit; a paste is trimmed to fit (board R5d). */
    fun onDetailsChanged(value: String) {
        _state.update { it.copy(details = value.take(ReportState.MAX_DETAILS), failed = false) }
    }

    fun setAlsoBlock(value: Boolean) {
        _state.update { it.copy(alsoBlock = value) }
    }

    fun send() {
        val current = _state.value
        val subject = current.subject ?: return
        val reason = current.reason ?: return
        if (!current.canSend) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, failed = false) }
            val result = repository.report(
                ReportRequest(
                    targetType = subject.type,
                    targetUserId = subject.id.takeIf { subject.type == ReportTargetType.USER },
                    targetLeagueId = subject.id.takeIf { subject.type == ReportTargetType.LEAGUE },
                    contextLeagueId = subject.contextLeagueId,
                    reason = reason,
                    details = current.details.trim().ifBlank { null },
                ),
            )
            when (result) {
                ReportResult.Sent -> {
                    if (current.alsoBlock) repository.block(subject.id)
                    _state.update { it.copy(isSubmitting = false, step = ReportStep.SENT) }
                }
                ReportResult.AlreadyReported ->
                    _state.update { it.copy(isSubmitting = false, step = ReportStep.ALREADY_REPORTED) }
                ReportResult.Failed -> _state.update { it.copy(isSubmitting = false, failed = true) }
            }
        }
    }
}

data class BlockTarget(val userId: String, val name: String)

data class BlockState(
    val confirming: BlockTarget? = null,
    val isSubmitting: Boolean = false,
    val failed: Boolean = false,
    /** Set after a block or unblock for the snackbar; cleared by [BlockViewModel.dismissSnackbar]. */
    val justBlocked: BlockTarget? = null,
    val justUnblocked: BlockTarget? = null,
)

/** The block dialog and its Undo snackbar, boards R7a to R7d, plus Unblock from a row (R2b). */
class BlockViewModel(private val repository: ModerationRepository) : ViewModel() {

    val blockedIds: StateFlow<Set<String>> = repository.blockedIds

    private val _state = MutableStateFlow(BlockState())
    val state: StateFlow<BlockState> = _state.asStateFlow()

    fun askToBlock(target: BlockTarget) {
        _state.update { it.copy(confirming = target, failed = false, justBlocked = null, justUnblocked = null) }
    }

    fun cancel() {
        _state.update { if (it.isSubmitting) it else it.copy(confirming = null, failed = false) }
    }

    fun confirm() {
        val target = _state.value.confirming ?: return
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, failed = false) }
            repository.block(target.userId)
                .onSuccess { _state.update { it.copy(isSubmitting = false, confirming = null, justBlocked = target) } }
                .onFailure { _state.update { it.copy(isSubmitting = false, failed = true) } }
        }
    }

    /** No dialog: unblocking isn't destructive (board R2b). */
    fun unblock(target: BlockTarget) {
        viewModelScope.launch {
            repository.unblock(target.userId)
                .onSuccess { _state.update { it.copy(justUnblocked = target, justBlocked = null) } }
        }
    }

    /** Undo after a block: silent, no second snackbar (board R7d). */
    fun undoBlock() {
        val target = _state.value.justBlocked ?: return
        _state.update { it.copy(justBlocked = null) }
        viewModelScope.launch { repository.unblock(target.userId) }
    }

    fun undoUnblock() {
        val target = _state.value.justUnblocked ?: return
        _state.update { it.copy(justUnblocked = null) }
        viewModelScope.launch { repository.block(target.userId) }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(justBlocked = null, justUnblocked = null) }
    }
}

data class BlockedRow(
    val person: BlockedPersonDto,
    val isUnblocking: Boolean = false,
    val failed: Boolean = false,
)

data class BlockedPeopleState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val rows: List<BlockedRow> = emptyList(),
    /** The last unblocked person, for the Undo snackbar (board R11g). */
    val justUnblocked: BlockedPersonDto? = null,
)

/** Blocked people, boards R11a to R11g. Rows fail on their own, and don't queue behind each other. */
class BlockedPeopleViewModel(private val repository: ModerationRepository) : ViewModel() {

    private val _state = MutableStateFlow(BlockedPeopleState())
    val state: StateFlow<BlockedPeopleState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadFailed = false) }
            repository.refreshBlocks()
                .onSuccess { people ->
                    _state.update { BlockedPeopleState(isLoading = false, rows = people.map { p -> BlockedRow(p) }) }
                }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    fun unblock(userId: String) {
        val row = _state.value.rows.firstOrNull { it.person.userId == userId } ?: return
        if (row.isUnblocking) return
        setRow(userId) { it.copy(isUnblocking = true, failed = false) }
        viewModelScope.launch {
            repository.unblock(userId)
                .onSuccess {
                    _state.update { s ->
                        s.copy(rows = s.rows.filterNot { it.person.userId == userId }, justUnblocked = row.person)
                    }
                }
                .onFailure { setRow(userId) { it.copy(isUnblocking = false, failed = true) } }
        }
    }

    /** Puts the row back with its original date. */
    fun undo() {
        val person = _state.value.justUnblocked ?: return
        _state.update { s ->
            s.copy(
                justUnblocked = null,
                rows = (s.rows + BlockedRow(person)).sortedByDescending { it.person.blockedAt },
            )
        }
        viewModelScope.launch { repository.block(person.userId) }
    }

    fun dismissSnackbar() {
        _state.update { it.copy(justUnblocked = null) }
    }

    private fun setRow(userId: String, change: (BlockedRow) -> BlockedRow) {
        _state.update { s -> s.copy(rows = s.rows.map { if (it.person.userId == userId) change(it) else it }) }
    }
}

/** Runs once the signed-in shell is up: loads the block list and queues removed-image notices (R12b to R12e). */
class ModerationNoticesViewModel(private val repository: ModerationRepository) : ViewModel() {

    private val _notices = MutableStateFlow<List<ModerationNoticeDto>>(emptyList())
    val notices: StateFlow<List<ModerationNoticeDto>> = _notices.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refreshBlocks()
            repository.pendingNotices().onSuccess { _notices.value = it }
        }
    }

    fun dismissFirst() {
        val first = _notices.value.firstOrNull() ?: return
        _notices.update { it.drop(1) }
        viewModelScope.launch { repository.dismissNotice(first.id) }
    }
}
