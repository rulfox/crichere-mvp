package com.crichere.app.league

/**
 * `purse − spent` below zero means the franchise went over its purse (the organizer allowed exceeding):
 * returns how far over, else `null`. A negative number is never shown (design update #4, A1).
 */
fun overPurseAmount(purseRemaining: Double?): Double? = purseRemaining?.takeIf { it < 0 }?.let { -it }

/**
 * What the bidder dock shows where the Amount field sits (design update #4, A3). A status tile of the
 * same height replaces the field, so the dock never changes height. Priority: squad full > leading >
 * purse can't cover.
 */
sealed interface DockMode {
    data object Bid : DockMode
    data class Leading(val amount: Double) : DockMode
    data class SquadFull(val playersWon: Int, val squadMax: Int) : DockMode
    data class PurseShort(val minimumNextBid: Double) : DockMode
}

/**
 * The connection pill next to the status chip (design update #4, A4). A drop that recovers within a
 * second shows nothing; [LOST] (with Retry) after 30 s; [BACK_ONLINE] for 1.5 s, only if a pill was showing.
 */
enum class ConnectionPhase { ONLINE, RECONNECTING, LOST, BACK_ONLINE }

/** Which body the organizer's "nobody can bid" card uses (design update #4, A2). */
enum class DeadEndKind { ALL_FULL, ALL_PURSE, MIXED }

fun deadEndKind(squadsFull: Int, purseBelowBase: Int): DeadEndKind = when {
    purseBelowBase == 0 -> DeadEndKind.ALL_FULL
    squadsFull == 0 -> DeadEndKind.ALL_PURSE
    else -> DeadEndKind.MIXED
}

/**
 * Body of the End Auction confirmation (design update #5, L19a/b), split so the screen can set the
 * count in 600 white: `prefix + emphasis + suffix`. [pending] counts every player not yet sold or
 * finally unsold, the one on the block included -- the same set the server marks unsold on end.
 */
data class EndAuctionBody(val prefix: String, val emphasis: String, val suffix: String) {
    val text: String get() = prefix + emphasis + suffix
}

fun endAuctionBody(pending: Int, deadEnd: Boolean): EndAuctionBody {
    val players = if (pending == 1) "1 player" else "$pending players"
    return when {
        pending <= 0 -> EndAuctionBody("Every player has been auctioned. Squads become final.", "", "")
        deadEnd -> EndAuctionBody("No franchise can buy the remaining ", players, ", so they'll be marked unsold. Squads become final.")
        pending == 1 -> EndAuctionBody("", players, " is still in the pool. They'll be marked unsold and squads become final. This can't be undone.")
        else -> EndAuctionBody("", players, " are still in the pool. They'll all be marked unsold and squads become final. This can't be undone.")
    }
}

/** Why End Auction failed (design update #5, L21): a network problem, or a refusal the server gave. */
enum class EndAuctionFailure { NETWORK, REFUSED }
