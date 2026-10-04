package com.crichere.app.league

/**
 * Indian digit grouping for a bid amount while it is typed (`15500` -> `15,500`, `1250000` -> `12,50,000`),
 * with the caret mappings a text field's visual transformation needs. The typed text itself stays plain
 * digits (and one optional `.`), so [AuctionViewModel] keeps parsing it as before; only what is drawn changes.
 *
 * Only the part before a `.` is grouped. Characters other than digits and `.` are not expected (the field
 * filters them) and are passed through unchanged.
 */
class GroupedAmount internal constructor(
    val text: String,
    private val originalToTransformed: IntArray,
) {
    /** Caret position in [text] for a caret [original] characters into the typed text. */
    fun toTransformed(original: Int): Int = originalToTransformed[original.coerceIn(0, originalToTransformed.lastIndex)]

    /** Caret position in the typed text for a caret [transformed] characters into [text]. */
    fun toOriginal(transformed: Int): Int {
        val end = transformed.coerceIn(0, text.length)
        return end - text.take(end).count { it == ',' }
    }
}

fun groupIndianAmount(raw: String): GroupedAmount {
    val integerLength = raw.indexOf('.').let { if (it == -1) raw.length else it }
    val out = StringBuilder()
    val map = IntArray(raw.length + 1)
    for (i in raw.indices) {
        val remainingDigits = integerLength - i
        // Last three digits form the first group, then pairs: ...,12,50,000
        if (i in 1 until integerLength && (remainingDigits == 3 || (remainingDigits > 3 && (remainingDigits - 3) % 2 == 0))) {
            out.append(',')
        }
        map[i] = out.length
        out.append(raw[i])
    }
    map[raw.length] = out.length
    return GroupedAmount(out.toString(), map)
}
