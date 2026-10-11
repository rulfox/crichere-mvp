package com.crichere.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.crichere.app.R
import com.crichere.app.account.DeleteAccountEvent
import com.crichere.app.account.DeleteAccountScreenState
import com.crichere.app.account.DeleteAccountViewModel
import com.crichere.app.account.DeletionBlockReason
import com.crichere.app.account.DeletionBlockerDto
import com.crichere.app.account.DeletionHandoverDto
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereDisabledContainer
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereInkDisabled
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.CrichereOnWarning
import com.crichere.app.ui.theme.InstrumentSansFamily
import org.koin.compose.viewmodel.koinViewModel

private val BodyInk = Color(0xFF3E4A41)
private val GraceBody = Color(0xFF2E4A31)
private val Skeleton = Color(0xFFE6E9E1)

private fun sans(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = size) =
    TextStyle(fontFamily = InstrumentSansFamily, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

private fun archivo(size: TextUnit, weight: FontWeight, lineHeight: TextUnit = size, letterSpacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = ArchivoFamily, fontWeight = weight, fontSize = size, lineHeight = lineHeight, letterSpacing = letterSpacing)

/**
 * Delete account (design D2–D6, docs/PHASE16.md). The server check runs on every entry; the screen
 * then shows exactly one of can delete (D3), blocked (D4) or error (D5). [onSignedOut] fires after a
 * successful request, once the local session is gone (D7). [onOpenLeague] opens a blocking league (E).
 */
@Composable
internal fun DeleteAccountRoute(onBack: () -> Unit, onOpenLeague: (String) -> Unit, onSignedOut: () -> Unit) {
    val viewModel: DeleteAccountViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.check() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DeleteAccountEvent.SignedOut -> onSignedOut()
            }
        }
    }

    DeleteAccountScreen(
        screen = state.screen,
        onBack = onBack,
        onOpenLeague = onOpenLeague,
        onRetry = viewModel::check,
        onDelete = viewModel::openConfirm,
    )

    val dialog = state.dialog
    val screen = state.screen
    if (dialog != null && screen is DeleteAccountScreenState.CanDelete) {
        DeleteAccountConfirmDialog(
            state = dialog,
            erasesOn = eraseDate(screen.erasesOn),
            onInputChange = viewModel::onInputChange,
            onDismiss = viewModel::dismissConfirm,
            onConfirm = viewModel::confirm,
        )
    }
}

@Composable
private fun DeleteAccountScreen(
    screen: DeleteAccountScreenState,
    onBack: () -> Unit,
    onOpenLeague: (String) -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(onBack)
        when (screen) {
            DeleteAccountScreenState.Error -> ErrorState(onRetry, Modifier.weight(1f))
            else -> {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (screen) {
                        DeleteAccountScreenState.Loading -> LoadingContent()
                        is DeleteAccountScreenState.CanDelete -> CanDeleteContent(screen)
                        is DeleteAccountScreenState.Blocked -> BlockedContent(screen.blockers, onOpenLeague)
                        DeleteAccountScreenState.Error -> Unit
                    }
                }
                BottomBar {
                    when (screen) {
                        is DeleteAccountScreenState.Blocked -> CheckAgainButton("Check again", onRetry, Modifier.fillMaxWidth())
                        else -> DeleteButton(enabled = screen is DeleteAccountScreenState.CanDelete, onClick = onDelete)
                    }
                }
            }
        }
    }
}

/** Back arrow + "Delete account", 56 dp under the status bar. */
@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text("Delete account", style = sans(18.sp, FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = archivo(24.sp, FontWeight.ExtraBold, 27.6.sp, (-0.48).sp), color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Lead(text: String) {
    Text(text, style = sans(14.sp, lineHeight = 21.sp), color = BodyInk)
}

/** D2. */
@Composable
private fun LoadingContent() {
    Heading("Delete your account")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Text("Checking your leagues…", style = sans(14.sp, FontWeight.Medium, 18.2.sp), color = BodyInk)
    }
    Card {
        listOf(239.dp, 188.dp, 212.dp, 164.dp).forEachIndexed { index, width ->
            if (index > 0) RowDivider()
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).background(Skeleton, RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(width).height(12.dp).background(Skeleton, RoundedCornerShape(6.dp)))
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(96.dp).background(Skeleton, RoundedCornerShape(16.dp)))
}

/** D3a / D3b. */
@Composable
private fun CanDeleteContent(screen: DeleteAccountScreenState.CanDelete) {
    val colors = MaterialTheme.colorScheme
    Heading("Delete your account")
    Lead("You'll be signed out on all your devices straight away.")
    Row(
        Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_history), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("14 days to change your mind", style = sans(14.5.sp, FontWeight.Bold, 17.4.sp), color = colors.onPrimaryContainer)
            Text(
                "Nothing is erased until ${eraseDate(screen.erasesOn)}. Sign in again before then and the deletion is cancelled.",
                style = sans(13.sp, lineHeight = 18.2.sp),
                color = GraceBody,
            )
        }
    }
    Section("Erased after 14 days") {
        Card {
            listOf(
                R.drawable.ic_call to "Your phone number",
                R.drawable.ic_account_circle to "Your profile and photo",
                R.drawable.ic_receipt_long to "Payment screenshots you uploaded",
                R.drawable.ic_bookmark to "Leagues you follow",
            ).forEachIndexed { index, (icon, label) ->
                if (index > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(icon), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = sans(14.sp, lineHeight = 18.9.sp), color = colors.onBackground)
                }
            }
        }
    }
    Section("Kept") {
        Card {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Auction results and rosters of leagues that already ran. Your name shows as:",
                    style = sans(14.sp, lineHeight = 19.6.sp),
                    color = colors.onBackground,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeletedUserAvatar(size = 32.dp, iconSize = 18.dp)
                    Text("Deleted user", style = sans(13.5.sp, FontWeight.SemiBold), color = DeletedUserName)
                }
            }
        }
    }
    if (screen.handovers.isNotEmpty()) {
        Section("Leagues you organize") {
            Text(
                "When your account is erased, the earliest co-organizer becomes the organizer.",
                style = sans(13.sp, lineHeight = 18.2.sp),
                color = colors.onSurfaceVariant,
            )
            Card {
                screen.handovers.forEachIndexed { index, handover ->
                    if (index > 0) RowDivider()
                    HandoverRow(handover)
                }
            }
        }
    }
}

@Composable
private fun HandoverRow(handover: DeletionHandoverDto) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LeagueTile(handover.leagueId, handover.leagueName, handover.leagueLogoUrl, size = 36.dp, fontSize = 11.sp)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(handover.leagueName, style = sans(14.sp, FontWeight.SemiBold, 16.8.sp), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    "Passes to ${handover.newOrganizerName ?: "a co-organizer"}",
                    style = sans(12.5.sp, lineHeight = 15.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** D4. Each row opens that league; the reason line is the fix. */
@Composable
private fun BlockedContent(blockers: List<DeletionBlockerDto>, onOpenLeague: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Heading("You can't delete your account yet")
    Lead("These leagues need you first. Sort them out, then come back and try again.")
    Card {
        blockers.forEachIndexed { index, blocker ->
            if (index > 0) RowDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onOpenLeague(blocker.leagueId) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LeagueTile(blocker.leagueId, blocker.leagueName, blocker.leagueLogoUrl, size = 40.dp, fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(blocker.leagueName, style = sans(14.5.sp, FontWeight.SemiBold, 17.4.sp), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val (icon, tint, reason) = when (blocker.reason) {
                        DeletionBlockReason.AUCTION_LIVE -> Triple(R.drawable.ic_gavel, CrichereOnWarning, "Auction is live. Wait for it to end.")
                        DeletionBlockReason.NO_CO_ORGANIZER -> Triple(R.drawable.ic_person_add, colors.primary, "Add a co-organizer or mark the league completed.")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                        Text(reason, style = sans(13.sp, lineHeight = 17.55.sp), color = BodyInk)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = CrichereInkDisabled, modifier = Modifier.size(20.dp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text("Nothing has changed on your account.", style = sans(12.5.sp, lineHeight = 16.875.sp), color = colors.onSurfaceVariant)
    }
}

/** D5. */
@Composable
private fun ErrorState(onRetry: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 36.dp, end = 36.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text("Couldn't check your account", style = archivo(18.sp, FontWeight.Bold, 21.6.sp), color = colors.onBackground, textAlign = TextAlign.Center)
        Text(
            "Check your connection and try again. Nothing has been deleted.",
            style = sans(14.sp, lineHeight = 21.sp),
            color = BodyInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        CheckAgainButton("Retry", onRetry, Modifier.width(160.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = archivo(14.5.sp, FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp))
        content()
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() = HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

@Composable
private fun LeagueTile(id: String, name: String, logoUrl: String?, size: Dp, fontSize: TextUnit) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(tileColor(id)), contentAlignment = Alignment.Center) {
        Text(shortCode(name), style = archivo(fontSize, FontWeight.ExtraBold), color = Color.White)
        logoUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** The bottom action bar: hairline on top, 12/20/28 padding above the navigation bar. */
@Composable
private fun BottomBar(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)) { content() }
    }
}

/** D3's destructive 52 dp pill; greyed out while the check runs (D2). */
@Composable
private fun DeleteButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CrichereErrorStrong,
            contentColor = Color.White,
            disabledContainerColor = CrichereDisabledContainer,
            disabledContentColor = CrichereInkSubtle,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Delete account", style = sans(15.sp, FontWeight.SemiBold)) }
}

/** D4 "Check again" and D5 "Retry": 48 dp outlined pill with a refresh icon. */
@Composable
private fun CheckAgainButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(48.dp),
    ) {
        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = sans(14.5.sp, FontWeight.SemiBold))
    }
}
