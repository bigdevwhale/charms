package app.charms.ui.charm

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.charms.R
import app.charms.container
import app.charms.data.Cast
import app.charms.data.Deed
import app.charms.domain.Creature
import app.charms.domain.Tier
import app.charms.ui.World
import app.charms.ui.components.CreatureArt
import app.charms.ui.components.GhostButton
import app.charms.ui.components.GoldButton
import app.charms.ui.components.Sigil
import app.charms.ui.components.sigil
import app.charms.ui.components.star
import app.charms.ui.theme.C
import app.charms.ui.theme.F
import app.charms.ui.today.PageState
import app.charms.ui.today.pageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A page opened from today: what to photograph and the button that opens the camera (or the seal). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeedSheet(d: Deed, w: World, onDismiss: () -> Unit, onShoot: () -> Unit, onSealed: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = C.night2, dragHandle = null) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val glowing = pageState(d, w)
            val color = when (glowing) { PageState.Glowing -> C.glow; PageState.Rung -> C.ink; else -> C.gold }
            Sigil(d.sigil, color, 64.dp)
            Spacer(Modifier.height(10.dp))
            Text(d.title, style = F.title, textAlign = TextAlign.Center)
            val line = when (glowing) {
                PageState.Glowing -> stringResource(R.string.deed_glowing)
                PageState.Rung -> stringResource(R.string.deed_rung)
                else -> stringResource(R.string.deed_before, app.charms.ui.T.minutes(LocalContext.current, d.magicHour))
            }
            Text(line, style = F.italic.copy(color = color))
            Spacer(Modifier.height(22.dp))
            if (d.photo) {
                if (d.hint.isNotBlank()) Text(stringResource(R.string.deed_shoot, d.hint), style = F.body, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                GoldButton(stringResource(R.string.deed_charm), Modifier.fillMaxWidth(), onClick = onShoot)
            } else {
                Text(stringResource(R.string.deed_seal_hint), style = F.body, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                SealButton(d, onSealed)
            }
            Spacer(Modifier.height(10.dp))
            GhostButton(stringResource(R.string.deed_later), Modifier.fillMaxWidth(), color = C.dim, onClick = onDismiss)
        }
    }
}

/** Hold the seal until its ring closes. */
@Composable
private fun SealButton(d: Deed, onSealed: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val p = remember { Animatable(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    Box(
        Modifier
            .size(150.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    job = scope.launch {
                        p.animateTo(1f, tween(900, easing = LinearEasing))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        withContext(Dispatchers.Default) { ctx.container.grimoire.seal(d.id) }
                        onSealed()
                    }
                    waitForUpOrCancellation()
                    if (p.value < 1f) { job?.cancel(); scope.launch { p.animateTo(0f, tween(250)) } }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(C.panel2, radius = size.minDimension / 2 * .82f)
            drawCircle(C.gold.copy(alpha = .35f), radius = size.minDimension / 2 * .82f, style = Stroke(2.dp.toPx()))
            drawArc(C.goldLight, -90f, 360f * p.value, false, style = Stroke(6.dp.toPx()), topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()))
            sigil(d.sigil, C.gold.copy(alpha = .5f + .5f * p.value), size.minDimension * .4f)
        }
    }
    Text(stringResource(R.string.deed_seal), style = F.small, modifier = Modifier.padding(top = 6.dp))
}

/** The charm working, then the page it became. */
@Composable
fun RevealScreen(deedId: Long, seal: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    var cast by remember { mutableStateOf<Cast?>(null) }
    var failed by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(deedId) {
        val started = System.currentTimeMillis()
        val result = withContext(Dispatchers.Default) {
            if (seal) {
                val d = c.db.dao().deed(deedId)
                val ch = c.db.dao().charm(deedId, app.charms.domain.Magic.today().toEpochDay())
                if (d != null && ch != null) Cast(ch, d, app.charms.domain.Magic.spells(c.db.dao().deeds(), c.db.dao().charms(ch.day, ch.day), app.charms.domain.Magic.today()).let { all ->
                    // a seal's finished spell: the part of this deed, if all of it is done now
                    all.filter { it.name == d.part }
                }) else null
            } else c.grimoire.charm(ctx, deedId, c.vault.capture())
        }
        if (result != null && result.charm.file.isNotEmpty()) page = withContext(Dispatchers.IO) { c.vault.full(result.charm.file) }
        // Let the charm be seen working for a moment, even when it's quick.
        val left = 1600 - (System.currentTimeMillis() - started)
        if (left > 0) delay(left)
        cast = result
        failed = result == null
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(C.night.copy(alpha = .97f))
            .clickable(remember { MutableInteractionSource() }, null) {},
        contentAlignment = Alignment.Center,
    ) {
        val done = cast
        when {
            failed -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.charm_failed), style = F.heading, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                GoldButton(stringResource(R.string.back), onClick = onDone)
            }
            done == null -> Working()
            else -> Revealed(done, page, onDone)
        }
    }
}

@Composable
private fun Working() {
    val t = rememberInfiniteTransition(label = "work")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "turn")
    val beat by t.animateFloat(.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beat")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(220.dp)) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(C.gold.copy(alpha = .35f * beat), Color.Transparent)), radius = r)
            rotate(turn) {
                drawCircle(C.gold.copy(alpha = .6f), radius = r * .78f, style = Stroke(1.5.dp.toPx()))
                drawCircle(C.gold.copy(alpha = .3f), radius = r * .66f, style = Stroke(1.dp.toPx()))
                for (k in 0 until 12) {
                    val a = k * PI / 6
                    star(Offset(center.x + r * .72f * cos(a).toFloat(), center.y + r * .72f * sin(a).toFloat()), r * (if (k % 3 == 0) .07f else .035f), C.goldLight)
                }
            }
            rotate(-turn * 1.6f) { star(center, r * .3f * beat, C.goldLight) }
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.charm_working), style = F.title.copy(color = C.goldLight))
    }
}

@Composable
private fun Revealed(cast: Cast, page: Bitmap?, onDone: () -> Unit) {
    val tier = Tier.of(cast.charm.tier)
    val color = C.tier(tier)
    val appear = remember { Animatable(0f) }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(900))
        sweep.animateTo(1f, tween(1300))
    }
    val sparks = remember { val r = Random(cast.charm.id); List(46) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            sparks.forEach { (x, y, k) ->
                val p = appear.value
                val cx = size.width / 2 + (x - .5f) * size.width * 1.1f * p
                val cy = size.height * .38f + (y - .5f) * size.height * .9f * p - k * 60f * sweep.value
                star(Offset(cx, cy), (2f + k * 6f) * (1.2f - sweep.value * .6f), color.copy(alpha = (1f - sweep.value * .7f) * (.4f + k * .6f)))
            }
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (page != null) {
                val img = remember(page) { page.asImageBitmap() }
                Image(
                    img, null,
                    Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .aspectRatio(page.width / page.height.toFloat())
                        .graphicsLayer { alpha = appear.value; scaleX = .9f + .1f * appear.value; scaleY = .9f + .1f * appear.value }
                        .clip(RoundedCornerShape(10.dp))
                        .drawWithContent {
                            drawContent()
                            // light running over the gilt
                            val x = size.width * (sweep.value * 1.6f - .3f)
                            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x55FFF1C1), Color.Transparent), start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height)))
                        },
                )
            } else {
                Box(Modifier.size(200.dp).scale(.8f + .2f * appear.value), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .4f), Color.Transparent)))
                        drawCircle(color, radius = size.minDimension * .36f, style = Stroke(3.dp.toPx()))
                        sigil(cast.deed.sigil, color, size.minDimension * .4f)
                    }
                }
                Text(cast.deed.title, style = F.title)
            }
            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(appear.value > .6f, enter = fadeIn(tween(500)) + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(when (tier) { Tier.GOLD -> R.string.charm_gold; Tier.SILVER -> R.string.charm_silver; Tier.INK -> R.string.charm_ink }), style = F.display.copy(color = color))
                    Text(stringResource(when (tier) { Tier.GOLD -> R.string.charm_gold_sub; Tier.SILVER -> R.string.charm_silver_sub; Tier.INK -> R.string.charm_ink_sub }), style = F.italic, textAlign = TextAlign.Center)
                    if (cast.charm.visitor >= 0) {
                        val who = Creature.of(cast.charm.visitor)
                        Spacer(Modifier.height(14.dp))
                        CreatureArt(who, C.goldLight, 72.dp, bg = C.night)
                        Text(stringResource(R.string.charm_visitor, stringResource(who.title)), style = F.heading.copy(color = C.goldLight), textAlign = TextAlign.Center)
                    }
                    cast.spells.forEach { part ->
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.spell_done, stringResource(part.spell)), style = F.title.copy(color = C.goldLight), textAlign = TextAlign.Center)
                        Text(stringResource(R.string.spell_done_sub, stringResource(part.title).lowercase()), style = F.small, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(22.dp))
                    GoldButton(stringResource(R.string.charm_to_grimoire), Modifier.fillMaxWidth(), onClick = onDone)
                }
            }
        }
    }
}
