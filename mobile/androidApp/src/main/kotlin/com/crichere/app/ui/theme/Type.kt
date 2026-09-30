package com.crichere.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.crichere.app.R

// Archivo (headlines, 700/800), Instrument Sans (UI text), JetBrains Mono (money, phone, OTP) --
// see assets/README.md. Variable fonts, referenced as a single weight-agnostic FontFamily;
// FontWeight on individual TextStyles below drives the rendered weight.
val ArchivoFamily = FontFamily(Font(R.font.archivo_variable))
val InstrumentSansFamily = FontFamily(Font(R.font.instrument_sans_variable))
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono_variable))

val CrichereTypography =
    Typography(
        displayLarge = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold),
        displayMedium = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold),
        displaySmall = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold),
        headlineLarge = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold),
        headlineMedium = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = InstrumentSansFamily, fontWeight = FontWeight.Medium),
    )

// Money, phone numbers, OTP digits -- not part of Material3's Typography scale, applied directly
// where those specific strings render (e.g. league fees, phone fields, OTP boxes).
val CrichereMonoTextStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Medium)
