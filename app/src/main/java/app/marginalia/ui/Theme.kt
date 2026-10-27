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
    Palette("ocean", "Ocean", "Deep teal night, sky blue", Color(0xFF4FC3F7), Color(0xFF0B6A94), Color(0xFF0D1519), Color(0xFFDCEAF0), Color(0xFFEAF3F6), Color(0xFF13222A)),
    Palette("plum", "Plum", "Wine dark, orchid pencil", Color(0xFFE08AC8), Color(0xFF9A2F7B), Color(0xFF1A1018), Color(0xFFF0DDE9), Color(0xFFF8ECF3), Color(0xFF2A1422)),
    Palette("sand", "Sand", "Dune brown, brass", Color(0xFFD9A441), Color(0xFF8A6410), Color(0xFF17140E), Color(0xFFEDE5D0), Color(0xFFF4EEDD), Color(0xFF2A2415)),
    Palette("slate", "Slate", "Cool grey, steel blue", Color(0xFF8FB3D9), Color(0xFF2F5F8F), Color(0xFF12161B), Color(0xFFE1E7EE), Color(0xFFEDF1F5), Color(0xFF1A2128)),
    Palette("rose", "Rose", "Dusky red, soft pink", Color(0xFFFF8FA3), Color(0xFFC0304F), Color(0xFF1C1215), Color(0xFFF4E1E5), Color(0xFFFAEDEF), Color(0xFF2B1519)),
    Palette("mint", "Mint", "Pine night, fresh mint", Color(0xFF5EE0B0), Color(0xFF14805B), Color(0xFF0E1714), Color(0xFFDDEFE8), Color(0xFFE9F5F0), Color(0xFF13241D)),
    Palette("copper", "Copper", "Oiled wood, bright copper", Color(0xFFE39A6B), Color(0xFF9B4A1E), Color(0xFF1A1310), Color(0xFFEFE2D8), Color(0xFFF6ECE3), Color(0xFF2A1D14)),
    Palette("steel", "Steel", "Quiet grey, no fuss", Color(0xFFB0BEC5), Color(0xFF455A64), Color(0xFF101315), Color(0xFFE4E8EA), Color(0xFFEEF1F2), Color(0xFF182024)),
    Palette("citrus", "Citrus", "Olive dark, lemon marker", Color(0xFFFFD84D), Color(0xFF8F7400), Color(0xFF171509), Color(0xFFF2EDD0), Color(0xFFF7F3DC), Color(0xFF292410)),
)

/** Styles for a palette made from one colour. */
val PaletteStyles = listOf("tonal" to "Tonal", "vibrant" to "Vibrant", "neutral" to "Neutral", "mono" to "Mono")

private fun hsv(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

private fun hueSatOf(c: Color): Triple<Float, Float, Float> {
    val mx = maxOf(c.red, c.green, c.blue)
    val mn = minOf(c.red, c.green, c.blue)
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == c.red -> 60f * (((c.green - c.blue) / d) % 6)
        mx == c.green -> 60f * ((c.blue - c.red) / d + 2)
        else -> 60f * ((c.red - c.green) / d + 4)
    }.let { if (it < 0) it + 360f else it }
    return Triple(h, if (mx == 0f) 0f else d / mx, mx)
}

fun makePalette(id: String, name: String, seed: Color, style: String): Palette {
    val (h, s, v) = hueSatOf(seed)
    val tint = when (style) { "vibrant" -> 0.5f; "neutral" -> 0.05f; "mono" -> 0f; else -> 0.22f }
    val lightTint = when (style) { "vibrant" -> 0.16f; "neutral" -> 0.03f; "mono" -> 0f; else -> 0.07f }
    return Palette(
        id, name, "Made from one colour",
        accent = seed,
        accentLight = hsv(h, (s + 0.1f).coerceAtMost(1f), v.coerceAtMost(0.62f)),
        bgDark = hsv(h, tint, 0.10f),
        textDark = hsv(h, tint * 0.35f, 0.92f),
        bgLight = hsv(h, lightTint, 0.97f),
        textLight = hsv(h, (tint * 1.4f).coerceAtMost(1f), 0.13f),
    )
}

fun allPalettes(custom: List<app.marginalia.data.CustomPalette>): List<Palette> =
    Palettes + custom.map { makePalette(it.id, it.name, Color(it.seed.toInt()), it.style) }

fun paletteOf(id: String, custom: List<app.marginalia.data.CustomPalette> = emptyList()) =
    allPalettes(custom).firstOrNull { it.id == id } ?: Palettes.first()

/** The four ways a palette can look. */
val Modes = listOf("light" to "Light", "dusk" to "Dusk", "dark" to "Dark", "oled" to "OLED")

fun backgroundFor(p: Palette, mode: String): Color = when (mode) {
    "light" -> p.bgLight
    "dusk" -> lerp(p.bgDark, p.textDark, 0.11f)
    "oled" -> Color.Black
    else -> p.bgDark
}

private fun readableOn(c: Color) = if (c.luminance() > 0.45f) Color(0xFF1A1512) else Color(0xFFFFFFFF)
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** [mode] is light, dusk, dark or oled. */
fun buildScheme(p: Palette, mode: String, accentOverride: Long): ColorScheme {
    val dark = mode != "light"
    val accent = if (accentOverride != 0L) Color(accentOverride.toInt()) else if (dark) p.accent else p.accentLight
    val bg = backgroundFor(p, mode)
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

/** The font file the user picked, or null when it cannot be read. */
fun customTypeface(path: String): android.graphics.Typeface? =
    if (path.isBlank()) null else runCatching { android.graphics.Typeface.createFromFile(path) }.getOrNull()

private fun customFamily(path: String): FontFamily? =
    customTypeface(path)?.let { FontFamily(androidx.compose.ui.text.font.Typeface(it)) }

fun readerFamily(setting: String, customPath: String = ""): FontFamily = when (setting) {
    "sans" -> SansFont
    "system" -> FontFamily.Default
    "custom" -> customFamily(customPath) ?: SerifFont
    else -> SerifFont
}

private fun TextStyle.with(f: FontFamily) = copy(fontFamily = f)

private fun typography(custom: FontFamily?): Typography {
    val d = Typography()
    val display = custom ?: DisplayFont
    val body = custom ?: SansFont
    return d.copy(
        displayLarge = d.displayLarge.with(display),
        displayMedium = d.displayMedium.with(display),
        displaySmall = d.displaySmall.with(display),
        headlineLarge = d.headlineLarge.with(display),
        headlineMedium = d.headlineMedium.with(display),
        headlineSmall = d.headlineSmall.with(display),
        titleLarge = d.titleLarge.with(display),
        titleMedium = d.titleMedium.with(display),
        titleSmall = d.titleSmall.with(display),
        bodyLarge = d.bodyLarge.with(body),
        bodyMedium = d.bodyMedium.with(body),
        bodySmall = d.bodySmall.with(body),
        labelLarge = d.labelLarge.with(display),
        labelMedium = d.labelMedium.with(display),
        labelSmall = d.labelSmall.with(display),
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
    val systemDark = isSystemInDarkTheme()
    val mode = when (settings.appearance) {
        "light", "dusk", "dark", "oled" -> settings.appearance
        else -> if (systemDark) "dark" else "light"
    }
    val palette = paletteOf(settings.themePreset, settings.customPalettes)
    val uiFont = androidx.compose.runtime.remember(settings.customFontPath, settings.uiFontCustom) {
        if (settings.uiFontCustom) customFamily(settings.customFontPath) else null
    }
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
            colorScheme = buildScheme(palette, mode, settings.accentOverride),
            motionScheme = if (settings.motion == "calm") MotionScheme.standard() else MotionScheme.expressive(),
            shapes = shapes,
            typography = typography(uiFont),
            content = content,
        )
    }
}
