package com.crichere.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SelectableDates
import android.text.format.DateFormat
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import com.crichere.app.ui.theme.InstrumentSansFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.league.AuctionField
import com.crichere.app.league.AuctionSettingsState
import com.crichere.app.league.AuctionSettingsViewModel
import com.crichere.app.league.amountText
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereDisabledContainer
import com.crichere.app.ui.theme.CrichereOnWarning
import com.crichere.app.ui.theme.CrichereWarningContainer
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PoolListText = Color(0xFF3E4A41)
private val DisabledSaveText = Color(0xFF6B756D)
private const val SAVED_NOTICE_MS = 3_000L

/** Resolves [AuctionSettingsViewModel] via Koin, parameterized on [leagueId] -- see `AppRoute.AuctionSettings` (ui/navigation). */
@Composable
internal fun AuctionSettingsRoute(
    leagueId: String,
    onBack: () -> Unit,
    viewModel: AuctionSettingsViewModel = koinViewModel(key = "auction-settings:$leagueId") { parametersOf(leagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }
    LaunchedEffect(state.showSavedNotice) {
        if (state.showSavedNotice) {
            delay(SAVED_NOTICE_MS)
            viewModel.onSavedNoticeShown()
        }
    }

    AuctionSettingsScreen(state = state, viewModel = viewModel, onBack = onBack)
}

/**
 * Design J1-J5: five fields (base price, purse, squad min/max, bid increment), the live squad
 * warning, and the read-only auction pool -- rendered from the league already loaded on
 * [AuctionSettingsState.league], no second network call (see docs/PHASE4.md's Decisions Made).
 * Save sits in a bottom bar; saved / failed show as the dark bar above it.
 */
@Composable
private fun AuctionSettingsScreen(state: AuctionSettingsState, viewModel: AuctionSettingsViewModel, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // U4 J9: clearing the auction time is instant; "Auction time cleared" offers Undo for 4 s.
    var clearedSchedule by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(clearedSchedule) {
        if (clearedSchedule != null) {
            delay(4_000)
            clearedSchedule = null
        }
    }
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        BackTitleBar("Auction Settings", onBack)
        val league = state.league
        when {
            state.isLoading && league == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            league == null -> LoadError(onRetry = viewModel::retry, title = "Couldn't load auction settings")
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 19.dp, end = 19.dp, top = 5.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Fields(state, viewModel, onScheduleCleared = { clearedSchedule = it })
                        state.squadWarning?.let { warning ->
                            SquadWarningNote(
                                "Squad max × franchises (${warning.squadMax} × ${warning.franchises} = ${warning.total}) is more than " +
                                    "players required (${warning.playersRequired}). Some squads may not fill.",
                            )
                        }
                        PoolCard(state)
                    }
                    val bottom = Modifier.align(Alignment.BottomCenter).padding(start = 13.dp, end = 13.dp, bottom = 19.dp)
                    val saveError = state.saveError
                    when {
                        saveError != null -> CrichereSnackbar(
                            message = saveError.message,
                            actionLabel = if (saveError.canRetry) "Retry" else null,
                            onAction = viewModel::submit,
                            modifier = bottom,
                        )
                        state.showSavedNotice -> CrichereSnackbar(message = "Auction settings saved", modifier = bottom)
                        clearedSchedule != null -> CrichereSnackbar(
                            message = "Auction time cleared",
                            actionLabel = "Undo",
                            onAction = {
                                viewModel.onScheduledAtChanged(clearedSchedule)
                                clearedSchedule = null
                            },
                            modifier = bottom,
                        )
                    }
                }
                SaveBar(state, onSave = viewModel::submit)
            }
        }
    }
}

/**
 * J3: the fields fade to 55% and stop taking input while saving. Each field reserves 7dp above
 * its box for the notched label, so the board's 12dp gaps are 5dp here.
 */
@Composable
private fun Fields(state: AuctionSettingsState, viewModel: AuctionSettingsViewModel, onScheduleCleared: (String) -> Unit) {
    val errors = state.fieldErrors
    val amount = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val count = KeyboardOptions(keyboardType = KeyboardType.Number)
    Column(Modifier.alpha(if (state.isSaving) 0.55f else 1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SettingField(state.basePrice, viewModel::onBasePriceChanged, "Base price", errors[AuctionField.BasePrice], amount, state.isSaving)
        SettingField(state.purse, viewModel::onPurseChanged, "Purse per franchise", errors[AuctionField.Purse], amount, state.isSaving)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingField(state.squadMin, viewModel::onSquadMinChanged, "Squad size (min)", errors[AuctionField.SquadMin], count, state.isSaving, Modifier.weight(1f))
            SettingField(state.squadMax, viewModel::onSquadMaxChanged, "Squad size (max)", errors[AuctionField.SquadMax], count, state.isSaving, Modifier.weight(1f))
        }
        SettingField(state.bidIncrement, viewModel::onBidIncrementChanged, "Bid increment", errors[AuctionField.BidIncrement], amount, state.isSaving)
        ScheduledAtField(state, viewModel::onScheduledAtChanged, onCleared = onScheduleCleared)
    }
}

/**
 * Optional "bidding opens at" time (docs/PHASE11.md D3), design update #4 J9-J12: a read-only tap field
 * that opens the date dialog (past days disabled), then the time dialog (Back returns to the date). The
 * row under it always holds the time-zone helper, or the passed-time warning (J9), or the error (J12).
 * Clearing is immediate; [onCleared] lets the screen offer Undo.
 */
@Composable
private fun ScheduledAtField(state: AuctionSettingsState, onChange: (String?) -> Unit, onCleared: (String) -> Unit) {
    val readOnly = state.isSaving
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val zone = ZoneId.systemDefault()
    val scheduledAt = state.scheduledAt
    val current = scheduledAt?.let { runCatching { Instant.parse(it).atZone(zone) }.getOrNull() }
    val error = state.scheduledAtError
    CrichereTapField(
        label = "Auction date & time (optional)",
        value = current?.format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", Locale.getDefault())),
        onClick = { if (!readOnly) pickingDate = true },
        look = FieldVariant.Form,
        error = error,
        valueSize = 14.5.sp,
        trailingIcon = if (current != null) R.drawable.ic_close else R.drawable.ic_event,
        trailingSize = if (current != null) 20.dp else 22.dp,
        trailingLabel = if (current != null) "Clear auction time" else "Pick auction time",
        onTrailingClick = {
            if (readOnly) return@CrichereTapField
            if (scheduledAt != null) {
                onChange(null)
                onCleared(scheduledAt)
            } else {
                pickingDate = true
            }
        },
        supporting = {
            val colors = MaterialTheme.colorScheme
            when {
                error != null -> Text(error, style = pText(12.sp, FontWeight.Medium, 16.sp), color = colors.error, modifier = Modifier.padding(start = 14.dp))
                state.scheduledAtPassed -> Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_schedule), contentDescription = null, tint = PassedWarning, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("This time has passed. Pick a new one or clear it.", style = pText(12.sp, FontWeight.Medium, 16.sp), color = PassedWarning)
                }
                // U5 J14: no zone name -- "GMT+05:30" means nothing to most organizers.
                else -> Text(
                    "Uses your phone's time zone.",
                    style = pText(12.sp, lineHeight = 16.sp),
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        },
    )
    if (pickingDate) {
        ScheduleDatePicker(
            initial = pickedDate ?: current?.toLocalDate(),
            onDismiss = { pickingDate = false; pickedDate = null },
            onPicked = { pickingDate = false; pickedDate = it },
        )
    }
    val date = pickedDate
    if (date != null && !pickingDate) {
        ScheduleTimePicker(
            date = date,
            initial = current?.toLocalTime() ?: LocalTime.of(19, 0),
            onBack = { pickingDate = true },
            onDismiss = { pickedDate = null },
            onPicked = { time ->
                pickedDate = null
                onChange(date.atTime(time).atZone(zone).toInstant().toString())
            },
        )
    }
}

/** J10: M3 date dialog titled "Auction date"; days before today can't be picked; Next opens the time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDatePicker(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.takeIf { !it.isBefore(today) }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = object : SelectableDates {
            // The picker hands over UTC midnight of each calendar day.
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    val colors = MaterialTheme.colorScheme
    val pickerColors = DatePickerDefaults.colors(
        containerColor = ScheduleDialogSurface,
        selectedDayContainerColor = colors.primary,
        selectedDayContentColor = Color.White,
        todayDateBorderColor = colors.primary,
        todayContentColor = colors.primary,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        colors = pickerColors,
        confirmButton = {
            // U5 J13: Next stays disabled (38%) until a day is picked.
            val millis = pickerState.selectedDateMillis
            TextButton(
                onClick = { if (millis != null) onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) },
                enabled = millis != null,
            ) { Text("Next", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary.copy(alpha = if (millis != null) 1f else 0.38f)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary) }
        },
    ) {
        DatePicker(
            state = pickerState,
            colors = pickerColors,
            showModeToggle = false,
            title = {
                Text("Auction date", style = pText(12.sp, FontWeight.Medium, 12.sp), color = DialogLabel, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp))
            },
            headline = {
                val selected = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                // U5 J13: "Pick a date" in ink-muted until a day is tapped (J10's 30/36 either way).
                Text(
                    selected?.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())) ?: "Pick a date",
                    style = pText(30.sp, lineHeight = 36.sp),
                    color = if (selected != null) colors.onBackground else colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 22.dp, bottom = 14.dp),
                )
            },
        )
    }
}

/** J11: M3 time picker in a dialog, 12- or 24-hour as the phone is set; Back returns to the date (kept). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePicker(date: LocalDate, initial: LocalTime, onBack: () -> Unit, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = DateFormat.is24HourFormat(context))
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ScheduleDialogSurface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                "Auction time · ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))}",
                style = pText(12.sp, FontWeight.Medium, 12.sp),
                color = DialogLabel,
            )
        },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                Text("OK", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("Back", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary) }
        },
        text = {
            // U5 J15: Material 3's own digits ("06" at 57/64) are accepted; only the font family is themed.
            val type = MaterialTheme.typography
            MaterialTheme(
                typography = type.copy(
                    displayLarge = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
                    bodyLarge = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
                    titleMedium = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
                ),
            ) {
            TimePicker(
                state = pickerState,
                // J11's palette: green for the active part, the board's neutral for the rest, warm amber for AM/PM.
                colors = TimePickerDefaults.colors(
                    containerColor = ScheduleDialogSurface,
                    clockDialColor = TimeNeutral,
                    selectorColor = colors.primary,
                    timeSelectorSelectedContainerColor = Color(0xFFD7EBD2),
                    timeSelectorSelectedContentColor = Color(0xFF0B2E10),
                    timeSelectorUnselectedContainerColor = TimeNeutral,
                    timeSelectorUnselectedContentColor = colors.onBackground,
                    periodSelectorBorderColor = Color(0xFF9FAE9A),
                    periodSelectorSelectedContainerColor = Color(0xFFF9D9C9),
                    periodSelectorSelectedContentColor = Color(0xFF5A2B12),
                    periodSelectorUnselectedContentColor = colors.onSurfaceVariant,
                ),
            )
            }
        },
    )
}

private val ScheduleDialogSurface = Color(0xFFF1F4EE)
private val DialogLabel = Color(0xFF3E4A41)
private val PassedWarning = Color(0xFF7A5B12)
private val TimeNeutral = Color(0xFFE2E5DC)

@Composable
private fun SettingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardOptions: KeyboardOptions,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    CrichereTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        look = FieldVariant.Form,
        mono = true,
        error = error,
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        modifier = modifier,
    )
}

/** J2's amber note. */
@Composable
private fun SquadWarningNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CrichereWarningContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = CrichereOnWarning, modifier = Modifier.padding(top = 3.6.dp).size(18.dp))
        Text(text, style = pText(12.5.sp, FontWeight.Medium, 17.5.sp), color = CrichereOnWarning)
    }
}

@Composable
private fun PoolCard(state: AuctionSettingsState) {
    val colors = MaterialTheme.colorScheme
    val league = state.league ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 13.dp),
    ) {
        Text("Auction pool", style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 14.sp), color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
        Text("${league.players.size} player(s) joined", style = pText(13.sp, lineHeight = 16.9.sp), color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
        if (league.franchises.isEmpty()) {
            Text(
                "No franchises yet. They appear here once owners claim them.",
                style = pText(13.sp, lineHeight = 18.2.sp),
                color = colors.onSurfaceVariant,
            )
        } else {
            val purse = league.auctionPurse?.let { amountText(it) } ?: "not set"
            Text("Franchises (purse: $purse each):", style = pText(13.sp, lineHeight = 16.9.sp), color = colors.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                league.franchises.joinToString("\n") { "- ${it.name}" },
                style = pText(12.5.sp, lineHeight = 18.75.sp),
                color = PoolListText,
            )
        }
    }
}

/** J1-J5's bottom bar: 44dp Save pill -- grey while errors show (J2), spinner while saving (J3). */
@Composable
private fun SaveBar(state: AuctionSettingsState, onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        val enabled = state.canSave
        Row(
            Modifier
                .padding(start = 19.dp, end = 19.dp, top = 12.dp, bottom = 24.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (enabled || state.isSaving) colors.primary else CrichereDisabledContainer)
                .clickable(enabled = enabled, onClick = onSave),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (state.isSaving) "Saving…" else "Save",
                style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp),
                color = if (enabled || state.isSaving) Color.White else DisabledSaveText,
            )
        }
    }
}
