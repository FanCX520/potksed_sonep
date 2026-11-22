package com.example.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R
import java.io.File

/**
 * Bundled local static font families (installed via font-util, zero runtime network dependency).
 */
val OutfitFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Light),
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit, FontWeight.Medium),
    Font(R.font.outfit, FontWeight.SemiBold),
    Font(R.font.outfit, FontWeight.Bold)
)

val UnifiedClockFontFamily: FontFamily = OutfitFontFamily

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Light),
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val NotoSerifScFontFamily = FontFamily(
    Font(R.font.noto_serif_sc, FontWeight.Normal),
    Font(R.font.noto_serif_sc, FontWeight.Medium),
    Font(R.font.noto_serif_sc, FontWeight.SemiBold),
    Font(R.font.noto_serif_sc, FontWeight.Bold)
)

val PlayfairDisplayFontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Medium),
    Font(R.font.playfair_display, FontWeight.SemiBold),
    Font(R.font.playfair_display, FontWeight.Bold)
)

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

object PenFontCatalog {
    const val FAMILY_OUTFIT = "outfit"
    const val FAMILY_INTER = "inter"
    const val FAMILY_JETBRAINS = "jetbrains_mono"
    const val FAMILY_NOTO_SERIF_SC = "noto_serif_sc"
    const val FAMILY_PLAYFAIR = "playfair"
    const val FAMILY_SPACE_GROTESK = "space_grotesk"
    const val FAMILY_SYSTEM_SANS = "system_sans"
    const val FAMILY_SYSTEM_SERIF = "system_serif"
    const val FAMILY_EXTERNAL = "external"

    val allFamilies: List<Pair<String, String>> = listOf(
        FAMILY_OUTFIT to "Outfit 几何无衬线",
        FAMILY_INTER to "Inter 极简现代",
        FAMILY_SPACE_GROTESK to "Space Grotesk 科技",
        FAMILY_JETBRAINS to "JetBrains Mono 等宽",
        FAMILY_NOTO_SERIF_SC to "思源宋体 (Noto Serif)",
        FAMILY_PLAYFAIR to "Playfair 典雅衬线",
        FAMILY_SYSTEM_SANS to "系统无衬线",
        FAMILY_SYSTEM_SERIF to "系统衬线"
    )

    private val externalCache = LinkedHashMap<String, FontFamily>(8, 0.75f, true)

    private val failedExternalPaths = mutableSetOf<String>()

    fun clearExternalCache() {
        synchronized(externalCache) { externalCache.clear() }
    }

    fun resolve(familyKey: String, externalPath: String = ""): FontFamily {
        if (externalPath.isNotBlank() &&
            !failedExternalPaths.contains(externalPath) &&
            (familyKey == FAMILY_EXTERNAL || familyKey == FAMILY_NOTO_SERIF_SC || File(externalPath).exists())
        ) {
            val cached = synchronized(externalCache) { externalCache[externalPath] }
            if (cached != null) return cached
            val loaded = try {
                val f = File(externalPath)
                if (f.exists() && f.isFile && f.length() in 4096L..(24L * 1024L * 1024L)) {
                    FontFamily(Typeface.createFromFile(f))
                } else {
                    failedExternalPaths.add(externalPath)
                    null
                }
            } catch (_: Throwable) {
                failedExternalPaths.add(externalPath)
                null
            }
            if (loaded != null) {
                synchronized(externalCache) {
                    if (externalCache.size >= 6) {
                        externalCache.entries.firstOrNull()?.let { externalCache.remove(it.key) }
                    }
                    externalCache[externalPath] = loaded
                }
                return loaded
            }
        }
        return when (familyKey) {
            FAMILY_INTER -> InterFontFamily
            FAMILY_JETBRAINS -> JetBrainsMonoFontFamily
            FAMILY_NOTO_SERIF_SC -> NotoSerifScFontFamily
            FAMILY_PLAYFAIR -> PlayfairDisplayFontFamily
            FAMILY_SPACE_GROTESK -> SpaceGroteskFontFamily
            FAMILY_SYSTEM_SANS -> FontFamily.SansSerif
            FAMILY_SYSTEM_SERIF -> FontFamily.Serif
            else -> OutfitFontFamily
        }
    }

    fun weightFromInt(weight: Int): FontWeight = when {
        weight <= 325 -> FontWeight.Light
        weight <= 450 -> FontWeight.Normal
        weight <= 550 -> FontWeight.Medium
        weight <= 650 -> FontWeight.SemiBold
        weight <= 750 -> FontWeight.Bold
        else -> FontWeight.ExtraBold
    }
}

@Immutable
data class FontSlotSpec(
    val familyKey: String,
    val scale: Float = 1.0f,
    val weight: Int = 500,
    val letterSpacingSp: Float = 0f,
    val externalPath: String = ""
) {
    fun clamped(): FontSlotSpec = copy(
        scale = scale.coerceIn(0.75f, 1.45f),
        weight = weight.coerceIn(300, 800),
        letterSpacingSp = letterSpacingSp.coerceIn(-2.0f, 3.0f)
    )
}

@Immutable
data class GranularTypographyProfile(
    val clock: FontSlotSpec = FontSlotSpec(PenFontCatalog.FAMILY_OUTFIT, 1.0f, 700, -0.8f),
    val title: FontSlotSpec = FontSlotSpec(PenFontCatalog.FAMILY_INTER, 1.0f, 600, -0.15f),
    val body: FontSlotSpec = FontSlotSpec(PenFontCatalog.FAMILY_INTER, 1.0f, 400, 0f),
    val poetry: FontSlotSpec = FontSlotSpec(PenFontCatalog.FAMILY_NOTO_SERIF_SC, 1.0f, 500, 0.3f),
    val metric: FontSlotSpec = FontSlotSpec(PenFontCatalog.FAMILY_JETBRAINS, 1.0f, 600, 0.1f)
) {
    companion object {
        const val SLOT_CLOCK = "clock"
        const val SLOT_TITLE = "title"
        const val SLOT_BODY = "body"
        const val SLOT_POETRY = "poetry"
        const val SLOT_METRIC = "metric"
    }

    fun slotByName(slot: String): FontSlotSpec = when (slot.lowercase()) {
        SLOT_CLOCK -> clock
        SLOT_TITLE -> title
        SLOT_BODY -> body
        SLOT_POETRY -> poetry
        SLOT_METRIC -> metric
        else -> body
    }

    fun withUpdatedSlot(slot: String, spec: FontSlotSpec): GranularTypographyProfile {
        val clean = spec.clamped()
        return when (slot.lowercase()) {
            SLOT_CLOCK -> copy(clock = clean)
            SLOT_TITLE -> copy(title = clean)
            SLOT_BODY -> copy(body = clean)
            SLOT_POETRY -> copy(poetry = clean)
            SLOT_METRIC -> copy(metric = clean)
            else -> copy(body = clean)
        }
    }
}

fun resolvePoetryFontFamily(externalFontPath: String): FontFamily =
    PenFontCatalog.resolve(
        familyKey = if (externalFontPath.isNotBlank()) PenFontCatalog.FAMILY_EXTERNAL else PenFontCatalog.FAMILY_NOTO_SERIF_SC,
        externalPath = externalFontPath
    )

fun buildDynamicTypography(
    profile: GranularTypographyProfile,
    isAppleLight: Boolean
): Typography {
    val clockFamily = PenFontCatalog.resolve(profile.clock.familyKey, profile.clock.externalPath)
    val titleFamily = PenFontCatalog.resolve(profile.title.familyKey, profile.title.externalPath)
    val bodyFamily = PenFontCatalog.resolve(profile.body.familyKey, profile.body.externalPath)
    val metricFamily = PenFontCatalog.resolve(profile.metric.familyKey, profile.metric.externalPath)

    val cs = profile.clock.scale
    val ts = profile.title.scale
    val bs = profile.body.scale
    val ms = profile.metric.scale

    val clockWeight = PenFontCatalog.weightFromInt(profile.clock.weight)
    val titleWeight = PenFontCatalog.weightFromInt(profile.title.weight)
    val bodyWeight = PenFontCatalog.weightFromInt(profile.body.weight)
    val metricWeight = PenFontCatalog.weightFromInt(profile.metric.weight)

    return Typography(
        displayLarge = TextStyle(
            fontFamily = clockFamily,
            fontWeight = clockWeight,
            fontSize = (66f * cs).sp,
            lineHeight = (70f * cs).sp,
            letterSpacing = (-1.2f + profile.clock.letterSpacingSp).sp,
            fontFeatureSettings = "tnum"
        ),
        displayMedium = TextStyle(
            fontFamily = clockFamily,
            fontWeight = clockWeight,
            fontSize = (42f * cs).sp,
            lineHeight = (46f * cs).sp,
            letterSpacing = (-0.4f + profile.clock.letterSpacingSp).sp,
            fontFeatureSettings = "tnum"
        ),
        headlineMedium = TextStyle(
            fontFamily = clockFamily,
            fontWeight = titleWeight,
            fontSize = ((if (isAppleLight) 21f else 22f) * ts).sp,
            lineHeight = (25f * ts).sp,
            letterSpacing = profile.title.letterSpacingSp.sp,
            fontFeatureSettings = "tnum"
        ),
        titleMedium = TextStyle(
            fontFamily = titleFamily,
            fontWeight = titleWeight,
            fontSize = (15f * ts).sp,
            lineHeight = (20f * ts).sp,
            letterSpacing = profile.title.letterSpacingSp.sp
        ),
        titleSmall = TextStyle(
            fontFamily = titleFamily,
            fontWeight = titleWeight,
            fontSize = (13f * ts).sp,
            lineHeight = (18f * ts).sp,
            letterSpacing = profile.title.letterSpacingSp.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = bodyWeight,
            fontSize = (13f * bs).sp,
            lineHeight = (18f * bs).sp,
            letterSpacing = profile.body.letterSpacingSp.sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = bodyWeight,
            fontSize = (11f * bs).sp,
            lineHeight = (15f * bs).sp,
            letterSpacing = profile.body.letterSpacingSp.sp
        ),
        labelMedium = TextStyle(
            fontFamily = metricFamily,
            fontWeight = metricWeight,
            fontSize = (11f * ms).sp,
            lineHeight = (14f * ms).sp,
            letterSpacing = profile.metric.letterSpacingSp.sp,
            fontFeatureSettings = "tnum"
        ),
        labelSmall = TextStyle(
            fontFamily = metricFamily,
            fontWeight = metricWeight,
            fontSize = (10f * ms).sp,
            lineHeight = (13f * ms).sp,
            letterSpacing = profile.metric.letterSpacingSp.sp,
            fontFeatureSettings = "tnum"
        )
    )
}

val PenOsTypography: Typography = buildDynamicTypography(GranularTypographyProfile(), isAppleLight = false)
val AppleGalleryTypography: Typography = buildDynamicTypography(GranularTypographyProfile(), isAppleLight = true)
