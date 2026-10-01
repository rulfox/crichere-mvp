package com.crichere.app.ui

import androidx.compose.ui.graphics.Color
import com.crichere.app.profile.PlayingRole
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// Small display helpers shared by the league screens (Dashboard cards, League Detail, ...).

/** Logo-tile colors for leagues without a logo, picked stably per league id. */
private val TilePalette = listOf(Color(0xFF1B5E20), Color(0xFF2B5FB5), Color(0xFF7A5B12), Color(0xFFB5462B), Color(0xFF5B2A86))

internal fun tileColor(id: String): Color = TilePalette[abs(id.hashCode()) % TilePalette.size]

/** First letters of the first three words that start with a letter: "Kolhapur Premier League 2026" -> "KPL". */
internal fun shortCode(name: String): String =
    name.split(' ').filter { it.firstOrNull()?.isLetter() == true }.take(3).joinToString("") { it.first().uppercase() }
        .ifEmpty { name.take(2).uppercase() }

/** Up to two initials for an avatar: "Aarav Pawar" -> "AP". */
internal fun initials(name: String?): String =
    name?.split(' ')?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.first().uppercase() }.orEmpty()

private val IndianGrouping: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"))

/** "₹5,000" (Indian digit grouping, no decimals). */
internal fun rupees(amount: Double): String = "₹${IndianGrouping.format(amount)}"

internal fun entryLabel(fee: Double?): String = if (fee == null || fee <= 0.0) "Free" else "${rupees(fee)} entry"

private val ShortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val LongDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "2026-10-12" -> "12 Oct" (or the raw string if it isn't an ISO date). */
internal fun shortDate(iso: String): String = runCatching { LocalDate.parse(iso).format(ShortDate) }.getOrDefault(iso)

/** "2026-10-12" -> "12 Oct 2026". */
internal fun longDate(iso: String): String = runCatching { LocalDate.parse(iso).format(LongDate) }.getOrDefault(iso)

internal fun yearOf(iso: String): String? = runCatching { LocalDate.parse(iso).year.toString() }.getOrNull()

internal fun PlayingRole.label(): String = when (this) {
    PlayingRole.BATSMAN -> "Batsman"
    PlayingRole.BOWLER -> "Bowler"
    PlayingRole.ALL_ROUNDER -> "All-rounder"
    PlayingRole.WICKETKEEPER -> "Wicketkeeper"
}
