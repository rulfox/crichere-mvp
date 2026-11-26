package com.crichere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.league.AuctionField
import com.crichere.app.league.AuctionSettingsState
import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.LadderPreviewLine
import com.crichere.app.league.RangeField
import com.crichere.app.ui.theme.CrichereGreenContainer
import com.crichere.app.ui.theme.CrichereOnGreenContainer
import com.crichere.app.ui.theme.CrichereOnWarning
import com.crichere.app.ui.theme.CrichereWarningContainer
import com.crichere.app.ui.theme.JetBrainsMonoFamily

private val wholeRupees = KeyboardOptions(keyboardType = KeyboardType.Number)

private fun monoText(size: Int, weight: FontWeight = FontWeight.Medium, lineHeight: Int? = null) =
    TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight?.sp ?: TextStyle.Default.lineHeight)

/** A dashed rounded outline (the "+ Add range" pill and the preview's placeholder box). */
private fun Modifier.dashedBorder(color: androidx.compose.ui.graphics.Color, radius: Int) = drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        cornerRadius = CornerRadius(radius.dp.toPx()),
    )
}

/**
 * Auction Settings' "Bid steps" (docs/PHASE22.md, design S1-S3; iosApp `BidStepsSection.swift`):
 * optional ranges -- each starts where the row above ended, so the organizer only types "Up to" and
 * "Step" -- then the required "and above" step (the league's bid increment), and a preview of how
 * bids will go. A broken row shows its error under the whole row; the preview hides until it's fixed.
 */
@Composable
internal fun BidStepsSection(state: AuctionSettingsState, viewModel: AuctionSettingsViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant, modifier = Modifier.padding(top = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Bid steps", style = pText(15.sp, FontWeight.SemiBold, lineHeight = 20.sp), color = colors.onBackground)
            Text(
                "How much each new bid must go up by. Add ranges to use bigger steps at higher prices.",
                style = pText(12.5.sp, lineHeight = 17.sp),
                color = colors.onSurfaceVariant,
            )
        }
        state.ranges.forEachIndexed { index, row ->
            val error = state.rangeErrorFor(index)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Caption(prefix = "From ", start = state.rangeStartText(index))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    CrichereTextField(
                        value = row.upTo, onValueChange = { viewModel.onRangeUpToChanged(index, it) }, label = "Up to",
                        look = FieldVariant.Form, mono = true, error = if (error?.field == RangeField.UpTo) "" else null,
                        keyboardOptions = wholeRupees, readOnly = state.isSaving, modifier = Modifier.weight(1f),
                    )
                    CrichereTextField(
                        value = row.step, onValueChange = { viewModel.onRangeStepChanged(index, it) }, label = "Step",
                        look = FieldVariant.Form, mono = true, error = if (error?.field == RangeField.Step) "" else null,
                        keyboardOptions = wholeRupees, readOnly = state.isSaving, modifier = Modifier.weight(1f),
                    )
                    val removeLabel = "Remove range from ${state.rangeStartText(index).orEmpty()}"
                    Icon(
                        painterResource(R.drawable.ic_close), contentDescription = null, tint = colors.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(width = 40.dp, height = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !state.isSaving, onClick = { viewModel.onRemoveRange(index) })
                            .semantics { contentDescription = removeLabel }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                }
                if (error != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 2.dp)) {
                        Icon(painterResource(R.drawable.ic_error), null, tint = colors.error, modifier = Modifier.padding(top = 1.dp).size(15.dp))
                        Text(error.message, style = pText(12.5.sp, FontWeight.Medium, lineHeight = 17.sp), color = colors.error)
                    }
                }
            }
        }
        if (state.canAddRange) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .dashedBorder(colors.outline, radius = 20)
                    .clickable(enabled = !state.isSaving, onClick = viewModel::onAddRange)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Text("Add range", style = pText(13.5.sp, FontWeight.SemiBold), color = colors.primary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Caption(start = state.aboveStartText, suffix = " and above")
            Row {
                CrichereTextField(
                    value = state.bidIncrement, onValueChange = viewModel::onBidIncrementChanged, label = "Step",
                    look = FieldVariant.Form, mono = true, error = state.fieldErrors[AuctionField.BidIncrement],
                    keyboardOptions = wholeRupees, readOnly = state.isSaving, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(50.dp))
            }
        }
        val preview = state.ladderPreview
        if (preview != null) {
            LadderPreview(preview)
        } else {
            Text(
                "Fix the steps above to see how bids will go.",
                style = pText(12.5.sp, lineHeight = 17.sp),
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().dashedBorder(colors.outline, radius = 14).padding(14.dp),
            )
        }
    }
}

/** "From ₹1,000" / "₹15,000 and above" over a row; the amount sits in mono ink. */
@Composable
private fun Caption(start: String?, prefix: String = "", suffix: String = "") {
    val colors = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, color = colors.onBackground)) { append(start ?: "—") }
            append(suffix)
        },
        style = pText(12.sp, FontWeight.Medium),
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp),
    )
}

/** Design S1's "Bids will go" box: a step pill and the bids it adds, per range. */
@Composable
private fun LadderPreview(lines: List<LadderPreviewLine>) {
    val colors = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant).padding(14.dp),
    ) {
        Text("BIDS WILL GO", style = pText(11.5.sp, FontWeight.SemiBold, lineHeight = 14.sp).copy(letterSpacing = 0.3.sp), color = colors.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        val pillWidth = 64.dp
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Row(Modifier.width(pillWidth)) {
                    Text(
                        line.step, style = monoText(11, FontWeight.Bold, lineHeight = 20), color = CrichereOnGreenContainer,
                        modifier = Modifier.clip(CircleShape).background(CrichereGreenContainer).padding(horizontal = 8.dp),
                    )
                }
                Text(line.values, style = monoText(12, lineHeight = 20), color = colors.onBackground, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Design S4: once the auction has started, settings are read-only. */
@Composable
internal fun LockedAuctionNote() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CrichereWarningContainer).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_lock), null, tint = CrichereOnWarning, modifier = Modifier.size(18.dp))
        Text("The auction has started. Settings can’t be changed now.", style = pText(13.sp, FontWeight.Medium, lineHeight = 18.sp), color = CrichereOnWarning)
    }
}

/** Design S4's read-only steps table. */
@Composable
internal fun LockedBidSteps(rows: List<LadderPreviewLine>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Bid steps", style = pText(15.sp, FontWeight.SemiBold, lineHeight = 20.sp), color = colors.onBackground)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp)),
        ) {
            TableRow("Bid range", "Step", header = true)
            rows.forEach { row ->
                HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                TableRow(row.values, row.step, header = false)
            }
        }
    }
}

@Composable
private fun TableRow(range: String, step: String, header: Boolean) {
    val colors = MaterialTheme.colorScheme
    val style = if (header) pText(12.sp, FontWeight.SemiBold) else monoText(13)
    val color = if (header) colors.onSurfaceVariant else colors.onBackground
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (header) colors.surfaceVariant else colors.surface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(range, style = style, color = color, modifier = Modifier.weight(1f))
        Text(step, style = style, color = color)
    }
}
