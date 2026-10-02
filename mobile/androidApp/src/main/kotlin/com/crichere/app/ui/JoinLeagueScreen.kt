package com.crichere.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.league.JoinLeagueState
import com.crichere.app.league.JoinLeagueViewModel
import com.crichere.app.league.LeagueDto
import com.crichere.app.profile.BattingStyle
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereErrorStrong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Resolves [JoinLeagueViewModel] via Koin, parameterized on [leagueId] -- see `AppRoute.JoinLeague` (ui/navigation). */
@Composable
internal fun JoinLeagueRoute(
    leagueId: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: JoinLeagueViewModel = koinViewModel(key = "join-league:$leagueId") { parametersOf(leagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }
    LaunchedEffect(state.joined) { if (state.joined) onDone() }

    JoinLeagueScreen(state = state, viewModel = viewModel, onCancel = onCancel)
}

/**
 * Join as player (design board screen F): fee + "Pay via UPI" (with the no-UPI-app and
 * no-UPI-ID fallbacks), payment screenshot with a tick-the-checklist preview and upload progress,
 * the free-league summary, and the refunds/disputes disclaimer -- see docs/PHASE3.md.
 */
@Composable
private fun JoinLeagueScreen(state: JoinLeagueState, viewModel: JoinLeagueViewModel, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<PickedPhoto?>(null) }
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var noUpiApp by remember { mutableStateOf(false) }
    var copiedTick by remember { mutableIntStateOf(0) }
    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copiedTick) {
        if (copiedTick > 0) {
            showCopied = true
            delay(3_000)
            showCopied = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { picked = withContext(Dispatchers.IO) { decodePickedPhoto(context, uri) } }
    }
    val choose = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.statusBarsPadding()) { FlowTopBar("Join as player", onClose = onCancel) }
            val league = state.league
            when {
                league == null && state.loadFailed -> LoadError(onRetry = viewModel::retry)
                league == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 19.dp, end = 19.dp, top = 6.dp, bottom = 20.dp),
                    ) {
                        val fee = league.playerFee
                        if (fee != null) {
                            LeagueSummaryRow(league.id, league.name, league.logoUrl, league.startsOn, league.format)
                            Spacer(Modifier.height(12.dp))
                            PayToCard(
                                feeLabel = "Player fee",
                                fee = fee,
                                upiId = league.organizerUpiId,
                                noUpiApp = noUpiApp,
                                copied = copiedTick > 0,
                                onPayViaUpi = {
                                    val upi = league.organizerUpiId ?: return@PayToCard
                                    noUpiApp = !launchUpiPayment(context, upi, league.name, fee, "Player fee for ${league.name}")
                                },
                                onCopyUpiId = {
                                    league.organizerUpiId?.let { copyToClipboard(context, "UPI ID", it) }
                                    copiedTick++
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                            PaymentProofCard(
                                state = proofState(state),
                                thumbnail = thumbnail,
                                onChoose = choose,
                                onCancel = {
                                    viewModel.cancelUpload()
                                    thumbnail = null
                                },
                                onRetry = viewModel::retryUpload,
                                onRemove = {
                                    viewModel.removeScreenshot()
                                    thumbnail = null
                                },
                                onView = {},
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Crichere doesn't process payment or handle refunds/disputes -- that's between you and the organizer directly.",
                                style = pText(11.5.sp, lineHeight = 16.675.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FreeLeagueSummary(league, state)
                        }
                    }
                    JoinBar(state, league, onJoin = viewModel::submit)
                }
            }
        }

        AnimatedVisibility(
            visible = showCopied,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 13.dp, end = 13.dp, bottom = 100.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("UPI ID copied", style = pText(14.sp, FontWeight.Medium), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    picked?.let { photo ->
        val league = state.league
        ScreenshotCheckSheet(
            bitmap = photo.bitmap,
            checklist = listOfNotNull(
                league?.playerFee?.let { "Amount ${rupees(it)}" },
                league?.organizerUpiId?.let { "UPI ID $it" },
                "Reference / UTR no.",
            ),
            onDismiss = { picked = null },
            onChooseAnother = {
                picked = null
                choose()
            },
            onUse = {
                picked = null
                thumbnail = photo.bitmap.asImageBitmap()
                scope.launch {
                    val bytes = withContext(Dispatchers.Default) { encodeScreenshot(photo.bitmap) }
                    viewModel.uploadScreenshot(bytes, "image/jpeg", photo.fileName.substringBeforeLast('.') + ".jpg")
                }
            },
        )
    }
}

private fun proofState(state: JoinLeagueState): ProofState = when {
    state.isUploadingScreenshot -> ProofState.Uploading(state.uploadProgress, fileCaption(state.uploadFileName, state.uploadSizeBytes))
    state.uploadFailed -> ProofState.Failed
    state.screenshotUrl != null -> ProofState.Attached(fileCaption(state.uploadFileName, state.uploadSizeBytes))
    else -> ProofState.Empty
}

/** Design F6: free league -- no payment step, just who's joining. */
@Composable
private fun FreeLeagueSummary(league: LeagueDto, state: JoinLeagueState) {
    val colors = MaterialTheme.colorScheme
    Text(league.name, style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp), color = colors.onBackground)
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth().background(colors.primaryContainer, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_celebration), null, tint = colors.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("No entry fee", style = pText(14.5.sp, FontWeight.SemiBold), color = colors.onPrimaryContainer)
            Spacer(Modifier.height(3.dp))
            Text("You'll be added straight to the player pool.", style = pText(12.5.sp, lineHeight = 16.25.sp), color = Color(0xFF2E4A31))
        }
    }
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        SummaryRow("Joining as", state.viewerName ?: "You")
        HorizontalDivider(thickness = 1.dp, color = colors.surfaceVariant)
        val role = listOfNotNull(state.viewerRole?.label(), state.viewerBatting?.shortLabel()).joinToString(" · ")
        SummaryRow("Role", role.ifEmpty { "Not set" })
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = pText(13.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = pText(13.5.sp), color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun BattingStyle.shortLabel() = when (this) {
    BattingStyle.RIGHT_HAND -> "Right-hand"
    BattingStyle.LEFT_HAND -> "Left-hand"
}

/** Bottom bar: the join-failure banner (F7) above the Join button. */
@Composable
private fun JoinBar(state: JoinLeagueState, league: LeagueDto, onJoin: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        Column(Modifier.padding(start = 19.dp, end = 19.dp, top = 12.dp, bottom = 24.dp)) {
            val message = state.errorMessage
            if (message != null) {
                Row(
                    Modifier.fillMaxWidth().background(colors.errorContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_error), null, tint = colors.error, modifier = Modifier.padding(top = 1.dp).size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        state.errorTitle?.let {
                            Text(it, style = pText(13.5.sp, FontWeight.SemiBold), color = CrichereErrorStrong)
                            Spacer(Modifier.height(3.dp))
                        }
                        Text(message, style = pText(12.5.sp, lineHeight = 16.25.sp), color = CrichereErrorStrong)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            CricherePrimaryButton(
                text = "Join",
                onClick = onJoin,
                enabled = league.playerFee == null || state.screenshotUrl != null,
                loading = state.isSubmitting,
                loadingText = "Joining…",
            )
        }
    }
}
