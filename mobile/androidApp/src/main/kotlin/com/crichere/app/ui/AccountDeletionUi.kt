package com.crichere.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.R
import com.crichere.app.ui.theme.InstrumentSansFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pieces shared by the account deletion flow (design board "Crichere Account Deletion", D1–D10).

/** D10: the neutral avatar's circle and person icon, and the muted name colour. */
internal val DeletedAvatarBackground = Color(0xFFE3E8DD)
internal val DeletedAvatarIcon = Color(0xFF8A948C)
internal val DeletedUserName = Color(0xFF5A665D)

/** D10b, on the dark auction screen: the same idea on the dark surface. */
internal val DeletedAvatarBackgroundDark = Color.White.copy(alpha = 0.08f)
internal val DeletedUserNameDark = Color(0xFF8FA094)

/**
 * D10: a deleted user never shows a photo or initials -- a grey shape with a filled person icon.
 * [shape] is a circle on light screens and an 8 dp square on the dark auction roster (D10b).
 */
@Composable
internal fun DeletedUserAvatar(
    size: Dp,
    iconSize: Dp,
    background: Color = DeletedAvatarBackground,
    tint: Color = DeletedAvatarIcon,
    shape: Shape = CircleShape,
) {
    Box(Modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_person_filled), contentDescription = "Deleted user", tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * The tinted notice with an icon, a message and a close button (D7 amber on Sign in, D9 green on
 * League detail): radius 16, padding 14, gap 12, text 500 13.5/18.9.
 */
@Composable
internal fun DismissibleNotice(
    @DrawableRes icon: Int,
    text: String,
    container: Color,
    ink: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(container, RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Text(
            text,
            style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.9.sp),
            color = ink,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Dismiss", tint = ink, modifier = Modifier.size(20.dp))
        }
    }
}

private val EraseDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** The server's ISO instant as the design's "25 Oct 2026", in the phone's time zone. */
internal fun eraseDate(isoInstant: String): String =
    runCatching { Instant.parse(isoInstant).atZone(ZoneId.systemDefault()).format(EraseDate) }.getOrDefault(isoInstant)
