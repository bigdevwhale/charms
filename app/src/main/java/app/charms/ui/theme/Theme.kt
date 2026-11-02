package app.charms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.charms.R
import app.charms.domain.Tier

/** Night ink, parchment and gilt: a grimoire read by candlelight. */
object C {
    val night = Color(0xFF120D22)
    val night2 = Color(0xFF1A1430)
    val panel = Color(0xFF211939)
    val panel2 = Color(0xFF2B2150)
    val line = Color(0xFF4A3D70)
    val gold = Color(0xFFE8C26A)
    val goldLight = Color(0xFFFFE9A8)
    val goldDeep = Color(0xFF8A5A1C)
    val silver = Color(0xFFC9D2E0)
    val ink = Color(0xFFA89D86)
    val text = Color(0xFFF3E9D2)
    val dim = Color(0xFFA99CC8)
    val faint = Color(0xFF6E6294)
    val bad = Color(0xFFFF9A88)
    val glow = Color(0xFFFFC765)

    fun tier(t: Tier) = when (t) {
        Tier.GOLD -> gold
        Tier.SILVER -> silver
        Tier.INK -> ink
    }

    /** Potion bottle colours. */
    val potions = listOf(Color(0xFFE86A5C), Color(0xFF7FB6E8), Color(0xFF9BE0B4), Color(0xFFC59BF0), Color(0xFFFFC765), Color(0xFFF08FC0))
}

object F {
    val serif = FontFamily(
        Font(R.font.cormorant_semibold, FontWeight.SemiBold),
        Font(R.font.cormorant_bold, FontWeight.Bold),
        Font(R.font.cormorant_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val sans = FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
    )

    val display = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, color = C.text)
    val title = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, color = C.text)
    val heading = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, color = C.text)
    val italic = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 22.sp, color = C.dim)
    val body = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 21.sp, color = C.text)
    val small = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 17.sp, color = C.dim)
    val label = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.2.sp, color = C.dim)
}

@Composable
fun CharmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.gold, onPrimary = C.night, secondary = C.goldLight, background = C.night, onBackground = C.text,
            surface = C.panel, onSurface = C.text, surfaceVariant = C.panel2, onSurfaceVariant = C.dim, outline = C.line,
            surfaceContainer = C.panel, surfaceContainerHigh = C.panel2, surfaceContainerHighest = C.panel2, surfaceContainerLow = C.night2,
        ),
        content = content,
    )
}
