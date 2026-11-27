package com.crichere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.league.AuctionField
import com.crichere.app.league.AuctionSettingsState
import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.BidMode
import com.crichere.app.league.LadderPreviewLine
import com.crichere.app.league.RangeField
import com.crichere.app.ui.theme.CrichereError
import com.crichere.app.ui.theme.CrichereGreenContainer
import com.crichere.app.ui.theme.CrichereOnGreenContainer
import com.crichere.app.ui.theme.CrichereOnWarning
import com.crichere.app.ui.theme.CrichereWarningContainer
import com.crichere.app.ui.theme.JetBrainsMonoFamily

private val wholeRupees = KeyboardOptions(keyboardType = KeyboardType.Number)
private val ErrorRowFill = Color(0xFFFDF3F2)
private val CardOutline = Color(0xFFC5CEC0)
private val RowDivider = Color(0xFFDCE2D8)

internal fun monoText(size: Number, weight: FontWeight = FontWeight.Medium, lineHeight: Int? = null) =
    TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = weight, fontSize = size.toFloat().sp, lineHeight = lineHeight?.sp ?: TextStyle.Default.lineHeight)

/** A dashed rounded outline (the preview's placeholder box). */
private fun Modifier.dashedBorder(color: Color, radius: Int) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        cornerRadius = CornerRadius(radius.dp.toPx()),
    )
}

/** A section's heading ("When", "Franchises", "Bidding"): League Creation's `SectionHeading` style. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = TextStyle(fontFamily = com.crichere.app.ui.theme.ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 15.sp),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 1.dp),
    )
}

/** A small grey line under a field, lined up with the text inside it (like the fields' own helper row). */
@Composable
internal fun FieldHelp(text: String) {
    Text(text, style = pText(12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
}

/**
 * The Bidding section (Auction Settings redesign; iosApp `BidStepsSection.swift`): the starting price, then
 * "How bids go up" as two example cards -- "Same amount always" (one "Each bid adds" amount) or "Changes
 * with price" (rows that read "₹1,000 to [3,000] add [₹500]", ending "Above ₹3,000 add […]") -- and a
 * preview of the bids. A row with a mistake turns pink and says what's wrong, with amounts to tap.
 */
@Composable
internal fun BiddingSection(state: AuctionSettingsState, viewModel: AuctionSettingsViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Bidding")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CrichereTextField(
                value = state.basePrice, onValueChange = viewModel::onBasePriceChanged, label = "Starting price (₹)",
                look = FieldVariant.Form, mono = true, error = state.fieldErrors[AuctionField.BasePrice],
                keyboardOptions = wholeRupees, readOnly = state.isSaving, placeholder = "e.g. 250",
            )
            FieldHelp("Every player’s bidding opens at this price.")
        }

        Text("How bids go up", style = pText(13.sp, FontWeight.SemiBold, 17.55.sp), color = colors.onBackground, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.selectableGroup().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard("Same amount always", state.sameExample, state.bidMode == BidMode.SameAlways, enabled = !state.isSaving, Modifier.weight(1f)) {
                viewModel.onBidModeChanged(BidMode.SameAlways)
            }
            ModeCard("Changes with price", "e.g. +250 till 1,000, then +500", state.bidMode == BidMode.ChangesWithPrice, enabled = !state.isSaving, Modifier.weight(1f)) {
                viewModel.onBidModeChanged(BidMode.ChangesWithPrice)
            }
        }

        if (state.bidMode == BidMode.SameAlways) {
            CrichereTextField(
                value = state.bidIncrement, onValueChange = viewModel::onBidIncrementChanged, label = "Each bid adds (₹)",
                look = FieldVariant.Form, mono = true, error = state.fieldErrors[AuctionField.BidIncrement],
                keyboardOptions = wholeRupees, readOnly = state.isSaving, placeholder = "e.g. 250",
            )
        } else {
            PriceRanges(state, viewModel)
        }

        val preview = state.ladderPreview
        if (preview != null) {
            LadderPreview(preview)
        } else {
            Text(
                state.previewPlaceholder,
                style = pText(13.sp, lineHeight = 18.sp),
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().dashedBorder(colors.outline, radius = 14).padding(14.dp),
            )
        }
    }
}

/** One "How bids go up" card: a radio, its name and a short example. */
@Composable
private fun ModeCard(title: String, example: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else CardOutline, RoundedCornerShape(14.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(
                selected = selected, onClick = null, modifier = Modifier.size(20.dp),
                colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
            )
            Text(title, style = pText(13.5.sp, FontWeight.SemiBold, 17.sp), color = colors.onBackground)
        }
        Text(example, style = monoText(12, lineHeight = 17), color = colors.onSurfaceVariant)
    }
}

/** "Changes with price": an example sentence, the rows, and "+ Add another price range". */
@Composable
private fun PriceRanges(state: AuctionSettingsState, viewModel: AuctionSettingsViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            buildAnnotatedString {
                append("Bids go up by bigger amounts as the price rises. Example: ")
                withStyle(SpanStyle(color = colors.onBackground)) { append("+₹250 till ₹1,000, then +₹500 till ₹3,000, then +₹1,000.") }
            },
            style = pText(12.5.sp, lineHeight = 17.5.sp),
            color = colors.onSurfaceVariant,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, RowDivider, RoundedCornerShape(14.dp)),
        ) {
            state.ranges.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = RowDivider)
                val error = state.rangeErrorFor(index)
                val start = state.rangeStartText(index) ?: "—"
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.background(if (error != null) ErrorRowFill else colors.surface).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(start, style = monoText(13, FontWeight.SemiBold), color = colors.onBackground, modifier = Modifier.width(54.dp))
                        Text("to", style = pText(13.sp), color = colors.onSurfaceVariant)
                        CompactAmountField(
                            row.upTo, { viewModel.onRangeUpToChanged(index, it) }, "Range from $start ends at",
                            isError = error?.field == RangeField.UpTo, readOnly = state.isSaving, modifier = Modifier.weight(1f),
                        )
                        Text("add", style = pText(13.sp), color = colors.onSurfaceVariant)
                        CompactAmountField(
                            row.step, { viewModel.onRangeStepChanged(index, it) }, "Range from $start adds",
                            isError = error?.field == RangeField.Step, readOnly = state.isSaving, rupee = true, modifier = Modifier.width(84.dp),
                        )
                        val removeLabel = "Remove range from $start"
                        Icon(
                            painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.onSurfaceVariant,
                            modifier = Modifier
                                .size(width = 40.dp, height = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !state.isSaving, role = Role.Button, onClick = { viewModel.onRemoveRange(index) })
                                .semantics { contentDescription = removeLabel }
                                .padding(horizontal = 11.dp, vertical = 15.dp),
                        )
                    }
                    if (error != null) RowError(error.message, error.suggestions, enabled = !state.isSaving) { viewModel.onRangeSuggestionPicked(index, it) }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = RowDivider)
            val aboveError = state.fieldErrors[AuctionField.BidIncrement]
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.background(if (aboveError != null) ErrorRowFill else colors.surface).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            ) {
                val above = state.aboveStartText ?: "—"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("Above ")
                            withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)) { append(above) }
                        },
                        style = pText(13.5.sp, lineHeight = 18.sp), color = colors.onBackground, modifier = Modifier.weight(1f),
                    )
                    Text("add", style = pText(13.sp), color = colors.onSurfaceVariant)
                    CompactAmountField(
                        state.bidIncrement, viewModel::onBidIncrementChanged, "Above $above adds",
                        isError = aboveError != null, readOnly = state.isSaving, rupee = true, modifier = Modifier.width(84.dp),
                    )
                    Spacer(Modifier.width(40.dp))
                }
                if (aboveError != null) RowError(aboveError, emptyList(), enabled = false) {}
            }
        }
        if (state.canAddRange) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !state.isSaving, role = Role.Button, onClick = viewModel::onAddRange)
                    .padding(end = 8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Text("Add another price range", style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp), color = colors.primary)
            }
        }
    }
}

/** A 44dp boxed number for the range rows; [rupee] puts a ₹ in front. */
@Composable
private fun CompactAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    isError: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    rupee: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = wholeRupees,
        textStyle = monoText(14.5, FontWeight.Medium).copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.semantics {
            contentDescription = description
            if (isError) error("Check this amount")
        },
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(if (isError) 2.dp else 1.dp, if (isError) CrichereError else colors.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp),
            ) {
                if (rupee) Text("₹", style = monoText(14, FontWeight.SemiBold), color = colors.onSurfaceVariant)
                inner()
            }
        },
    )
}

/** A row's mistake in plain words, and the amounts that would work ("Try ₹400 ₹500"). */
@Composable
private fun RowError(message: String, suggestions: List<Long>, enabled: Boolean, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_error), null, tint = CrichereError, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Text(message, style = pText(13.sp, FontWeight.Medium, 18.sp), color = CrichereError)
        }
        if (suggestions.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 22.dp)) {
                Text("Try", style = pText(13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                suggestions.forEach { amount ->
                    val text = "₹" + indianGrouping(amount)
                    Text(
                        text,
                        style = monoText(13, FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable(enabled = enabled, role = Role.Button, onClick = { onPick(amount) })
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** `150000` -> `1,50,000` for the suggestion chips. */
private fun indianGrouping(amount: Long): String {
    val digits = amount.toString()
    if (digits.length <= 3) return digits
    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$rest,$last3"
}

/** "Bids will be": a step pill and the bids it adds, per range; each line filled to the width. */
@Composable
private fun LadderPreview(lines: List<LadderPreviewLine>) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant).padding(14.dp),
    ) {
        Text("Bids will be", style = pText(12.5.sp, lineHeight = 17.sp), color = colors.onSurfaceVariant)
        if (lines.size == 1) {
            FillingLine(lines.single().options, monoText(13, lineHeight = 20), colors.onBackground, Modifier.fillMaxWidth())
        } else {
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(64.dp)) {
                        Text(
                            line.step, style = monoText(11, FontWeight.Bold, lineHeight = 20), color = CrichereOnGreenContainer,
                            modifier = Modifier.clip(CircleShape).background(CrichereGreenContainer).padding(horizontal = 8.dp),
                        )
                    }
                    FillingLine(line.options, monoText(12.5, lineHeight = 20), colors.onBackground, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Shows the longest of [options] (longest first) that fits on one line, so a trailing "…" ends the row. */
@Composable
private fun FillingLine(options: List<String>, style: TextStyle, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val text = remember(options, width, style) {
            options.firstOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= width } ?: options.last()
        }
        Text(text, style = style, color = color, maxLines = 1)
    }
}

/** Once the auction has started, settings are read-only. */
@Composable
internal fun LockedAuctionNote() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CrichereWarningContainer).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = CrichereOnWarning, modifier = Modifier.size(18.dp))
        Text("The auction has started. These settings can’t be changed now.", style = pText(13.5.sp, FontWeight.Medium, lineHeight = 19.sp), color = CrichereOnWarning)
    }
}

/** One read-only group: a title over label / value rows in a white card. */
@Composable
internal fun LockedGroup(title: String, rows: List<Pair<String, String>>, extra: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, RowDivider, RoundedCornerShape(14.dp)),
        ) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = RowDivider)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, style = pText(14.sp), color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = pText(14.sp, FontWeight.SemiBold), color = colors.onBackground)
                }
            }
            extra?.invoke()
        }
    }
}

/** The read-only price ranges under "How bids go up": "₹250 to ₹1,000 · add ₹250". */
@Composable
internal fun LockedRanges(rows: List<LadderPreviewLine>) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().drawBehind {
                    drawLine(RowDivider, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.values, style = monoText(13, FontWeight.SemiBold), color = colors.onBackground, modifier = Modifier.weight(1f))
                Text(
                    row.step, style = monoText(12, FontWeight.Bold, lineHeight = 20), color = CrichereOnGreenContainer,
                    modifier = Modifier.clip(CircleShape).background(CrichereGreenContainer).padding(horizontal = 8.dp),
                )
            }
        }
    }
}
