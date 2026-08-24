package com.joeabouserhal.financetracker.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.joeabouserhal.financetracker.R

// OFL-licensed fonts bundled in res/font.
// Archivo Black = display / headline slab, Space Grotesk = body,
// IBM Plex Mono = numbers, labels, buttons.
val ArchivoBlack = FontFamily(Font(R.font.archivo_black, FontWeight.Normal))

val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk_variable, FontWeight.Normal))

val IbmPlexMono =
  FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
  )

// Sizes are deliberately generous — brutalist UI reads best big.
val AppTypography =
  Typography(
    displayLarge =
      TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 60.sp, lineHeight = 60.sp),
    displayMedium =
      TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 48.sp),
    displaySmall =
      TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge =
      TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium =
      TextStyle(fontFamily = ArchivoBlack, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall =
      TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge =
      TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium =
      TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall =
      TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge =
      TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium =
      TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall =
      TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge =
      TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
      ),
  )
