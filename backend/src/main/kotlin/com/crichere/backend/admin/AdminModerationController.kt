package com.crichere.backend.admin

import com.crichere.backend.common.ProblemDetails
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

/** Every admin action needs a reason in plain words; it goes to the audit log with the admin's email. */
data class AdminNoteRequest(@field:NotBlank @field:Size(max = 1000) val note: String)

data class RemoveImageRequest(
    val kind: ImageKind,
    val targetId: UUID,
    val reportId: UUID? = null,
    @field:NotBlank @field:Size(max = 1000) val note: String,
)

data class SuspendRequest(
    val reportId: UUID? = null,
    /** Only for urgent safety cases: suspending an organizer stalls their live auction. */
    val duringAuction: Boolean = false,
    @field:NotBlank @field:Size(max = 1000) val note: String,
)

data class SuspendedOverview(val accounts: List<SuspendedAccount>, val blockedNumbers: List<BlockedNumber>)

/** Moderation for the admin web (docs/PHASE20.md Step 3). Everything here needs an admin session. */
@RestController
@RequestMapping("/api/v1/admin")
class AdminModerationController(private val moderation: AdminModerationService) {

    @GetMapping("/reports")
    fun reports(
        @RequestParam(defaultValue = "OPEN") status: String,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "25") limit: Int,
    ): AdminPage<ReportListItem> {
        if (status !in AdminModerationService.REPORT_VIEWS) throw AdminConflictException("BAD_FILTER", "Unknown report status.")
        return moderation.listReports(status, reason, cursor, limit.coerceIn(1, MAX_PAGE))
    }

    @GetMapping("/reports/{id}")
    fun report(@PathVariable id: UUID): ReportDetail = moderation.reportDetail(id)

    @PostMapping("/reports/{id}/dismiss")
    fun dismiss(@PathVariable id: UUID, @Valid @RequestBody body: AdminNoteRequest, @AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<Void> {
        moderation.dismiss(id, admin.email, body.note.trim())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/reports/{id}/resolve")
    fun resolve(@PathVariable id: UUID, @Valid @RequestBody body: AdminNoteRequest, @AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<Void> {
        moderation.resolve(id, admin.email, body.note.trim())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/images/remove")
    fun removeImage(@Valid @RequestBody body: RemoveImageRequest, @AuthenticationPrincipal admin: AdminPrincipal): RemoveImageResult =
        moderation.removeImage(body.kind, body.targetId, body.reportId, admin.email, body.note.trim())

    @PostMapping("/users/{id}/suspend")
    fun suspend(@PathVariable id: UUID, @Valid @RequestBody body: SuspendRequest, @AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<Void> {
        moderation.suspend(id, body.reportId, body.duringAuction, admin.email, body.note.trim())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/users/{id}/unsuspend")
    fun unsuspend(@PathVariable id: UUID, @Valid @RequestBody body: AdminNoteRequest, @AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<Void> {
        moderation.unsuspend(id, admin.email, body.note.trim())
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/suspended")
    fun suspended(): SuspendedOverview = SuspendedOverview(moderation.suspendedAccounts(), moderation.blockedNumbers())

    @PostMapping("/suspended-numbers/{hash}/release")
    fun releaseNumber(@PathVariable hash: String, @Valid @RequestBody body: AdminNoteRequest, @AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<Void> {
        moderation.releaseNumber(hash, admin.email, body.note.trim())
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/audit")
    fun audit(
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) admin: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): AdminPage<AuditEntry> = moderation.auditLog(action, admin, cursor, limit.coerceIn(1, MAX_PAGE))

    private companion object {
        const val MAX_PAGE = 100
    }
}

@RestControllerAdvice(basePackageClasses = [AdminModerationController::class])
class AdminModerationExceptionHandler {
    @ExceptionHandler(AdminNotFoundException::class)
    fun notFound(e: AdminNotFoundException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ProblemDetails.of(HttpStatus.NOT_FOUND, "not-found", "Not found", "NOT_FOUND", "${e.what} not found.", request.requestURI),
        )

    @ExceptionHandler(AdminConflictException::class)
    fun conflict(e: AdminConflictException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ProblemDetails.of(HttpStatus.CONFLICT, "admin-action-refused", "Action refused", e.code, e.detail, request.requestURI),
        )
}
