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
