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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crichere.app.R
import com.crichere.app.league.LeagueRoleDto
import com.crichere.app.league.ManageRolesState
import com.crichere.app.league.ManageRolesViewModel
import com.crichere.app.league.RoleNotice
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereErrorStrong
import com.crichere.app.ui.theme.CrichereGreenContainer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val RowAvatar = Color(0xFFE3E8DD)
private val RevokeBorder = Color(0xFFD9B8B2)
private val RevokeTrack = Color(0xFFEBD6D2)
private val DialogSurface = Color(0xFFF1F4EE)
private val DialogBody = Color(0xFF3E4A41)

/** Resolves [ManageRolesViewModel] via Koin, parameterized on [leagueId] -- see `AppRoute.ManageRoles` (ui/navigation). */
@Composable
internal fun ManageRolesRoute(
    leagueId: String,
    onBack: () -> Unit,
    viewModel: ManageRolesViewModel = koinViewModel(key = "manage-roles:$leagueId") { parametersOf(leagueId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.retry() }

    ManageRolesScreen(state = state, viewModel = viewModel, onBack = onBack)
}

/**
 * Design K1-K8 (see docs/PHASE7.md): look up another registered user by phone number, confirm,
 * then grant them full organizer authority over this league; below that, the current
 * co-organizers, each revocable after its own confirmation. Both confirmations are deliberate
 * given what's being handed over or taken away -- not a bare tap.
 */
@Composable
private fun ManageRolesScreen(state: ManageRolesState, viewModel: ManageRolesViewModel, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var showGrantConfirm by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<LeagueRoleDto?>(null) }

    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        BackTitleBar("Manage Co-Organizers", onBack)
        val league = state.league
        when {
            state.isLoading && league == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            league == null -> LoadError(onRetry = viewModel::retry, title = "Couldn't load co-organizers")
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 19.dp, end = 19.dp, top = 4.dp, bottom = 24.dp),
            ) {
                Text(
                    "A co-organizer can do everything you can do for this league.",
                    style = pText(12.5.sp, lineHeight = 17.5.sp),
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp)) // + the field's 7dp notch reserve = the board's 12dp
                LookupRow(
                    state = state,
                    onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                    onLookup = {
                        focusManager.clearFocus()
                        viewModel.lookup()
                    },
                )
                state.lookupResult?.let { found ->
                    Spacer(Modifier.height(12.dp))
                    FoundCard(name = found.name ?: "Unnamed user", isGranting = state.isGranting, onGrant = { showGrantConfirm = true })
                }
                state.grantError?.let {
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(it)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Current co-organizers",
                    style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, lineHeight = 14.5.sp),
                    color = colors.onBackground,
                )
                state.revokeError?.let {
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(it)
                }
                Spacer(Modifier.height(12.dp))
                if (league.coOrganizers.isEmpty()) {
                    Text("No co-organizers yet.", style = pText(13.sp, lineHeight = 18.2.sp), color = colors.onSurfaceVariant)
                } else {
                    CoOrganizerList(roles = league.coOrganizers, revokingRoleIds = state.revokingRoleIds, onRevoke = { pendingRevoke = it })
                }
            }
        }
    }

    if (showGrantConfirm) {
        val name = state.lookupResult?.name ?: "This user"
        ConfirmDialog(
            title = "Grant co-organizer access?",
            body = "$name will be able to do everything you can do for this league, including editing it and running the auction.",
            confirmLabel = "Grant",
            confirmColor = colors.primary,
            onDismiss = { showGrantConfirm = false },
            onConfirm = {
                showGrantConfirm = false
                viewModel.grant()
            },
        )
    }
    pendingRevoke?.let { role ->
        ConfirmDialog(
            title = "Revoke ${role.name ?: "this co-organizer"}?",
            body = "They'll lose co-organizer access to this league right away.",
            confirmLabel = "Revoke",
            confirmColor = CrichereErrorStrong,
            onDismiss = { pendingRevoke = null },
            onConfirm = {
                pendingRevoke = null
                viewModel.revoke(role.id)
            },
        )
    }
}

/** Phone field + Look up pill (K1/K2/K4/K6); the pill widens to "Looking up…" and the field gives way. */
@Composable
private fun LookupRow(state: ManageRolesState, onPhoneNumberChanged: (String) -> Unit, onLookup: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CrichereTextField(
            value = state.phoneNumberInput,
            onValueChange = onPhoneNumberChanged,
            label = "Phone number",
            look = FieldVariant.Form,
            error = state.lookupError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onLookup() }),
            modifier = Modifier.weight(1f),
        )
        Pill(
            text = if (state.isLookingUp) "Looking up…" else "Look up",
            loading = state.isLookingUp,
            height = 52.dp,
            fontSize = 14.sp,
            horizontalPadding = 18.dp,
            onClick = onLookup,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun FoundCard(name: String, isGranting: Boolean, onGrant: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(start = 15.dp, end = 15.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, size = 38.dp, fontSize = 13.sp, background = CrichereGreenContainer)
        Spacer(Modifier.width(12.dp))
        Text("Found: $name", style = pText(14.sp, FontWeight.SemiBold, 16.8.sp), color = colors.onBackground, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Pill(
            text = if (isGranting) "Granting…" else "Grant",
            loading = isGranting,
            height = 36.dp,
            fontSize = 13.sp,
            horizontalPadding = if (isGranting) 12.dp else 15.dp,
            onClick = onGrant,
        )
    }
}

@Composable
private fun CoOrganizerList(roles: List<LeagueRoleDto>, revokingRoleIds: Set<String>, onRevoke: (LeagueRoleDto) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(14.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(1.dp),
    ) {
        roles.forEachIndexed { index, role ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.surfaceVariant)
            Row(
                Modifier.fillMaxWidth().height(61.dp).padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val name = role.name ?: "Unnamed user"
                Avatar(name, size = 34.dp, fontSize = 12.sp, background = RowAvatar)
                Spacer(Modifier.width(12.dp))
                Text(
                    name,
                    style = pText(13.5.sp, FontWeight.SemiBold, 13.5.sp),
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                if (role.id in revokingRoleIds) {
                    // K5: spinner + muted label in place of the pill.
                    Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = CrichereErrorStrong, trackColor = RevokeTrack, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Revoking…", style = pText(12.5.sp, FontWeight.SemiBold, 12.5.sp), color = colors.onSurfaceVariant)
                    }
                } else {
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, RevokeBorder, RoundedCornerShape(18.dp))
                            .clickable { onRevoke(role) }
                            .padding(horizontal = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Revoke", style = pText(12.5.sp, FontWeight.SemiBold, 12.5.sp), color = CrichereErrorStrong)
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String, size: Dp, fontSize: TextUnit, background: Color) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            initials(name),
            style = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = fontSize),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Green pill with an optional white spinner (Look up / Grant and their in-flight states). */
@Composable
private fun Pill(
    text: String,
    loading: Boolean,
    height: Dp,
    fontSize: TextUnit,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, trackColor = Color.White.copy(alpha = 0.35f), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = pText(fontSize, FontWeight.SemiBold, fontSize), color = Color.White, maxLines = 1)
    }
}

/** K8's red banner (also used for a failed revoke). */
@Composable
private fun ErrorBanner(notice: RoleNotice) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = CrichereErrorStrong, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(notice.title, style = pText(13.sp, FontWeight.SemiBold, 17.55.sp), color = CrichereErrorStrong)
            Spacer(Modifier.height(3.dp))
            Text(notice.message, style = pText(12.sp, lineHeight = 16.8.sp), color = CrichereErrorStrong)
        }
    }
}

/** K3 / K5: the board's 314dp dialog. */
@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, confirmColor: Color, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 23.dp),
        containerColor = DialogSurface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = pText(19.sp, FontWeight.SemiBold, 23.75.sp), color = MaterialTheme.colorScheme.onBackground) },
        text = { Text(body, style = pText(13.5.sp, lineHeight = 19.575.sp), color = DialogBody) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, style = pText(14.sp, FontWeight.SemiBold), color = confirmColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = pText(14.sp, FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary) }
        },
    )
}
