package com.crichere.backend.moderation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Authenticated (falls through to `.anyRequest().authenticated()`), and only ever about the caller's own list. */
@RestController
@RequestMapping("/api/v1/blocks")
class BlockController(private val blockService: BlockService) {

    @GetMapping
    fun list(@AuthenticationPrincipal userId: UUID): List<BlockedPersonResponse> = blockService.list(userId)

    @PutMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun block(@AuthenticationPrincipal userId: UUID, @PathVariable targetUserId: UUID) {
        blockService.block(userId, targetUserId)
    }

    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(@AuthenticationPrincipal userId: UUID, @PathVariable targetUserId: UUID) {
        blockService.unblock(userId, targetUserId)
    }
}

/** Write-only by design: see [ReportService]. */
@RestController
@RequestMapping("/api/v1/reports")
class ReportController(private val reportService: ReportService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun report(@AuthenticationPrincipal userId: UUID, @Valid @RequestBody request: ReportRequest) {
        reportService.report(userId, request)
    }
}
