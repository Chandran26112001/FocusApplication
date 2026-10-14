package com.example.focusastra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.focusastra.R

val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

val Typography = Typography(
    headlineLarge = TextStyle(fontFamily = SpaceMono, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 39.sp),
    headlineMedium = TextStyle(fontFamily = SpaceMono, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = SpaceMono, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = SpaceMono, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = SpaceMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = SpaceMono, fontSize = 11.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = SpaceMono, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.5.sp),
)
