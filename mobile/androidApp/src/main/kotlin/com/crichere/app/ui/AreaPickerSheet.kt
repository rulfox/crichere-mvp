package com.crichere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.ui.theme.InstrumentSansFamily

/**
 * Design screen D2: bottom sheet listing places (states/districts/cities) with a search box; the
 * current selection is highlighted. Picking one closes the sheet via [onSelected].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> AreaPickerSheet(
    title: String,
    searchHint: String,
    options: List<T>,
    selectedName: String?,
    optionLabel: (T) -> String,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val visible = options.filter { optionLabel(it).contains(query.trim(), ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        scrimColor = Color(0x800A120C),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(Color(0xFFB9C1B4), RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                title,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                color = colors.onBackground,
                modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp),
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFFE4E8DF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(searchHint, style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 14.sp), color = colors.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 14.sp, color = colors.onBackground),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Open scrolled to the current selection (with a little context above it).
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (options.indexOfFirst { optionLabel(it) == selectedName } - 2).coerceAtLeast(0),
            )
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
                items(visible) { option ->
                    val name = optionLabel(option)
                    val selected = name == selectedName
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colors.primaryContainer else Color.Transparent)
                            .clickable { onSelected(option) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(name, style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp), color = colors.onBackground)
                    }
                }
            }
        }
    }
}
