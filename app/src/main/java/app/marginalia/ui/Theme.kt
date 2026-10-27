@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.marginalia.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marginalia.R
import app.marginalia.data.AppSettings

/** Five looks. Each is a full dark + light pair built from a few tuned colours. */
data class Palette(
    val id: String,
    val label: String,
    val blurb: String,
    val accent: Color,
    val accentLight: Color,
    val bgDark: Color,
    val textDark: Color,
    val bgLight: Color,
    val textLight: Color,
)

val Palettes = listOf(
    Palette("ink", "Ink", "Charcoal, paper text, vermilion mark", Color(0xFFFF6A47), Color(0xFFC8381B), Color(0xFF151413), Color(0xFFECE7DF), Color(0xFFF6F2EA), Color(0xFF1C1917)),
    Palette("paper", "Paper", "Warm book page, red pencil", Color(0xFFE8704F), Color(0xFFB83A1E), Color(0xFF1B1713), Color(0xFFEFE6D8), Color(0xFFF1E9DA), Color(0xFF241E17)),
    Palette("moss", "Moss", "Forest black, cream, lime marker", Color(0xFFB9D84A), Color(0xFF5F7A12), Color(0xFF0F1511), Color(0xFFE3EBDA), Color(0xFFEEF2E4), Color(0xFF16201A)),
    Palette("ember", "Ember", "Burnt brown, glowing amber", Color(0xFFF2A93B), Color(0xFFA85F00), Color(0xFF1B1210), Color(0xFFF2E3D0), Color(0xFFF7EBDB), Color(0xFF2A1B14)),
    Palette("mono", "Mono", "True black, white type, pink flare", Color(0xFFFF5C93), Color(0xFFC21F5E), Color(0xFF000000), Color(0xFFF3F3F3), Color(0xFFFAFAFA), Color(0xFF111111)),
)

fun paletteOf(id: String) = Palettes.firstOrNull { it.id == id } ?: Palettes.first()

private fun readableOn(c: Color) = if (c.luminance() > 0.45f) Color(0xFF1A1512) else Color(0xFFFFFFFF)
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

fun buildScheme(p: Palette, dark: Boolean, accentOverride: Long): ColorScheme {
    val accent = if (accentOverride != 0L) Color(accentOverride.toInt()) else if (dark) p.accent else p.accentLight
    val bg = if (dark) p.bgDark else p.bgLight
    val text = if (dark) p.textDark else p.textLight
    fun mix(t: Float) = lerp(bg, text, t)
    val onAccent = readableOn(accent)
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(bg, accent, 0.28f),
            onPrimaryContainer = lerp(accent, text, 0.55f),
            secondary = lerp(text, bg, 0.35f),
            onSecondary = bg,
            secondaryContainer = mix(0.14f),
            onSecondaryContainer = text,
            tertiary = Color(0xFFF2C14E),
            onTertiary = Color(0xFF241A00),
            tertiaryContainer = lerp(bg, Color(0xFFF2C14E), 0.25f),
            onTertiaryContainer = Color(0xFFFFE9A8),
            background = bg,
            onBackground = text,
            surface = bg,
            onSurface = text,
            surfaceVariant = mix(0.12f),
            onSurfaceVariant = lerp(text, bg, 0.3f),
            surfaceContainerLowest = lerp(bg, Color.Black, 0.35f),
            surfaceContainerLow = mix(0.04f),
            surfaceContainer = mix(0.07f),
            surfaceContainerHigh = mix(0.1f),
            surfaceContainerHighest = mix(0.14f),
            outline = mix(0.45f),
            outlineVariant = mix(0.2f),
            error = Color(0xFFFF8A80),
            onError = Color(0xFF3B0A06),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(bg, accent, 0.22f),
            onPrimaryContainer = lerp(accent, text, 0.6f),
            secondary = lerp(text, bg, 0.3f),
            onSecondary = bg,
            secondaryContainer = mix(0.1f),
            onSecondaryContainer = text,
            tertiary = Color(0xFF8A6500),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFE08C),
            onTertiaryContainer = Color(0xFF2B2000),
            background = bg,
            onBackground = text,
            surface = bg,
            onSurface = text,
            surfaceVariant = mix(0.09f),
            onSurfaceVariant = lerp(text, bg, 0.25f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(bg, text, 0.03f),
            surfaceContainer = lerp(bg, text, 0.055f),
            surfaceContainerHigh = lerp(bg, text, 0.08f),
            surfaceContainerHighest = lerp(bg, text, 0.11f),
            outline = mix(0.5f),
            outlineVariant = mix(0.18f),
            error = Color(0xFFB3261E),
            onError = Color.White,
        )
    }
}

// ---------- fonts ----------

private fun variable(res: Int, weights: List<Int>) = FontFamily(
    weights.map { w ->
        Font(
            resId = res,
            weight = FontWeight(w),
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontWeight(w), FontStyle.Normal),
        )
    },
)

val DisplayFont = variable(R.font.bricolage, listOf(300, 400, 500, 600, 700, 800))
val SerifFont = variable(R.font.newsreader, listOf(300, 400, 500, 600, 700))
val SansFont = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_regular, FontWeight.Medium),
    Font(R.font.atkinson_bold, FontWeight.SemiBold),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

fun readerFamily(setting: String): FontFamily = when (setting) {
    "sans" -> SansFont
    "system" -> FontFamily.Default
    else -> SerifFont
}

private fun TextStyle.with(f: FontFamily) = copy(fontFamily = f)

private fun typography(): Typography {
    val d = Typography()
    return d.copy(
        displayLarge = d.displayLarge.with(DisplayFont),
        displayMedium = d.displayMedium.with(DisplayFont),
        displaySmall = d.displaySmall.with(DisplayFont),
        headlineLarge = d.headlineLarge.with(DisplayFont),
        headlineMedium = d.headlineMedium.with(DisplayFont),
        headlineSmall = d.headlineSmall.with(DisplayFont),
        titleLarge = d.titleLarge.with(DisplayFont),
        titleMedium = d.titleMedium.with(DisplayFont),
        titleSmall = d.titleSmall.with(DisplayFont),
        bodyLarge = d.bodyLarge.with(SansFont),
        bodyMedium = d.bodyMedium.with(SansFont),
        bodySmall = d.bodySmall.with(SansFont),
        labelLarge = d.labelLarge.with(DisplayFont),
        labelMedium = d.labelMedium.with(DisplayFont),
        labelSmall = d.labelSmall.with(DisplayFont),
    )
}

// ---------- motion ----------

/** How bouncy our own animations are. The Material components follow the motion scheme. */
data class Spring(val damping: Float, val stiffness: Float) {
    fun <T> spec(): FiniteAnimationSpec<T> = spring(damping, stiffness)
}

val LocalSpring = staticCompositionLocalOf { Spring(0.62f, 380f) }

fun springFor(motion: String) = when (motion) {
    "calm" -> Spring(0.9f, 420f)
    "playful" -> Spring(0.42f, 300f)
    else -> Spring(0.62f, 380f)
}

val LocalSettings = staticCompositionLocalOf { AppSettings() }

@Composable
fun MarginaliaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val palette = paletteOf(settings.themePreset)
    val r = settings.roundness
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((6 * r).dp),
        small = RoundedCornerShape((10 * r).dp),
        medium = RoundedCornerShape((16 * r).dp),
        large = RoundedCornerShape((24 * r).dp),
        extraLarge = RoundedCornerShape((34 * r).dp),
    )
    CompositionLocalProvider(
        LocalSettings provides settings,
        LocalSpring provides springFor(settings.motion),
    ) {
        MaterialExpressiveTheme(
            colorScheme = buildScheme(palette, dark, settings.accentOverride),
            motionScheme = if (settings.motion == "calm") MotionScheme.standard() else MotionScheme.expressive(),
            shapes = shapes,
            typography = typography(),
            content = content,
        )
    }
}
