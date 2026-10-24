package com.aburv.kurippidu

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val DiaryHandwritingFont = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal)
)

/** Size of diary text, chosen in Settings. */
enum class DiaryFontSize(val size: TextUnit, val label: String) {
    SMALL(16.sp, "Small"),
    MEDIUM(18.sp, "Medium"),
    LARGE(22.sp, "Large")
}

val LocalDiaryTextSize = staticCompositionLocalOf { DiaryFontSize.MEDIUM.size }
