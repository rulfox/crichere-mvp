package com.crichere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.crichere.app.ui.theme.InstrumentSansFamily

// Form field look from the design board (screens C, I, J, ...): 54dp box, 12dp radius, the label
// sits inside as a placeholder until there's a value, then moves into a notch on the top border.

private val FieldShape = RoundedCornerShape(12.dp)
private val FieldHeight = 54.dp
private val DisabledFill = Color(0xFFEFF1EC)
private val DisabledBorder = Color(0xFFDDE1D6)
private val DisabledText = Color(0xFFB0B8B1)

private enum class FieldMode { Idle, Active, Disabled }

@Composable
private fun FieldFrame(
    label: String,
    hasValue: Boolean,
    mode: FieldMode,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (border, borderColor, fill) = when (mode) {
        FieldMode.Active -> Triple(2.dp, colors.primary, colors.surface)
        FieldMode.Disabled -> Triple(1.dp, DisabledBorder, DisabledFill)
        FieldMode.Idle -> Triple(1.dp, colors.outline, Color.Transparent)
    }
    Box(modifier = modifier.fillMaxWidth().padding(top = 7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .background(fill, FieldShape)
                .border(border, borderColor, FieldShape)
                .padding(start = 14.dp + border, end = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
        if (hasValue) {
            // Background matches the screen so the label masks the top border (notched outline).
            Text(
                text = label,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 11.sp),
                color = if (mode == FieldMode.Active) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier
                    .offset(x = 11.dp, y = (-6).dp)
                    .background(colors.background)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun placeholderStyle(fontSize: TextUnit, disabled: Boolean) = TextStyle(
    fontFamily = InstrumentSansFamily,
    fontSize = fontSize,
    color = if (disabled) DisabledText else MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun valueStyle(fontSize: TextUnit) = TextStyle(
    fontFamily = InstrumentSansFamily,
    fontWeight = FontWeight.Medium,
    fontSize = fontSize,
    color = MaterialTheme.colorScheme.onBackground,
)

/** Single-line text input in the board's notched-label style. */
@Composable
fun CrichereTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = valueStyle(14.5.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        decorationBox = { inner ->
            FieldFrame(
                label = label,
                hasValue = value.isNotEmpty() || isFocused,
                mode = if (isFocused) FieldMode.Active else FieldMode.Idle,
            ) {
                if (value.isEmpty() && !isFocused) Text(label, style = placeholderStyle(14.5.sp, disabled = false))
                inner()
            }
        },
    )
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
                Text(label, style = placeholderStyle(fontSize, disabled = !enabled), maxLines = 1)
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
