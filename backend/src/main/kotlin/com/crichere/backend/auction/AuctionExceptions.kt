package com.crichere.backend.auction

/**
 * `start` was called but the readiness check fails: settings not fully configured, the pool is
 * empty, no active franchise exists, or `auctionSquadMax * activeFranchiseCount` would already
 * exceed the active pool size -- the same inequality as docs/PHASE4.md's save-time warning,
 * re-checked against real counts and hard-blocking instead of warning (see docs/PHASE5.md).
 */
class AuctionNotReadyException(message: String) : RuntimeException(message)

/**
 * The action requires the auction to still be `NOT_STARTED` -- editing auction settings, joining/
 * claiming, or a roster removal, all attempted once the auction has left `NOT_STARTED` (see
 * docs/PHASE5.md's Decisions Made: settings and the roster both freeze the moment auction starts).
 */
class AuctionAlreadyStartedException : RuntimeException("This auction has already started")

/** `PATCH /leagues/{id}/complete` was called while the auction is `IN_PROGRESS`. */
class AuctionInProgressException : RuntimeException("This league's auction is in progress")

/** An auction action needs `IN_PROGRESS` (bid/next-player/sold/unsold/undo/toggle/end) but the auction hasn't started yet or has already ended. */
class AuctionNotInProgressException : RuntimeException("This auction is not in progress")

/** `next-player` was called while a player is already open -- must be closed with `sold`/`unsold` first. */
class AuctionPlayerAlreadyOpenException : RuntimeException("A player is already open for bidding")

/** A bid/`sold`/`unsold` was attempted with no player currently open -- call `next-player` first. */
class AuctionNoPlayerOpenException : RuntimeException("No player is currently open for bidding")

/** A bid was below the required minimum (current bid + increment, or base price if no bid yet). */
class BidTooLowException(val minimumAmount: java.math.BigDecimal) : RuntimeException("Bid must be at least $minimumAmount")

/** The bidding franchise would exceed `auctionSquadMax` if this bid won. */
class SquadFullException : RuntimeException("This franchise's squad is already full")

/** The bidding franchise would exceed its purse, and `auction_allow_exceed_purse` is off. */
class PurseExceededException : RuntimeException("This bid would exceed the franchise's remaining purse")

/** `sold` was called with no leading bid -- use `unsold` instead. */
class NoBidsToSellException : RuntimeException("There are no bids to sell to")

/** `undo` was called with no last action to reverse. */
class NothingToUndoException : RuntimeException("There is nothing to undo")
