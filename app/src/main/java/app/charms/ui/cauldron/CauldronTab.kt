package app.charms.ui.cauldron

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.charms.R
import app.charms.container
import app.charms.data.Potion
import app.charms.domain.Magic
import app.charms.ui.T
import app.charms.ui.World
import app.charms.ui.components.GoldButton
import app.charms.ui.components.Panel
import app.charms.ui.components.SectionLabel
import app.charms.ui.components.star
import app.charms.ui.theme.C
import app.charms.ui.theme.F
import kotlin.math.sin
import kotlin.random.Random

fun levelName(level: Int) = when (level) { 1 -> R.string.level_1; 2 -> R.string.level_2; else -> R.string.level_3 }

@Composable
fun CauldronTab(w: World) {
    val ctx = LocalContext.current
    val today = w.today
    val monday = Magic.monday(today)
    val week = Magic.week(monday, w.deeds, w.charms, w.rests, today)
    // Last week's cauldron, if it was left on the fire unbrewed.
    val lastMonday = monday.minusDays(7)
    val last = Magic.week(lastMonday, w.deeds, w.charms, w.rests, today)
    val brewedThis = w.brews.firstOrNull { it.week == monday.toEpochDay() }
    val brewedLast = w.brews.firstOrNull { it.week == lastMonday.toEpochDay() }
    val target = when {
        Magic.canBrew(monday, today) && brewedThis == null -> week
        Magic.canBrew(lastMonday, today) && brewedLast == null && last.level > 0 -> last
        else -> null
    }
    var choosing by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
        item {
            Text(stringResource(R.string.cauldron_title), style = F.display, modifier = Modifier.padding(top = 14.dp))
            Text(T.week(ctx, monday), style = F.italic)
            Cauldron(week.fill, week.level, Modifier.fillMaxWidth().height(300.dp))
            Text(
                stringResource(R.string.cauldron_points, week.points, week.max),
                style = F.heading.copy(color = C.goldLight), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (week.level == 0) stringResource(R.string.cauldron_level_0) else stringResource(R.string.cauldron_level, stringResource(levelName(week.level))),
                style = F.italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            WeekRow(week, Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(18.dp))
            when {
                brewedThis != null -> BrewedCard(brewedThis, w)
                target != null && target.level > 0 -> GoldButton(stringResource(R.string.cauldron_brew), Modifier.fillMaxWidth()) { choosing = true }
                target != null -> Text(stringResource(R.string.cauldron_cold), style = F.italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                else -> Text(stringResource(R.string.cauldron_brew_when), style = F.small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (brewedThis == null && brewedLast != null && !brewedLast.given) {
                Spacer(Modifier.height(12.dp)); BrewedCard(brewedLast, w)
            }
            SectionLabel(stringResource(R.string.cauldron_shelf))
            if (w.potions.isEmpty()) Text(stringResource(R.string.cauldron_shelf_empty), style = F.small)
        }
        items(w.potions, key = { "p" + it.id }) { p -> PotionRow(p) }
        val older = w.brews.filter { it.week < lastMonday.toEpochDay() || (it.week == lastMonday.toEpochDay() && it.given) }
        if (older.isNotEmpty()) item { SectionLabel(stringResource(R.string.cauldron_history)) }
        items(older, key = { "b" + it.id }) { b ->
            val p = w.potions.firstOrNull { it.id == b.potionId }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Bottle(b.level, p?.hue ?: 0, Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p?.name ?: "—", style = F.body)
                    Text(T.week(ctx, java.time.LocalDate.ofEpochDay(b.week)) + if (b.given) " · " + stringResource(R.string.cauldron_given) else "", style = F.small)
                }
            }
        }
    }

    if (choosing && target != null) {
        val c = ctx.container
        ChoosePotion(w.potions.filter { it.level <= target.level }, target.level, onDismiss = { choosing = false }) { p ->
            choosing = false
            c.write { grimoire.brew(target.monday, p, target.level) }
        }
    }
}

@Composable
private fun BrewedCard(b: app.charms.data.Brew, w: World) {
    val p = w.potions.firstOrNull { it.id == b.potionId } ?: return
    Panel(Modifier.fillMaxWidth(), edge = C.gold) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Bottle(p.level, p.hue, Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cauldron_brewed, p.name), style = F.heading.copy(color = C.goldLight))
                Text(p.reward, style = F.body)
                Text(if (b.given) stringResource(R.string.cauldron_given) else stringResource(R.string.cauldron_show), style = F.small.copy(color = if (b.given) C.dim else C.gold))
            }
        }
    }
}

@Composable
private fun PotionRow(p: Potion) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Bottle(p.level, p.hue, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = F.heading)
            Text(p.reward, style = F.small)
        }
        Text(stringResource(levelName(p.level)), style = F.italic.copy(color = C.gold))
    }
}

@Composable
private fun ChoosePotion(potions: List<Potion>, level: Int, onDismiss: () -> Unit, onPick: (Potion) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.night2,
        title = { Text(stringResource(R.string.cauldron_choose), style = F.title) },
        text = {
            Column {
                Text(stringResource(R.string.cauldron_choose_sub, stringResource(levelName(level))), style = F.small)
                Spacer(Modifier.height(10.dp))
                potions.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bottle(p.level, p.hue, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column { Text(p.name, style = F.heading); Text(p.reward, style = F.small) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel), color = C.dim) } },
    )
}

@Composable
private fun WeekRow(week: Magic.Week, modifier: Modifier) {
    val ctx = LocalContext.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.days.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(ctx.getString(dayShort(d.day.dayOfWeek.value)), style = F.small)
                Canvas(Modifier.size(36.dp)) {
                    val r = size.minDimension / 2
                    if (d.rest) { drawCircle(C.line, r * .5f, style = Stroke(1.dp.toPx())); return@Canvas }
                    drawCircle(C.line, r * .9f, style = Stroke(1.dp.toPx()))
                    val total = (d.gold + d.silver + d.ink).coerceAtLeast(0)
                    if (d.asked > 0 && total > 0) {
                        var a = -90f
                        val per = 360f / d.asked
                        listOf(d.gold to C.gold, d.silver to C.silver, d.ink to C.ink).forEach { (n, col) ->
                            if (n > 0) drawArc(col, a, per * n - 4f, false, style = Stroke(3.dp.toPx()), topLeft = Offset(r * .1f + 1.5.dp.toPx(), r * .1f + 1.5.dp.toPx()), size = Size(r * 1.8f - 3.dp.toPx(), r * 1.8f - 3.dp.toPx()))
                            a += per * n
                        }
                    }
                    if (d.spells > 0) star(center, r * .32f, C.goldLight)
                }
            }
        }
    }
}

fun dayShort(i: Int) = listOf(R.string.day_short_1, R.string.day_short_2, R.string.day_short_3, R.string.day_short_4, R.string.day_short_5, R.string.day_short_6, R.string.day_short_7)[i - 1]

/** An iron cauldron over a fire; the brew rises with the week and bubbles. */
@Composable
fun Cauldron(fill: Float, level: Int, modifier: Modifier) {
    val f by animateFloatAsState(fill, tween(1200), label = "fill")
    val t = rememberInfiniteTransition(label = "boil")
    val time by t.animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)), label = "time")
    val bubbles = remember { val r = Random(5); List(14) { Triple(r.nextFloat(), r.nextFloat(), .5f + r.nextFloat()) } }
    val brew = when (level) { 3 -> C.goldLight; 2 -> Color(0xFF9BE0B4); 1 -> Color(0xFF7FB6E8); else -> Color(0xFF5B4E8C) }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2; val potW = w * .62f; val top = h * .3f; val bottom = h * .86f
        // fire
        for (k in 0..6) {
            val x = cx + (k - 3) * potW * .11f
            val flick = 1f + .18f * sin(time * 12f + k * 1.7f)
            val fh = h * .12f * flick * (1f - kotlin.math.abs(k - 3) * .12f)
            val p = Path().apply { moveTo(x - potW * .06f, h * .97f); quadraticTo(x, h * .97f - fh * 1.6f, x + potW * .06f, h * .97f); close() }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFE86A2C), Color(0x00E8402C)), startY = h * .97f, endY = h * .97f - fh * 1.6f))
        }
        drawOval(Brush.radialGradient(listOf(Color(0x55FF9A3C), Color.Transparent), center = Offset(cx, h * .95f), radius = potW * .7f), topLeft = Offset(cx - potW * .7f, h * .8f), size = Size(potW * 1.4f, h * .3f))
        // the pot
        val pot = Path().apply {
            moveTo(cx - potW / 2, top)
            cubicTo(cx - potW * .62f, top + (bottom - top) * .55f, cx - potW * .38f, bottom, cx, bottom)
            cubicTo(cx + potW * .38f, bottom, cx + potW * .62f, top + (bottom - top) * .55f, cx + potW / 2, top)
            close()
        }
        // legs
        drawLine(Color(0xFF15111F), Offset(cx - potW * .3f, bottom - 10f), Offset(cx - potW * .36f, h * .93f), 10.dp.toPx())
        drawLine(Color(0xFF15111F), Offset(cx + potW * .3f, bottom - 10f), Offset(cx + potW * .36f, h * .93f), 10.dp.toPx())
        drawPath(pot, Brush.horizontalGradient(listOf(Color(0xFF0E0B16), Color(0xFF2E2840), Color(0xFF0E0B16)), startX = cx - potW / 2, endX = cx + potW / 2))
        // the brew, seen through a glass-like window in the pot: its level shows the week
        clipPath(pot) {
            val surf = bottom - (bottom - top) * (.08f + .86f * f)
            val wave = Path().apply {
                moveTo(cx - potW, h)
                var x = cx - potW
                while (x <= cx + potW) { lineTo(x, surf + 4f * sin(x * .03f + time * 3f)); x += 8f }
                lineTo(cx + potW, h); close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(brew, brew.copy(alpha = .55f)), startY = surf, endY = bottom))
            bubbles.forEach { (bx, by, s) ->
                val cyc = ((time * .25f * s + by) % 1f)
                val y = bottom - (bottom - surf) * cyc
                if (y > surf) drawCircle(Color.White.copy(alpha = .35f * (1 - cyc)), radius = (3f + 4f * s) * density / 2, center = Offset(cx - potW * .35f + bx * potW * .7f, y))
            }
            // threshold marks: small, middling, great
            listOf(Magic.SMALL, Magic.MIDDLING, Magic.GREAT).forEach { th ->
                val y = bottom - (bottom - top) * (.08f + .86f * th)
                drawLine(C.gold.copy(alpha = .5f), Offset(cx + potW * .22f, y), Offset(cx + potW * .34f, y), 1.5.dp.toPx())
            }
        }
        // rim
        drawOval(Color(0xFF2E2840), topLeft = Offset(cx - potW * .55f, top - h * .035f), size = Size(potW * 1.1f, h * .07f))
        drawOval(Color(0xFF0B0812), topLeft = Offset(cx - potW * .48f, top - h * .02f), size = Size(potW * .96f, h * .04f))
        // steam and sparks over a good brew
        if (f > .05f) for (k in 0..4) {
            val ph = (time * .2f + k * .2f) % 1f
            val x = cx + (k - 2) * potW * .12f + 10f * sin(time * 2f + k)
            val y = top - ph * h * .26f
            drawCircle(brew.copy(alpha = .22f * (1 - ph)), radius = (6f + ph * 18f) * density / 2, center = Offset(x, y))
            if (level >= 2 && k % 2 == 0) star(Offset(x + 8f, y - 6f), 4f * density * (1 - ph), C.goldLight.copy(alpha = 1 - ph))
        }
    }
}

/** A potion bottle: a vial, a round flask or a tall gilt one, by strength. */
@Composable
fun Bottle(level: Int, hue: Int, modifier: Modifier) {
    val col = C.potions[hue % C.potions.size]
    Canvas(modifier) { bottle(level, col) }
}

fun DrawScope.bottle(level: Int, col: Color) {
    val w = size.width; val h = size.height
    val glass = C.text.copy(alpha = .75f)
    val body = Path()
    when (level) {
        1 -> body.apply {
            moveTo(w * .42f, h * .18f); lineTo(w * .58f, h * .18f); lineTo(w * .58f, h * .4f)
            cubicTo(w * .7f, h * .48f, w * .7f, h * .92f, w * .5f, h * .92f); cubicTo(w * .3f, h * .92f, w * .3f, h * .48f, w * .42f, h * .4f); close()
        }
        2 -> body.apply {
            moveTo(w * .42f, h * .14f); lineTo(w * .58f, h * .14f); lineTo(w * .58f, h * .38f)
            cubicTo(w * .92f, h * .44f, w * .92f, h * .94f, w * .5f, h * .94f); cubicTo(w * .08f, h * .94f, w * .08f, h * .44f, w * .42f, h * .38f); close()
        }
        else -> body.apply {
            moveTo(w * .44f, h * .1f); lineTo(w * .56f, h * .1f); lineTo(w * .56f, h * .3f); lineTo(w * .78f, h * .44f)
            lineTo(w * .74f, h * .94f); lineTo(w * .26f, h * .94f); lineTo(w * .22f, h * .44f); lineTo(w * .44f, h * .3f); close()
        }
    }
    clipPath(body) { drawRect(Brush.verticalGradient(listOf(col.copy(alpha = .5f), col), startY = h * .45f, endY = h), topLeft = Offset(0f, h * .5f), size = Size(w, h * .5f)) }
    drawPath(body, glass, style = Stroke(1.6.dp.toPx()))
    drawRect(Color(0xFF8A5A1C), topLeft = Offset(w * .4f, h * .04f), size = Size(w * .2f, h * .1f))
    if (level == 3) star(Offset(w * .5f, h * .68f), w * .1f, C.goldLight)
    drawLine(Color.White.copy(alpha = .4f), Offset(w * .38f, h * .55f), Offset(w * .36f, h * .78f), 1.5.dp.toPx())
}
