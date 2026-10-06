package com.crichere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.InstrumentSansFamily
import com.crichere.app.ui.theme.JetBrainsMonoFamily

// Form field look from the design board (screens C, I, J, ...): 54dp box, 12dp radius, the label
// sits inside as a placeholder until there's a value, then moves into a notch on the top border.

private val FieldShape = RoundedCornerShape(12.dp)
private val FieldHeight = 54.dp
private val DisabledFill = Color(0xFFEFF1EC)
private val DisabledBorder = Color(0xFFDDE1D6)
private val DisabledText = Color(0xFFB0B8B1)

private enum class FieldMode { Idle, Active, Disabled }

/**
 * Per-screen look of a field. Profile setup (C) uses the defaults; League creation (I) uses
 * [Form]: white fill, 52dp, and a label notch that can sit on a white card.
 */
data class FieldVariant(
    val height: Dp = FieldHeight,
    val idleFill: Color = Color.Transparent,
    /** What the notched label masks the border with: the screen, or a card behind the field. */
    val notchColor: Color? = null,
) {
    companion object {
        val Form = FieldVariant(height = 52.dp, idleFill = Color.White)
        val FormOnCard = FieldVariant(height = 52.dp, idleFill = Color.White, notchColor = Color.White)
    }
}

@Composable
private fun FieldFrame(
    label: String,
    hasValue: Boolean,
    mode: FieldMode,
    modifier: Modifier = Modifier,
    look: FieldVariant = FieldVariant(),
    error: String? = null,
    height: Dp = look.height,
    topAligned: Boolean = false,
    reserveErrorSlot: Boolean = false,
    /** A 16 dp row under the field that is always there (helper, warning or error); the caller draws the error in it. */
    supporting: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (border, borderColor, fill) = when {
        mode == FieldMode.Disabled -> Triple(1.dp, DisabledBorder, DisabledFill)
        error != null -> Triple(2.dp, colors.error, if (mode == FieldMode.Active) colors.surface else look.idleFill)
        mode == FieldMode.Active -> Triple(2.dp, colors.primary, colors.surface)
        else -> Triple(1.dp, colors.outline, look.idleFill)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(fill, FieldShape)
                    .border(border, borderColor, FieldShape)
                    .padding(start = 14.dp + border, end = 11.dp, top = if (topAligned) 15.dp else 0.dp),
                contentAlignment = if (topAligned) Alignment.TopStart else Alignment.CenterStart,
                content = content,
            )
            if (hasValue || error != null) {
                // The notch masks the top border with whatever is behind the field.
                Text(
                    text = label,
                    style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 11.sp),
                    color = when {
                        error != null -> colors.error
                        mode == FieldMode.Active -> colors.primary
                        else -> colors.onSurfaceVariant
                    },
                    modifier = Modifier
                        .offset(x = 11.dp, y = (-6).dp)
                        .background(look.notchColor ?: colors.background)
                        .padding(horizontal = 4.dp),
                )
            }
        }
        if (supporting != null) {
            Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) { supporting() }
        } else if (reserveErrorSlot) {
            // U4 F1: the 16 dp row is always there, so an error doesn't push the fields below down.
            Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(16.dp)) {
                if (error != null) {
                    Text(
                        error,
                        style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.error,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        } else if (error != null) {
            Text(
                error,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.6.sp),
                color = colors.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun placeholderStyle(fontSize: TextUnit, disabled: Boolean, subtle: Boolean = false) = TextStyle(
    fontFamily = InstrumentSansFamily,
    fontSize = fontSize,
    color = when {
        disabled -> DisabledText
        subtle -> CrichereInkSubtle
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    },
)

@Composable
private fun valueStyle(fontSize: TextUnit, mono: Boolean = false) = TextStyle(
    fontFamily = if (mono) JetBrainsMonoFamily else InstrumentSansFamily,
    fontWeight = FontWeight.Medium,
    fontSize = fontSize,
    color = MaterialTheme.colorScheme.onBackground,
)

/**
 * Text input in the board's notched-label style. [placeholder] replaces the label as the empty
 * hint (e.g. "name@bank"); [mono] sets numbers and IDs in JetBrains Mono; [minLines] > 1 makes a
 * top-aligned multi-line box of [multiLineHeight].
 */
@Composable
fun CrichereTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    look: FieldVariant = FieldVariant(),
    error: String? = null,
    placeholder: String? = null,
    mono: Boolean = false,
    trailingIcon: Int? = null,
    minLines: Int = 1,
    multiLineHeight: Dp = 80.dp,
    readOnly: Boolean = false,
    /** Keep a 16 dp error row under the field even without an error (no jump when one appears). */
    reserveErrorSlot: Boolean = false,
    /** Disabled look (board I16): grey fill/border/label, no typing. */
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val multiLine = minLines > 1
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = !multiLine,
        textStyle = if (multiLine) valueStyle(13.5.sp).copy(fontWeight = FontWeight.Normal, lineHeight = 18.9.sp) else valueStyle(14.5.sp, mono),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        readOnly = readOnly,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        decorationBox = { inner ->
            FieldFrame(
                label = label,
                hasValue = value.isNotEmpty() || isFocused || placeholder != null,
                mode = when {
                    !enabled -> FieldMode.Disabled
                    isFocused -> FieldMode.Active
                    else -> FieldMode.Idle
                },
                look = look,
                error = error,
                height = if (multiLine) multiLineHeight else look.height,
                topAligned = multiLine,
                reserveErrorSlot = reserveErrorSlot,
            ) {
                if (value.isEmpty() && (!isFocused || placeholder != null)) {
                    // An errored empty mono field shows its label in-box in mono too (board J2).
                    val inBoxStyle = placeholderStyle(if (multiLine) 13.5.sp else 14.5.sp, disabled = !enabled, subtle = placeholder != null || error != null)
                    Text(
                        placeholder ?: label,
                        style = if (mono && error != null && placeholder == null) inBoxStyle.copy(fontFamily = JetBrainsMonoFamily) else inBoxStyle,
                    )
                }
                Box(Modifier.padding(end = if (trailingIcon != null) 30.dp else 0.dp)) { inner() }
                if (trailingIcon != null) {
                    Icon(
                        painterResource(trailingIcon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp).size(20.dp),
                    )
                }
            }
        },
    )
}

/** A field that opens something on tap (a date picker) instead of taking text. */
@Composable
fun CrichereTapField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    look: FieldVariant = FieldVariant(),
    error: String? = null,
    trailingIcon: Int? = null,
    valueSize: TextUnit = 14.sp,
    /** Makes [trailingIcon] its own 40 dp button (a clear "x"), separate from the field's tap. */
    onTrailingClick: (() -> Unit)? = null,
    trailingLabel: String? = null,
    trailingSize: Dp = 18.dp,
    supporting: (@Composable () -> Unit)? = null,
) {
    FieldFrame(
        label = label,
        hasValue = value != null,
        mode = FieldMode.Idle,
        look = look,
        error = error,
        supporting = supporting,
        modifier = modifier.semantics(mergeDescendants = true) {}.clickable(role = Role.Button, onClick = onClick),
    ) {
        if (value != null) {
            Text(value, style = valueStyle(valueSize), maxLines = 1, modifier = Modifier.padding(end = if (onTrailingClick != null) 36.dp else 26.dp))
        } else {
            Text(label, style = placeholderStyle(14.5.sp, disabled = false, subtle = error != null), maxLines = 1)
        }
        if (trailingIcon != null && onTrailingClick != null) {
            // The frame pads 11 dp on the right; the board's button sits 6 dp from the edge.
            Box(
                Modifier.align(Alignment.CenterEnd).offset(x = 5.dp).size(40.dp).clip(CircleShape).clickable(onClickLabel = trailingLabel, onClick = onTrailingClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(trailingIcon), contentDescription = trailingLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(trailingSize))
            }
        } else if (trailingIcon != null) {
            Icon(
                painterResource(trailingIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(trailingSize),
            )
        }
    }
}

/**
 * Tap-to-open picker in the board's field style (screen C5): arrow flips up and the field takes the
 * focused look while its menu is open; the current selection is highlighted in the menu.
 */
@Composable
fun <T> CrichereSelectField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 14.5.sp,
    look: FieldVariant = FieldVariant(),
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val mode = when {
        !enabled -> FieldMode.Disabled
        expanded -> FieldMode.Active
        else -> FieldMode.Idle
    }
    val arrowTint = when {
        !enabled -> DisabledText
        expanded -> colors.primary
        selected != null -> colors.onBackground
        else -> colors.onSurfaceVariant
    }

    Box(modifier = modifier.onSizeChanged { widthPx = it.width }) {
        FieldFrame(
            label = label,
            hasValue = selected != null,
            mode = mode,
            look = look,
            error = error,
            modifier = Modifier.clickable(enabled = enabled, role = Role.DropdownList) { expanded = true },
        ) {
            if (selected != null) {
                Text(
                    optionLabel(selected),
                    style = valueStyle(fontSize),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 26.dp),
                )
            } else {
                Text(label, style = placeholderStyle(fontSize, disabled = !enabled, subtle = error != null), maxLines = 1)
            }
            Icon(
                painter = painterResource(if (expanded) R.drawable.ic_arrow_drop_up else R.drawable.ic_arrow_drop_down),
                contentDescription = null,
                tint = arrowTint,
                modifier = Modifier.align(Alignment.CenterEnd).size(22.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 4.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = colors.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.width(with(density) { widthPx.toDp() }),
        ) {
            options.forEach { option ->
                MenuRow(
                    text = optionLabel(option),
                    selected = option == selected,
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, selected: Boolean, onClick: () -> Unit, height: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground))
    }
}
