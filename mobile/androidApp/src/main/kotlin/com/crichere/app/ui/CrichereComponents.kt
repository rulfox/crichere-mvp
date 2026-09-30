package com.crichere.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crichere.app.ui.theme.ArchivoFamily
import com.crichere.app.ui.theme.CrichereDisabledContainer
import com.crichere.app.ui.theme.CrichereInkSubtle
import com.crichere.app.ui.theme.InstrumentSansFamily

private val PillShape = RoundedCornerShape(26.dp)
private val PillLabel = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

/** Compact green wordmark used as the header on auth screens once the hero is gone. */
@Composable
fun CrichereWordmark(modifier: Modifier = Modifier) {
    Text(
        text = "Crichere",
        style = TextStyle(
            fontFamily = ArchivoFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.84).sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/**
 * Full-width 52dp green pill. While [loading] it stays green (at 85% opacity, per the design's
 * in-flight state) instead of greying out; callers' ViewModels already ignore repeat taps.
 */
@Composable
fun CricherePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingText: String = text,
) {
    Button(
        onClick = onClick,
        enabled = enabled || loading,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = CrichereDisabledContainer,
            disabledContentColor = CrichereInkSubtle,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (loading) 0.85f else 1f),
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
                Text(loadingText, style = PillLabel)
            }
        } else {
            Text(text, style = PillLabel)
        }
    }
}

/** Full-width 52dp outlined pill (secondary action). */
@Composable
fun CrichereOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = PillShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(text, style = PillLabel)
    }
}

/** Icon + title + body callout (error / warning banners). */
@Composable
fun NoticeCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    containerColor: Color,
    titleColor: Color,
    bodyColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = titleColor,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.8.sp),
                color = titleColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = TextStyle(fontFamily = InstrumentSansFamily, fontSize = 12.5.sp, lineHeight = 17.5.sp),
                color = bodyColor,
            )
        }
    }
}
