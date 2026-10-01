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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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

private val PoolListText = Color(0xFF3E4A41)
private val DisabledSaveText = Color(0xFF6B756D)
private const val SAVED_NOTICE_MS = 3_000L

/** Resolves [AuctionSettingsViewModel] via Koin, parameterized on [leagueId] -- see `AuthNavHost`'s `MainDestination.AuctionSettings`. */
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
                        Fields(state, viewModel)
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
private fun Fields(state: AuctionSettingsState, viewModel: AuctionSettingsViewModel) {
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
    }
}

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
