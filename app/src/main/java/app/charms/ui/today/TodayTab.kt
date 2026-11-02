package app.charms.ui.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.charms.R
import app.charms.container
import app.charms.data.Charm
import app.charms.data.Deed
import app.charms.domain.Magic
import app.charms.domain.Part
import app.charms.domain.Rank
import app.charms.domain.Tier
import app.charms.ui.T
import app.charms.ui.World
import app.charms.ui.components.Sigil
import app.charms.ui.components.Star
import app.charms.ui.components.rememberSmall
import app.charms.ui.components.star
import app.charms.ui.theme.C
import app.charms.ui.theme.F

/** Where a page stands today. */
sealed interface PageState {
    data object Quiet : PageState
    data object Glowing : PageState
    data object Rung : PageState
    data class Done(val charm: Charm) : PageState
}

fun pageState(d: Deed, w: World): PageState {
    val day = w.today.toEpochDay()
    w.charms.firstOrNull { it.deedId == d.id && it.day == day }?.let { return PageState.Done(it) }
    if (w.bells.any { it.deedId == d.id && it.day == day }) return PageState.Rung
    return if (Magic.minutesOf(w.now) > d.magicHour) PageState.Glowing else PageState.Quiet
}

@Composable
fun TodayTab(w: World, onDeed: (Long) -> Unit, onMentor: () -> Unit) {
    val ctx = LocalContext.current
    val today = w.today
    val on = Magic.deedsOn(w.deeds, today)
    val rest = today.toEpochDay() in w.rests
    val spells = Magic.spells(w.deeds, w.charms, today).toSet()
    val count = w.charms.count { !it.blot }
    val rank = Rank.of(count)
    val next = Rank.next(count)
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (w.settings.name.isBlank()) stringResource(R.string.today_title_noname) else stringResource(R.string.today_title, w.settings.name),
                        style = F.display,
                    )
                    Text(T.weekday(ctx, today), style = F.italic)
                }
                IconButton(onClick = onMentor) { Icon(Icons.Outlined.Key, stringResource(R.string.mentor), tint = C.faint) }
            }
            Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Star(C.gold, 12.dp)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(rank.title), style = F.heading.copy(color = C.gold))
                if (next != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.today_rank_next, next.from - count, stringResource(next.title)), style = F.small)
                }
            }
        }
        if (rest || on.isEmpty()) {
            item { Text(stringResource(if (rest) R.string.today_rest else R.string.today_nothing), style = F.italic, modifier = Modifier.padding(top = 40.dp)) }
        } else {
            Part.entries.forEach { part ->
                val mine = on.filter { it.part == part.name }
                if (mine.isEmpty()) return@forEach
                item(key = "h" + part.name) { PartHeader(part, part in spells) }
                items(mine, key = { it.id }) { d -> DeedCard(d, pageState(d, w), onClick = { if (pageState(d, w) !is PageState.Done) onDeed(d.id) }) }
            }
        }
    }
}

@Composable
private fun PartHeader(part: Part, done: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(part.title).uppercase(), style = F.label)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(C.line))
        Spacer(Modifier.width(10.dp))
        if (done) Star(C.gold, 12.dp)
        Spacer(Modifier.width(4.dp))
        Text("«" + stringResource(part.spell) + "»", style = F.italic.copy(color = if (done) C.gold else C.faint))
    }
}

@Composable
fun DeedCard(d: Deed, state: PageState, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val pulse = rememberInfiniteTransition(label = "glow")
    val a by pulse.animateFloat(.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")
    val edge = when (state) {
        PageState.Quiet -> C.line
        PageState.Glowing -> C.glow.copy(alpha = a)
        PageState.Rung -> C.ink.copy(alpha = .7f)
        is PageState.Done -> C.tier(Tier.of(state.charm.tier)).copy(alpha = .8f)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (state is PageState.Glowing) Brush.horizontalGradient(listOf(Color(0x33FFC765), C.panel)) else Brush.horizontalGradient(listOf(C.panel, C.panel)))
            .border(if (state is PageState.Glowing) 1.5.dp else 1.dp, edge, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sigilColor = when (state) {
            is PageState.Done -> C.tier(Tier.of(state.charm.tier))
            PageState.Glowing -> C.glow
            else -> C.dim
        }
        Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, sigilColor.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
            Sigil(d.sigil, sigilColor, 30.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, style = F.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = when (state) {
                PageState.Quiet -> stringResource(R.string.deed_before, T.minutes(ctx, d.magicHour))
                PageState.Glowing -> stringResource(R.string.deed_glowing)
                PageState.Rung -> stringResource(R.string.deed_rung)
                is PageState.Done -> stringResource(Tier.of(state.charm.tier).title) + (if (state.charm.blot) " · " + stringResource(R.string.mentor_blotted) else "")
            }
            val color = when (state) {
                PageState.Quiet -> C.dim
                PageState.Glowing -> C.glow
                PageState.Rung -> C.ink
                is PageState.Done -> C.tier(Tier.of(state.charm.tier))
            }
            Text(line, style = F.small.copy(color = color))
        }
        if (state is PageState.Done) {
            Spacer(Modifier.width(8.dp))
            val c = LocalContext.current.container
            val img = if (state.charm.file.isNotEmpty()) rememberSmall(state.charm.file) { c.vault.small(it) } else null
            Box(Modifier.size(width = 42.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, color(state), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Canvas(Modifier.fillMaxSize()) { star(center, size.minDimension * .3f, color(state)) }
            }
        }
    }
}

private fun color(s: PageState.Done) = C.tier(Tier.of(s.charm.tier))
