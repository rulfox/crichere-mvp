package com.crichere.backend.common

import com.crichere.backend.auction.AuctionAlreadyStartedException
import com.crichere.backend.auction.AuctionInProgressException
import com.crichere.backend.auction.AuctionNoPlayerOpenException
import com.crichere.backend.auction.AuctionNotInProgressException
import com.crichere.backend.auction.AuctionNotReadyException
import com.crichere.backend.auction.AuctionPlayerAlreadyOpenException
import com.crichere.backend.auction.BidTooLowException
import com.crichere.backend.auction.NoBidsToSellException
import com.crichere.backend.auction.NothingToUndoException
import com.crichere.backend.auction.PurseExceededException
import com.crichere.backend.auction.SquadFullException
import com.crichere.backend.auth.AuthenticationFailedException
import com.crichere.backend.auth.RateLimitExceededException
import com.crichere.backend.franchise.LeagueFranchiseNotFoundException
import com.crichere.backend.franchise.NoLeaveRequestPendingException as FranchiseNoLeaveRequestPendingException
import com.crichere.backend.franchise.NotFranchiseOwnerException
import com.crichere.backend.league.CapacityBelowActiveCountException
import com.crichere.backend.league.FeeLockedException
import com.crichere.backend.league.GroundNotFoundException
import com.crichere.backend.league.LeagueAwardNotFoundException
import com.crichere.backend.league.LeagueCapacityFullException
import com.crichere.backend.league.LeagueCompletedException
import com.crichere.backend.league.LeagueNotFoundException
import com.crichere.backend.league.NotOrganizerException
import com.crichere.backend.league.OrganizerUpiRequiredException
import com.crichere.backend.league.PaymentScreenshotRequiredException
import com.crichere.backend.league.SquadSizeInvalidException
import com.crichere.backend.player.AlreadyJoinedException
import com.crichere.backend.player.LeaguePlayerNotFoundException
import com.crichere.backend.player.NoLeaveRequestPendingException as PlayerNoLeaveRequestPendingException
import com.crichere.backend.player.NotPlayerOwnerException
import com.crichere.backend.profile.BowlingStyleNotAllowedException
import com.crichere.backend.profile.BowlingStyleRequiredException
import com.crichere.backend.reference.MalformedDistrictIdException
import com.crichere.backend.reference.MalformedStateCodeException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import kotlin.math.max

/**
 * Turns exceptions escaping the controllers into RFC 7807 responses.
 *
 * Only exceptions that something in the current codebase can actually throw are handled here.
 * Speculative handlers for a not-found entity or a conflict would be dead code today; they
 * belong to the task that first throws them.
 *
 * Note that this advice never sees a Spring Security rejection: those are raised inside the
 * filter chain, before the dispatcher servlet picks a handler. Those are wired separately in
 * `SecurityConfig`, using the same [ProblemDetails] builder so the shapes match.
 *
 * The governing rule for every handler below: the exception's own message never reaches the
 * response body. Framework and JPA exception messages routinely embed SQL fragments, entity
 * class names, and constraint names.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Bean-validation failure on a `@Valid @RequestBody`.
     *
     * The per-field messages *are* echoed, because they come from our own DTO annotations
     * (`"idToken is required"`), not from a framework internal -- and a client genuinely
     * cannot fix a 400 it cannot localise to a field.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val errors = exception.bindingResult.fieldErrors.associate { fieldError ->
            fieldError.field to (fieldError.defaultMessage ?: "is invalid")
        }
        return ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "validation-failed",
            title = "Validation failed",
            code = "VALIDATION_FAILED",
            detail = "One or more fields in the request body are missing or invalid.",
            instance = request.requestURI,
            extensions = mapOf("errors" to errors),
        )
    }

    /**
     * Body absent, truncated, or not parseable as JSON. The parser's message points at byte
     * offsets and Jackson class names, so it is dropped entirely.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "malformed-request",
            title = "Malformed request",
            code = "MALFORMED_REQUEST",
            detail = "The request body could not be read as JSON.",
            instance = request.requestURI,
        )

    /** e.g. `GET /api/v1/auth/session`, which exists only as a `POST`. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.METHOD_NOT_ALLOWED,
            slug = "method-not-allowed",
            title = "Method not allowed",
            code = "METHOD_NOT_ALLOWED",
            detail = "This endpoint does not support the requested HTTP method.",
            instance = request.requestURI,
        )

    /**
     * Every authentication failure -- bad Firebase token, unknown/expired/revoked refresh
     * token -- collapses into one indistinguishable 401. Which credential failed, and why, is
     * exactly the information an attacker probing for valid accounts wants.
     */
    @ExceptionHandler(AuthenticationFailedException::class)
    fun handleAuthenticationFailed(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.UNAUTHORIZED,
            slug = "invalid-credentials",
            title = "Invalid credentials",
            code = "INVALID_CREDENTIALS",
            detail = "The supplied credentials are not valid.",
            instance = request.requestURI,
        )

    /**
     * Rate limit tripped. `Retry-After` is set (in whole seconds, as RFC 9110 requires) so a
     * well-behaved client backs off instead of retrying immediately.
     */
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(
        exception: RateLimitExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val retryAfterSeconds = max(1L, exception.retryAfter.seconds)
        val body = ProblemDetails.of(
            status = HttpStatus.TOO_MANY_REQUESTS,
            slug = "rate-limit-exceeded",
            title = "Too many requests",
            code = "RATE_LIMIT_EXCEEDED",
            detail = "Too many authentication attempts. Please try again later.",
            instance = request.requestURI,
            extensions = mapOf("retryAfterSeconds" to retryAfterSeconds),
        )
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(body)
    }

    /** Same shape as [handleRateLimitExceeded], for the content-creation endpoints (league/ground/award). */
    @ExceptionHandler(ContentRateLimitExceededException::class)
    fun handleContentRateLimitExceeded(
        exception: ContentRateLimitExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        val retryAfterSeconds = max(1L, exception.retryAfter.seconds)
        val body = ProblemDetails.of(
            status = HttpStatus.TOO_MANY_REQUESTS,
            slug = "rate-limit-exceeded",
            title = "Too many requests",
            code = "RATE_LIMIT_EXCEEDED",
            detail = "Too many requests. Please try again later.",
            instance = request.requestURI,
            extensions = mapOf("retryAfterSeconds" to retryAfterSeconds),
        )
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
            .body(body)
    }

    /** An unknown path under a public prefix, e.g. `POST /api/v1/auth/nope`. */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No endpoint matches this request.",
            instance = request.requestURI,
        )

    /**
     * A `{state}` path segment that cannot possibly be a state code (see
     * [com.crichere.backend.reference.ReferenceController]). Same shape as
     * [handleNoResourceFound] -- from the caller's point of view this is exactly that: nothing
     * matches this request.
     */
    @ExceptionHandler(MalformedStateCodeException::class)
    fun handleMalformedStateCode(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No state matches this code.",
            instance = request.requestURI,
        )

    /** Same shape as [handleMalformedStateCode], for the District-retrofit `districts/{id}/cities` endpoint. */
    @ExceptionHandler(MalformedDistrictIdException::class)
    fun handleMalformedDistrictId(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No district matches this id.",
            instance = request.requestURI,
        )

    /**
     * `PUT /profiles/me` supplied a `playingRole`/`bowlingStyle` combination that violates the
     * role-conditional rule (see `com.crichere.backend.profile.ProfileService`). Both
     * directions share one `type`/`code` -- a client branching on `code` doesn't need to tell
     * them apart -- but get their own fixed `detail` text rather than the exception's own
     * message, for the same reason every other handler in this file does that.
     */
    @ExceptionHandler(BowlingStyleRequiredException::class)
    fun handleBowlingStyleRequired(request: HttpServletRequest): ProblemDetail =
        profileValidationProblem(
            detail = "bowlingStyle is required when playingRole is BOWLER or ALL_ROUNDER.",
            request = request,
        )

    @ExceptionHandler(BowlingStyleNotAllowedException::class)
    fun handleBowlingStyleNotAllowed(request: HttpServletRequest): ProblemDetail =
        profileValidationProblem(
            detail = "bowlingStyle is only allowed when playingRole is BOWLER or ALL_ROUNDER.",
            request = request,
        )

    private fun profileValidationProblem(detail: String, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "invalid-bowling-style",
            title = "Invalid bowling style",
            code = "INVALID_BOWLING_STYLE",
            detail = detail,
            instance = request.requestURI,
        )

    /**
     * A presigned-upload endpoint was called but S3 is not usable in this environment yet (see
     * [PhotoUploadService]). `503`, not `500`: the request itself was fine, an external
     * dependency is the one that is not ready.
     */
    @ExceptionHandler(PhotoUploadUnavailableException::class)
    fun handlePhotoUploadUnavailable(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            slug = "photo-upload-unavailable",
            title = "Photo upload unavailable",
            code = "PHOTO_UPLOAD_UNAVAILABLE",
            detail = "Photo upload is temporarily unavailable. Please try again later.",
            instance = request.requestURI,
        )

    /** No league matches the id in the path -- `GET`/`PUT`/`PATCH`/awards-CRUD on a nonexistent league. */
    @ExceptionHandler(LeagueNotFoundException::class)
    fun handleLeagueNotFound(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No league matches this id.",
            instance = request.requestURI,
        )

    /** A league create/edit request referenced a `groundId` that doesn't exist (see `com.crichere.backend.league.LeagueService`). */
    @ExceptionHandler(GroundNotFoundException::class)
    fun handleGroundNotFound(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No ground matches this id.",
            instance = request.requestURI,
        )

    /** No award matches the id under the given league (see `com.crichere.backend.league.LeagueExceptions`). */
    @ExceptionHandler(LeagueAwardNotFoundException::class)
    fun handleLeagueAwardNotFound(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No award matches this id.",
            instance = request.requestURI,
        )

    /**
     * The caller is authenticated but is not this league's organizer -- Phase 2's first genuinely
     * new authorization surface (see `com.crichere.backend.league.LeagueExceptions`).
     */
    @ExceptionHandler(NotOrganizerException::class)
    fun handleNotOrganizer(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.FORBIDDEN,
            slug = "access-denied",
            title = "Access denied",
            code = "ACCESS_DENIED",
            detail = "You do not have permission to modify this league.",
            instance = request.requestURI,
        )

    /** A join/claim was attempted on a league that's already completed (see `com.crichere.backend.league.LeagueExceptions`). */
    @ExceptionHandler(LeagueCompletedException::class)
    fun handleLeagueCompleted(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "league-completed",
            title = "League completed",
            code = "LEAGUE_COMPLETED",
            detail = "This league is already completed.",
            instance = request.requestURI,
        )

    /** `playersRequired`/`franchisesRequired` capacity has already been reached (see `com.crichere.backend.player.PlayerService`/`com.crichere.backend.franchise.FranchiseService`). */
    @ExceptionHandler(LeagueCapacityFullException::class)
    fun handleLeagueCapacityFull(exception: LeagueCapacityFullException, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "capacity-full",
            title = "Capacity full",
            code = "CAPACITY_FULL",
            detail = "Registration for this role is full.",
            instance = request.requestURI,
            extensions = mapOf("role" to exception.role),
        )

    /** A create/edit request set a fee but left `organizerUpiId` blank (see `com.crichere.backend.league.LeagueService`). */
    @ExceptionHandler(OrganizerUpiRequiredException::class)
    fun handleOrganizerUpiRequired(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "organizer-upi-required",
            title = "Organizer UPI id required",
            code = "ORGANIZER_UPI_REQUIRED",
            detail = "organizerUpiId is required when a fee is set.",
            instance = request.requestURI,
        )

    /** An auction-settings save had `squadMin` greater than `squadMax` (see `com.crichere.backend.league.LeagueExceptions`). */
    @ExceptionHandler(SquadSizeInvalidException::class)
    fun handleSquadSizeInvalid(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "squad-size-invalid",
            title = "Invalid squad size",
            code = "SQUAD_SIZE_INVALID",
            detail = "squadMin cannot be greater than squadMax.",
            instance = request.requestURI,
        )

    /** A `PUT` edit tried to drop capacity below the current active count (see `com.crichere.backend.league.LeagueService.update`). */
    @ExceptionHandler(CapacityBelowActiveCountException::class)
    fun handleCapacityBelowActiveCount(exception: CapacityBelowActiveCountException, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "capacity-below-active-count",
            title = "Capacity below active count",
            code = "CAPACITY_BELOW_ACTIVE_COUNT",
            detail = "Capacity cannot be set below the current number of active participants.",
            instance = request.requestURI,
            extensions = mapOf("role" to exception.role),
        )

    /** A `PUT` edit tried to change a fee while active rows already exist for that role (see `com.crichere.backend.league.LeagueService.update`). */
    @ExceptionHandler(FeeLockedException::class)
    fun handleFeeLocked(exception: FeeLockedException, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "fee-locked",
            title = "Fee locked",
            code = "FEE_LOCKED",
            detail = "This fee cannot be changed while active participants already exist for this role.",
            instance = request.requestURI,
            extensions = mapOf("role" to exception.role),
        )

    /** A role's fee is set but the join/claim request didn't include a payment screenshot (see `com.crichere.backend.player.PlayerService`/`com.crichere.backend.franchise.FranchiseService`). */
    @ExceptionHandler(PaymentScreenshotRequiredException::class)
    fun handlePaymentScreenshotRequired(exception: PaymentScreenshotRequiredException, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "payment-screenshot-required",
            title = "Payment screenshot required",
            code = "PAYMENT_SCREENSHOT_REQUIRED",
            detail = "A payment screenshot is required to join/claim as ${exception.role}.",
            instance = request.requestURI,
        )

    /** No player row matches the id under the given league (see `com.crichere.backend.player.PlayerExceptions`). */
    @ExceptionHandler(LeaguePlayerNotFoundException::class)
    fun handleLeaguePlayerNotFound(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No player matches this id.",
            instance = request.requestURI,
        )

    /** No franchise row matches the id under the given league (see `com.crichere.backend.franchise.FranchiseExceptions`). */
    @ExceptionHandler(LeagueFranchiseNotFoundException::class)
    fun handleLeagueFranchiseNotFound(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.NOT_FOUND,
            slug = "not-found",
            title = "Not found",
            code = "NOT_FOUND",
            detail = "No franchise matches this id.",
            instance = request.requestURI,
        )

    /** The caller already has an active join row for this league (see `league_players_active_unique`). */
    @ExceptionHandler(AlreadyJoinedException::class)
    fun handleAlreadyJoined(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "already-joined",
            title = "Already joined",
            code = "ALREADY_JOINED",
            detail = "You have already joined this league as a player.",
            instance = request.requestURI,
        )

    /** An approve/dismiss was attempted with no pending leave request (see `com.crichere.backend.player.PlayerExceptions`). */
    @ExceptionHandler(PlayerNoLeaveRequestPendingException::class)
    fun handlePlayerNoLeaveRequestPending(request: HttpServletRequest): ProblemDetail = noLeaveRequestPendingProblem(request)

    /** Same as [handlePlayerNoLeaveRequestPending], for franchises (see `com.crichere.backend.franchise.FranchiseExceptions`). */
    @ExceptionHandler(FranchiseNoLeaveRequestPendingException::class)
    fun handleFranchiseNoLeaveRequestPending(request: HttpServletRequest): ProblemDetail = noLeaveRequestPendingProblem(request)

    private fun noLeaveRequestPendingProblem(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "no-leave-request-pending",
            title = "No leave request pending",
            code = "NO_LEAVE_REQUEST_PENDING",
            detail = "There is no pending leave request for this row.",
            instance = request.requestURI,
        )

    /** The caller is authenticated but is not this player row's own user -- checked on a self-scoped leave request (see `com.crichere.backend.player.PlayerExceptions`). */
    @ExceptionHandler(NotPlayerOwnerException::class)
    fun handleNotPlayerOwner(request: HttpServletRequest): ProblemDetail = accessDeniedProblem(request)

    /** The caller is authenticated but is neither the league's organizer nor this franchise's own owner (see `com.crichere.backend.franchise.FranchiseExceptions`). */
    @ExceptionHandler(NotFranchiseOwnerException::class)
    fun handleNotFranchiseOwner(request: HttpServletRequest): ProblemDetail = accessDeniedProblem(request)

    private fun accessDeniedProblem(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.FORBIDDEN,
            slug = "access-denied",
            title = "Access denied",
            code = "ACCESS_DENIED",
            detail = "You do not have permission to perform this action.",
            instance = request.requestURI,
        )

    /** `start` failed its readiness check (see `com.crichere.backend.auction.AuctionExceptions`). */
    @ExceptionHandler(AuctionNotReadyException::class)
    fun handleAuctionNotReady(exception: AuctionNotReadyException, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "auction-not-ready",
            title = "Auction not ready",
            code = "AUCTION_NOT_READY",
            detail = exception.message ?: "This league's auction is not ready to start.",
            instance = request.requestURI,
        )

    /** Settings/join/claim/roster-removal attempted once the auction has left `NOT_STARTED` (see docs/PHASE5.md's Decisions Made). */
    @ExceptionHandler(AuctionAlreadyStartedException::class)
    fun handleAuctionAlreadyStarted(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "auction-already-started",
            title = "Auction already started",
            code = "AUCTION_ALREADY_STARTED",
            detail = "This action is not allowed once the auction has started.",
            instance = request.requestURI,
        )

    /** `PATCH /leagues/{id}/complete` attempted while the auction is `IN_PROGRESS`. */
    @ExceptionHandler(AuctionInProgressException::class)
    fun handleAuctionInProgress(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "auction-in-progress",
            title = "Auction in progress",
            code = "AUCTION_IN_PROGRESS",
            detail = "This league's auction is in progress.",
            instance = request.requestURI,
        )

    /** An auction action needs `IN_PROGRESS` but the auction hasn't started or has already ended. */
    @ExceptionHandler(AuctionNotInProgressException::class)
    fun handleAuctionNotInProgress(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "auction-not-in-progress",
            title = "Auction not in progress",
            code = "AUCTION_NOT_IN_PROGRESS",
            detail = "This auction is not currently in progress.",
            instance = request.requestURI,
        )

    /** `next-player` called while a player is already open (see `com.crichere.backend.auction.AuctionService`). */
    @ExceptionHandler(AuctionPlayerAlreadyOpenException::class)
    fun handleAuctionPlayerAlreadyOpen(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "auction-player-already-open",
            title = "Player already open",
            code = "AUCTION_PLAYER_ALREADY_OPEN",
            detail = "A player is already open for bidding.",
            instance = request.requestURI,
        )

    /** A bid/`sold`/`unsold` attempted with no player currently open. */
    @ExceptionHandler(AuctionNoPlayerOpenException::class)
    fun handleAuctionNoPlayerOpen(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "auction-no-player-open",
            title = "No player open",
            code = "AUCTION_NO_PLAYER_OPEN",
            detail = "No player is currently open for bidding.",
            instance = request.requestURI,
        )

    /** A bid was below the required minimum (see `com.crichere.backend.auction.AuctionService.placeBid`). */
    @ExceptionHandler(BidTooLowException::class)
    fun handleBidTooLow(exception: BidTooLowException, request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.BAD_REQUEST,
            slug = "bid-too-low",
            title = "Bid too low",
            code = "BID_TOO_LOW",
            detail = "Bid must be at least ${exception.minimumAmount}.",
            instance = request.requestURI,
            extensions = mapOf("minimumAmount" to exception.minimumAmount),
        )

    /** A bid would push the franchise's squad past `auctionSquadMax`. */
    @ExceptionHandler(SquadFullException::class)
    fun handleSquadFull(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "squad-full",
            title = "Squad full",
            code = "SQUAD_FULL",
            detail = "This franchise's squad is already full.",
            instance = request.requestURI,
        )

    /** A bid would exceed the franchise's remaining purse and exceeding it isn't currently allowed. */
    @ExceptionHandler(PurseExceededException::class)
    fun handlePurseExceeded(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "purse-exceeded",
            title = "Purse exceeded",
            code = "PURSE_EXCEEDED",
            detail = "This bid would exceed the franchise's remaining purse.",
            instance = request.requestURI,
        )

    /** `sold` called with no leading bid (see `com.crichere.backend.auction.AuctionService`). */
    @ExceptionHandler(NoBidsToSellException::class)
    fun handleNoBidsToSell(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "no-bids-to-sell",
            title = "No bids to sell",
            code = "NO_BIDS_TO_SELL",
            detail = "There are no bids to sell to. Use unsold instead.",
            instance = request.requestURI,
        )

    /** `undo` called with no last action to reverse. */
    @ExceptionHandler(NothingToUndoException::class)
    fun handleNothingToUndo(request: HttpServletRequest): ProblemDetail =
        ProblemDetails.of(
            status = HttpStatus.CONFLICT,
            slug = "nothing-to-undo",
            title = "Nothing to undo",
            code = "NOTHING_TO_UNDO",
            detail = "There is nothing to undo.",
            instance = request.requestURI,
        )

    /**
     * Anything unanticipated. The stack trace goes to the server log -- where it is useful and
     * private -- and the caller gets a bare sentence with no class names, no SQL, and no
     * indication of what broke.
     *
     * Spring Security's own exceptions are re-thrown untouched. They are raised *inside* the
     * dispatcher (method security) but are meant to be translated by `ExceptionTranslationFilter`
     * further out in the chain; swallowing them here would silently turn every 401/403 into a
     * 500. Re-throwing the identical instance lets the resolver fall through without logging a
     * spurious warning.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ProblemDetail {
        if (exception is AccessDeniedException || exception is AuthenticationException) throw exception
        log.error("Unhandled exception while handling {} {}", request.method, request.requestURI, exception)
        return ProblemDetails.of(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            slug = "internal-error",
            title = "Internal server error",
            code = "INTERNAL_ERROR",
            detail = "An unexpected error occurred.",
            instance = request.requestURI,
        )
    }
}
