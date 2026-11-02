package app.charms.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.charms.art.Creatures
import app.charms.art.Sigils
import app.charms.domain.Creature
import app.charms.ui.theme.C
import app.charms.ui.theme.F
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** The night sky behind every screen: a few still stars and a faint glow from above. */
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    val stars = remember { val r = Random(3); List(70) { Triple(r.nextFloat(), r.nextFloat(), .4f + r.nextFloat() * 1.2f) } }
    val t = rememberInfiniteTransition(label = "sky")
    val tw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse), label = "tw")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF221A44), C.night, C.night)))
        drawCircle(Brush.radialGradient(listOf(Color(0x332A2060), Color.Transparent), center = Offset(size.width * .5f, 0f), radius = size.width), radius = size.width, center = Offset(size.width * .5f, 0f))
        stars.forEachIndexed { i, (x, y, r) ->
            val a = if (i % 5 == 0) .25f + .5f * tw else .35f
            drawCircle(C.goldLight.copy(alpha = a), radius = r * density, center = Offset(x * size.width, y * size.height))
        }
    }
}

fun DrawScope.sigil(i: Int, color: Color, s: Float = size.minDimension, width: Float = s * .09f) {
    drawIntoCanvas { Sigils.draw(it.nativeCanvas, i, size.width / 2, size.height / 2, s, color.toArgb(), width) }
}

@Composable
fun Sigil(i: Int, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { sigil(i, color, this.size.minDimension * .8f) }
}

@Composable
fun CreatureArt(who: Creature, color: Color, size: Dp, modifier: Modifier = Modifier, bg: Color = C.panel) {
    Canvas(modifier.size(size)) {
        drawIntoCanvas { Creatures.draw(it.nativeCanvas, who, this.size.width / 2, this.size.height / 2, this.size.minDimension, color.toArgb(), bg.toArgb()) }
    }
}

/** A four-pointed star. */
fun DrawScope.star(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r); lineTo(center.x + r * .22f, center.y - r * .22f); lineTo(center.x + r, center.y)
        lineTo(center.x + r * .22f, center.y + r * .22f); lineTo(center.x, center.y + r); lineTo(center.x - r * .22f, center.y + r * .22f)
        lineTo(center.x - r, center.y); lineTo(center.x - r * .22f, center.y - r * .22f); close()
    }
    drawPath(p, color)
}

@Composable
fun Star(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { star(center, this.size.minDimension / 2, color) }
}

/** A gilt button. */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (enabled) Brush.verticalGradient(listOf(C.goldLight, C.gold)) else Brush.verticalGradient(listOf(C.panel2, C.panel2)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = F.heading.copy(color = if (enabled) C.night else C.faint), textAlign = TextAlign.Center)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = C.gold, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = F.body.copy(color = color), textAlign = TextAlign.Center) }
}

/** A dark page with a thin gilt edge. */
@Composable
fun Panel(modifier: Modifier = Modifier, edge: Color = C.line, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(C.panel.copy(alpha = .92f))
            .border(1.dp, edge, RoundedCornerShape(18.dp))
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Star(C.gold.copy(alpha = .8f), 10.dp)
        Text(text.uppercase(), style = F.label)
    }
}

/** An engraving's small copy, loaded off the main thread. */
@Composable
fun rememberSmall(name: String, load: (String) -> Bitmap?): ImageBitmap? {
    val b by produceState<ImageBitmap?>(null, name) { value = withContext(Dispatchers.IO) { load(name)?.asImageBitmap() } }
    return b
}
