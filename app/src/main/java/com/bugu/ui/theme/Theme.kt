package com.bugu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bugu.R

// ---- 品牌色板：暖调奶油底 + 黎明珊瑚 / 暮光靛蓝 / 鼠尾草薄荷 ----
object H {
    val Canvas = Color(0xFFFAF8F5)
    val Card = Color(0xFFFFFFFF)
    val Hairline = Color(0xFFEFECE6)
    val WarmFill = Color(0xFFF4F1EA)
    val Track = Color(0xFFEDE7E0)

    val Ink = Color(0xFF1E1B18)
    val Ink2 = Color(0xFF5C564E)
    val Ink3 = Color(0xFF9C9488)
    // 可读性增强的正文/强调色:原 Coral/Mint/Indigo/Rose 用作图标与大面积填充,
    // 13sp 及以上文字改用这些深色变体,确保白底/浅色胶囊底达到 4.5:1。
    val Ink3Strong = Color(0xFF6B655C)
    val CoralInk = Color(0xFFB83A1B)
    val IndigoInk = Color(0xFF4338CA)
    val MintInk = Color(0xFF047857)
    val RoseInk = Color(0xFFBE123C)
    val AmberInk = Color(0xFF92400E)

    val Coral = Color(0xFFFA5E38)
    val CoralLight = Color(0xFFFF7243)
    val Indigo = Color(0xFF6366F1)
    val IndigoLight = Color(0xFF818CF8)
    val Mint = Color(0xFF10B981)
    val Rose = Color(0xFFF43F5E)

    val CoralTint = Color(0x1AFA5E38)
    val IndigoTint = Color(0x1A6366F1)
    val MintTint = Color(0x1A10B981)
    val RoseTint = Color(0x1AF43F5E)
    val GreyTint = Color(0xFFF1ECE5)

    val CoralGradient = Brush.linearGradient(listOf(CoralLight, Coral))
}

private val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

private val Grotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold,
        fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1.2).sp,
        fontFeatureSettings = "tnum",
    ),
    displayMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-1).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
        fontSize = 17.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Normal,
        fontSize = 17.sp, lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 19.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.5.sp,
        fontFeatureSettings = "tnum",
    ),
)

private val AppColorScheme: ColorScheme = lightColorScheme(
    primary = H.Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF580E00),
    secondary = H.Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E0FF),
    onSecondaryContainer = Color(0xFF07006C),
    tertiary = H.Mint,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF6FFBBE),
    onTertiaryContainer = Color(0xFF002113),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = H.Canvas,
    onBackground = H.Ink,
    surface = H.Canvas,
    onSurface = H.Ink,
    surfaceVariant = Color(0xFFE9E1DC),
    onSurfaceVariant = H.Ink2,
    outline = Color(0xFF8E7069),
    outlineVariant = H.Hairline,
)

@Composable
fun BuguTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content,
    )
}
