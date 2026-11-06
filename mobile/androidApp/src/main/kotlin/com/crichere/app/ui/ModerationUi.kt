package com.crichere.app.ui

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.moderation.BlockTarget
import com.crichere.app.moderation.BlockViewModel
import com.crichere.app.moderation.BlockedPeopleViewModel
import com.crichere.app.moderation.BlockedRow
import com.crichere.app.moderation.ModerationNoticeDto
import com.crichere.app.moderation.ModerationNoticeKind
import com.crichere.app.moderation.ReportReason
import com.crichere.app.moderation.ReportState
import com.crichere.app.moderation.ReportStep
import com.crichere.app.moderation.ReportTargetType
import com.crichere.app.moderation.ReportViewModel
import com.crichere.app.ui.theme.CrichereErrorStrong
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

internal const val TERMS_URL = "https://crichere.com/terms"
internal const val PRIVACY_URL = "https://crichere.com/privacy"

/** The auction note on boards V1 and V2. */
internal const val AUCTION_MONEY_NOTE = "Bids are recorded here only. No money moves through Crichere."

/** Boards T1 and T2: the in-app browser, so the sign-in screen keeps what was typed and ticked. */
internal fun openLegalPage(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
}

/** Board R2: the quiet ⋮ on someone else's roster row, and its menu. Block is the only red item. */
@Composable
internal fun ModerationRowMenu(name: String, blocked: Boolean, onReport: () -> Unit, onBlock: () -> Unit, onUnblock: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { open = true }
                .semantics { contentDescription = "More options for $name" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = colors.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.width(208.dp),
        ) {
            Text(name, style = pText(12.sp), color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp))
            DropdownMenuItem(
                text = { Text("Report", style = pText(15.sp), color = colors.onBackground) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_flag), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                onClick = { open = false; onReport() },
            )
            if (blocked) {
                DropdownMenuItem(
                    text = { Text("Unblock", style = pText(15.sp), color = colors.onBackground) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_block), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                    onClick = { open = false; onUnblock() },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Block", style = pText(15.sp), color = CrichereErrorStrong) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_block), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.size(20.dp)) },
                    onClick = { open = false; onBlock() },
                )
            }
        }
    }
}

/** Board R12a: a removed league or franchise logo. No label, so nobody is publicly flagged. */
@Composable
internal fun NeutralLogo(size: Dp, iconSize: Dp, corner: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(DeletedAvatarBackground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = DeletedAvatarIcon, modifier = Modifier.size(iconSize))
    }
}

/** Boards R3 and R3e to R3g: quiet links at the end of the page, never near Share and Follow. Null hides a link. */
@Composable
internal fun LeagueEndLinks(onReportLeague: (() -> Unit)?, onBlockOrganizer: (() -> Unit)?) {
    if (onReportLeague == null && onBlockOrganizer == null) return
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        onReportLeague?.let { QuietLink(R.drawable.ic_flag, "Report league", it) }
        onBlockOrganizer?.let { QuietLink(R.drawable.ic_block, "Block organizer", it) }
    }
}

@Composable
private fun QuietLink(icon: Int, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = pText(13.sp, FontWeight.Medium), color = colors.onSurfaceVariant)
    }
}

private fun ReportReason.label(type: ReportTargetType?): String = when (this) {
    ReportReason.INAPPROPRIATE_IMAGE -> if (type == ReportTargetType.LEAGUE) "Inappropriate image or text" else "Inappropriate photo or logo"
    ReportReason.HATE_OR_HARASSMENT -> "Hate or harassment"
    ReportReason.IMPERSONATION -> "Pretending to be someone else"
    ReportReason.UNDER_18 -> "Under 18"
    ReportReason.SPAM_OR_MISLEADING -> "Spam or misleading"
    ReportReason.BETTING_OR_GAMBLING -> "Betting or gambling"
    ReportReason.FAKE_OR_SCAM_LEAGUE -> "Fake or scam league"
    ReportReason.SOMETHING_ELSE -> "Something else"
}

/** Boards R4 to R6. Can't be dismissed while sending (R5e). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSheet(viewModel: ReportViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subject = state.subject ?: return
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !viewModel.state.value.isSubmitting },
    )
    ModalBottomSheet(
        onDismissRequest = viewModel::close,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            when (state.step) {
                ReportStep.REASON -> ReasonStep(state, viewModel)
                ReportStep.DETAILS -> DetailsStep(state, viewModel)
                ReportStep.SENT -> {
                    val line = if (subject.type == ReportTargetType.LEAGUE) {
                        "The organizer won't be told who reported this league. If something breaks our rules, we'll remove it."
                    } else {
                        "${subject.name.substringBefore(' ')} won't be told who reported them. If something breaks our rules, we'll remove it."
                    }
                    ReportOutcome(R.drawable.ic_check, "Thanks. Our team will review this.", line, viewModel::close)
                }
                ReportStep.ALREADY_REPORTED -> ReportOutcome(
                    R.drawable.ic_hourglass_top,
                    "You've already reported this. Our team will review it.",
                    "Your earlier report is still open, so this one wasn't sent again.",
                    viewModel::close,
                    written = state.details.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}

@Composable
private fun ReasonStep(state: ReportState, viewModel: ReportViewModel) {
    val colors = MaterialTheme.colorScheme
    val subject = state.subject ?: return
    val person = subject.type == ReportTargetType.USER
    Text(if (person) "REPORT A PERSON" else "REPORT A LEAGUE", style = pText(11.sp, FontWeight.Medium), color = colors.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Text(subject.name, style = pText(17.sp, FontWeight.SemiBold), color = colors.onBackground)
    Spacer(Modifier.height(4.dp))
    Text(subject.context, style = pText(13.sp), color = colors.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
    Text("What's the problem?", style = pText(14.sp, FontWeight.Medium), color = colors.onBackground)
    Spacer(Modifier.height(4.dp))
    state.reasons.forEach { reason ->
        Row(
            Modifier.fillMaxWidth().height(52.dp).clickable { viewModel.selectReason(reason) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = state.reason == reason, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.primary))
            Spacer(Modifier.width(14.dp))
            Text(reason.label(subject.type), style = pText(15.sp), color = colors.onBackground)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        if (person) {
            "${subject.name.substringBefore(' ')} won't be told who reported them. The organizer doesn't see reports. The Crichere team reviews each one."
        } else {
            "The organizer won't be told who reported this league, and doesn't see reports. The Crichere team reviews each one."
        },
        style = pText(12.sp, lineHeight = 18.sp),
        color = colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    CricherePrimaryButton(text = "Next", onClick = viewModel::next, enabled = state.reason != null)
}

@Composable
private fun DetailsStep(state: ReportState, viewModel: ReportViewModel) {
    val colors = MaterialTheme.colorScheme
    val subject = state.subject ?: return
    val atLimit = state.details.length >= ReportState.MAX_DETAILS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(enabled = !state.isSubmitting, onClick = viewModel::back),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = colors.onBackground, modifier = Modifier.size(22.dp)) }
        Text("Report ${subject.name}", style = pText(17.sp, FontWeight.SemiBold), color = colors.onBackground, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    state.reason?.let {
        Text(
            it.label(subject.type),
            style = pText(12.sp, FontWeight.Medium),
            color = colors.onBackground,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.surfaceVariant).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    Row {
        Text(if (state.detailsRequired) "Tell us what's wrong" else "Add details", style = pText(14.sp, FontWeight.Medium), color = colors.onBackground, modifier = Modifier.weight(1f))
        Text(if (state.detailsRequired) "Required" else "Optional", style = pText(13.sp), color = colors.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    BasicTextField(
        value = state.details,
        onValueChange = viewModel::onDetailsChanged,
        enabled = !state.isSubmitting,
        textStyle = pText(15.sp, lineHeight = 22.5.sp).copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .semantics { contentDescription = "Report details" },
        decorationBox = { inner ->
            if (state.details.isEmpty()) {
                Text(
                    if (state.detailsRequired) "Describe the problem in a sentence or two." else "What should our team look at?",
                    style = pText(15.sp, lineHeight = 22.5.sp),
                    color = colors.onSurfaceVariant,
                )
            }
            inner()
        },
    )
    Spacer(Modifier.height(8.dp))
    Row {
        Text(
            when {
                atLimit -> "That's the limit. Extra text isn't added."
                state.detailsRequired -> "Needed so the team knows what to check."
                else -> "Helps the team find it faster."
            },
            style = pText(12.sp),
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text("${state.details.length} / ${ReportState.MAX_DETAILS}", style = pText(12.sp), color = colors.onSurfaceVariant)
    }
    if (subject.type == ReportTargetType.USER) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !state.isSubmitting) { viewModel.setAlsoBlock(!state.alsoBlock) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.alsoBlock, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.primary))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Also block this person", style = pText(15.sp), color = colors.onBackground)
                Text("Hides their photo and uploads from you. They aren't told.", style = pText(12.sp), color = colors.onSurfaceVariant)
            }
        }
    }
    if (state.failed) {
        Spacer(Modifier.height(10.dp))
        Text("Your report didn't send. Your text is still here.", style = pText(13.sp), color = colors.error)
    }
    Spacer(Modifier.height(14.dp))
    CricherePrimaryButton(
        text = if (state.failed) "Retry" else "Send report",
        onClick = viewModel::send,
        enabled = state.canSend,
        loading = state.isSubmitting,
        loadingText = "Sending…",
    )
}

@Composable
private fun ReportOutcome(icon: Int, title: String, body: String, onDone: () -> Unit, written: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = pText(17.sp, FontWeight.SemiBold, 22.sp), color = colors.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(body, style = pText(14.sp, lineHeight = 21.sp), color = colors.onSurfaceVariant)
    }
    if (written != null) {
        Spacer(Modifier.height(14.dp))
        Text("WHAT YOU WROTE", style = pText(11.sp, FontWeight.Medium), color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(written, style = pText(14.sp, lineHeight = 21.sp), color = colors.onBackground)
    }
    Spacer(Modifier.height(18.dp))
    CricherePrimaryButton(text = "Done", onClick = onDone)
}

/** Boards R7a to R7e. [coOrganizerOfThisLeague] adds the R7e note. */
@Composable
internal fun BlockDialog(viewModel: BlockViewModel, coOrganizerOfThisLeague: (String) -> Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target = state.confirming ?: return
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = viewModel::cancel,
        containerColor = colors.surface,
        title = { Text("Block ${target.name}?", style = pText(21.sp, FontWeight.Bold, 26.sp), color = colors.onBackground) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    "Their photo and uploads are hidden from you.",
                    "Leagues they organize leave your dashboard, unless you've joined them.",
                    "Their name still appears in leagues you share, so results stay correct.",
                    "They aren't told.",
                ).forEach { Text(it, style = pText(14.sp, lineHeight = 21.sp), color = colors.onBackground) }
                if (coOrganizerOfThisLeague(target.userId)) {
                    Text(
                        "${target.name.substringBefore(' ')} co-organizes this league. It stays on your dashboard because only leagues they run as main organizer are removed.",
                        style = pText(13.sp, lineHeight = 19.sp),
                        color = colors.onBackground,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surfaceVariant).padding(12.dp),
                    )
                }
                Text("You can both still join the same leagues. Unblock any time from My Profile.", style = pText(12.sp, lineHeight = 18.sp), color = colors.onSurfaceVariant)
                if (state.failed) {
                    Text(
                        "Couldn't block ${target.name.substringBefore(' ')}. Check your connection and try again. Nothing has changed yet.",
                        style = pText(13.sp, lineHeight = 19.sp),
                        color = colors.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirm, enabled = !state.isSubmitting) {
                Text(
                    when {
                        state.isSubmitting -> "Blocking…"
                        state.failed -> "Try again"
                        else -> "Block"
                    },
                    style = pText(14.sp, FontWeight.SemiBold),
                    color = CrichereErrorStrong,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancel, enabled = !state.isSubmitting) {
                Text("Cancel", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary)
            }
        },
    )
}

/** Boards R7d and R11g: 6 seconds, with Undo. */
@Composable
internal fun BlockSnackbar(viewModel: BlockViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blocked = state.justBlocked
    val unblocked = state.justUnblocked
    LaunchedEffect(blocked, unblocked) {
        if (blocked != null || unblocked != null) {
            delay(6_000)
            viewModel.dismissSnackbar()
        }
    }
    when {
        blocked != null -> CrichereSnackbar("${blocked.name} blocked", modifier, actionLabel = "Undo", onAction = viewModel::undoBlock)
        unblocked != null -> CrichereSnackbar("${unblocked.name} unblocked", modifier, actionLabel = "Undo", onAction = viewModel::undoUnblock)
    }
}

/** Board R9a: the league's details stay hidden until the organizer is unblocked. */
@Composable
internal fun BlockedOrganizerNotice(organizerName: String, onUnblock: () -> Unit, onHome: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text("You blocked this league's organizer", style = pText(22.sp, FontWeight.Bold, 28.sp), color = colors.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            "${organizerName.replaceFirstChar(Char::uppercase).replace("This organizer", "This person")} organizes this league. While they're blocked, its details stay hidden from you. They haven't been told.",
            style = pText(15.sp, lineHeight = 23.sp),
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onUnblock,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            border = BorderStroke(1.dp, colors.outline),
        ) { Text("Unblock $organizerName", style = pText(15.sp, FontWeight.Medium), color = colors.onBackground) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Go to Home", style = pText(15.sp, FontWeight.Medium), color = colors.primary)
        }
    }
}

/** Board R9b, for a member. */
@Composable
internal fun BlockedOrganizerBanner(organizerName: String, onUnblock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "You blocked $organizerName, who organizes this league. Their logo, banner and description are hidden.",
            style = pText(13.sp, lineHeight = 19.sp),
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUnblock) { Text("Unblock", style = pText(13.sp, FontWeight.SemiBold), color = colors.primary) }
    }
}

/** Boards R11a to R11g. Avatars are always neutral: this screen must not show the photo the user asked to stop seeing. */
@Composable
internal fun BlockedPeopleScreen(onBack: () -> Unit, viewModel: BlockedPeopleViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(state.justUnblocked) {
        if (state.justUnblocked != null) {
            delay(6_000)
            viewModel.dismissSnackbar()
        }
    }
    Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            BackTitleBar("Blocked people", onBack)
            when {
                state.isLoading -> Unit
                state.loadFailed -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("Couldn't load your list", style = pText(18.sp, FontWeight.SemiBold), color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Text("Check your connection and try again. Everyone you blocked is still blocked.", style = pText(14.sp, lineHeight = 21.sp), color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    CricherePrimaryButton(text = "Retry", onClick = viewModel::load)
                }
                state.rows.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_block), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No one is blocked", style = pText(18.sp, FontWeight.SemiBold), color = colors.onBackground)
                    Spacer(Modifier.height(8.dp))
                    Text("To block someone, open ⋮ on their row in any league roster.", style = pText(14.sp, lineHeight = 21.sp), color = colors.onSurfaceVariant)
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Their photo and uploads are hidden from you. They aren't told, and their names still appear in leagues you share.",
                        style = pText(13.sp, lineHeight = 19.sp),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    state.rows.forEach { row -> BlockedPersonRow(row) { viewModel.unblock(row.person.userId) } }
                }
            }
        }
        state.justUnblocked?.let {
            CrichereSnackbar(
                "${it.name ?: "Person"} unblocked",
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                actionLabel = "Undo",
                onAction = viewModel::undo,
            )
        }
    }
}

@Composable
private fun BlockedPersonRow(row: BlockedRow, onUnblock: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = row.person.name ?: "Person"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeletedUserAvatar(size = 40.dp, iconSize = 22.dp, description = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = pText(15.sp, FontWeight.Medium), color = colors.onBackground)
                Text("Blocked ${blockedOn(row.person.blockedAt)}", style = pText(12.sp), color = colors.onSurfaceVariant)
            }
            OutlinedButton(onClick = onUnblock, enabled = !row.isUnblocking, border = BorderStroke(1.dp, colors.outline)) {
                Text(
                    when {
                        row.isUnblocking -> "Unblocking…"
                        row.failed -> "Retry"
                        else -> "Unblock"
                    },
                    style = pText(13.sp, FontWeight.Medium),
                    color = colors.onBackground,
                )
            }
        }
        if (row.failed) {
            Text(
                "Couldn't unblock. ${name.substringBefore(' ')} is still blocked.",
                style = pText(12.sp),
                color = colors.error,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            )
        }
    }
}

private fun blockedOn(isoInstant: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("d MMM").format(
        java.time.Instant.parse(isoInstant).atZone(java.time.ZoneId.systemDefault()),
    )
}.getOrDefault("")

/** Boards R13a to R13c: one way out. Neutral, because this is a state, not something the user got wrong. */
@Composable
internal fun SuspendedScreen(maskedPhone: String?, onBackToSignIn: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    androidx.activity.compose.BackHandler(onBack = onBackToSignIn)
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
            Text("Your account is suspended", style = pText(26.sp, FontWeight.Bold, 31.sp), color = colors.onBackground)
            Text("Contact hello@crichere.com.", style = pText(16.sp, lineHeight = 25.sp), color = colors.onBackground)
            if (maskedPhone != null) Text(maskedPhone, style = pText(13.sp), color = colors.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = onBackToSignIn,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            border = BorderStroke(1.dp, colors.outline),
        ) { Text("Back to sign in", style = pText(15.sp, FontWeight.Medium), color = colors.onBackground) }
    }
}

/** Boards R12b to R12e, one dialog at a time. */
@Composable
internal fun ModerationNoticeDialog(notice: ModerationNoticeDto, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val subject = notice.subjectName
    val (title, body, next) = when (notice.kind) {
        ModerationNoticeKind.PHOTO -> Triple("Your photo was removed", "Your photo", "You can upload a new photo from Edit profile.")
        ModerationNoticeKind.LEAGUE_LOGO -> Triple("Your league logo was removed", "Your logo for $subject", "You can upload a new logo from Edit league.")
        ModerationNoticeKind.LEAGUE_BANNER -> Triple("Your league banner was removed", "Your banner for $subject", "You can upload a new banner from Edit league.")
        ModerationNoticeKind.FRANCHISE_LOGO -> Triple("Your franchise logo was removed", "Your logo for $subject", null)
    }
    AlertDialog(
        onDismissRequest = {},
        containerColor = colors.surface,
        title = { Text(title, style = pText(19.sp, FontWeight.SemiBold, 24.sp), color = colors.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "$body was removed because it broke our rules. Contact hello@crichere.com if you think this is a mistake.",
                    style = pText(14.sp, lineHeight = 21.sp),
                    color = colors.onBackground,
                )
                if (next != null) Text(next, style = pText(13.sp, lineHeight = 19.sp), color = colors.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", style = pText(14.sp, FontWeight.SemiBold), color = colors.primary) } },
    )
}

/** Keeps [Color] out of the screens: the neutral tile for a hidden franchise logo on the dark auction. */
internal val HiddenLogoTile = Color(0xFF1D262E)

internal fun BlockTarget.firstName(): String = name.substringBefore(' ')
