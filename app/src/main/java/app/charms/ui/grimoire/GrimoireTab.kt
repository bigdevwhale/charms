package app.charms.ui.grimoire

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.charms.R
import app.charms.container
import app.charms.data.Charm
import app.charms.domain.Creature
import app.charms.domain.Magic
import app.charms.domain.Part
import app.charms.domain.Tier
import app.charms.ui.T
import app.charms.ui.World
import app.charms.ui.components.CreatureArt
import app.charms.ui.components.Star
import app.charms.ui.components.rememberSmall
import app.charms.ui.components.sigil
import app.charms.ui.theme.C
import app.charms.ui.theme.F
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun GrimoireTab(w: World) {
    var view by rememberSaveable { mutableStateOf(0) }
    var open by rememberSaveable { mutableLongStateOf(0L) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp), verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.tab_grimoire), style = F.display, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Seg(stringResource(R.string.grimoire_pages), view == 0) { view = 0 }
            Seg(stringResource(R.string.grimoire_bestiary), view == 1) { view = 1 }
        }
        if (view == 0) Pages(w) { open = it } else Bestiary(w)
    }
    if (open > 0) w.charms.firstOrNull { it.id == open }?.let { PageDialog(it, w) { open = 0 } }
}

@Composable
private fun Seg(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (on) C.gold else C.line, RoundedCornerShape(16.dp))
            .background(if (on) C.gold.copy(alpha = .12f) else C.night.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) { Text(text, style = F.body.copy(color = if (on) C.gold else C.dim)) }
}

@Composable
private fun Pages(w: World, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val byDay = w.charms.groupBy { it.day }.toSortedMap(compareByDescending { it })
    if (byDay.isEmpty()) {
        Text(stringResource(R.string.grimoire_empty), style = F.italic, modifier = Modifier.padding(24.dp))
        return
    }
    val deeds = w.allDeeds.associateBy { it.id }
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        byDay.forEach { (day, list) ->
            val date = LocalDate.ofEpochDay(day)
            val spells = Magic.spells(w.deeds, w.charms, date)
            item(key = "d$day", span = { GridItemSpan(3) }) {
                Row(Modifier.padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(T.weekday(ctx, date), style = F.heading)
                    if (spells.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp)); Star(C.gold, 10.dp); Spacer(Modifier.width(4.dp))
                        Text(spells.joinToString(" · ") { ctx.getString(it.spell) }, style = F.italic.copy(color = C.gold))
                    }
                }
            }
            val sorted = list.sortedBy { deeds[it.deedId]?.magicHour ?: 0 }
            items(sorted, key = { it.id }) { ch -> Thumb(ch, deeds[ch.deedId]?.sigil ?: 0) { onOpen(ch.id) } }
        }
    }
}

@Composable
private fun Thumb(ch: Charm, sigil: Int, onClick: () -> Unit) {
    val c = LocalContext.current.container
    val col = C.tier(Tier.of(ch.tier))
    Box(
        Modifier
            .aspectRatio(.75f)
            .clip(RoundedCornerShape(8.dp))
            .background(C.panel)
            .border(1.dp, col.copy(alpha = if (ch.blot) .25f else .6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val img = if (ch.file.isNotEmpty()) rememberSmall(ch.file) { c.vault.small(it) } else null
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (ch.blot) .35f else 1f)
        else Canvas(Modifier.fillMaxSize()) {
            drawCircle(col.copy(alpha = .5f), radius = size.minDimension * .3f, style = Stroke(1.5.dp.toPx()))
            sigil(sigil, col.copy(alpha = if (ch.blot) .3f else 1f), size.minDimension * .32f)
        }
        if (ch.blot) Text(stringResource(R.string.mentor_blotted), style = F.small.copy(color = C.bad))
    }
}

@Composable
fun PageDialog(ch: Charm, w: World, onClose: () -> Unit) {
    val c = LocalContext.current.container
    val deed = w.allDeeds.firstOrNull { it.id == ch.deedId }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.night.copy(alpha = .96f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            if (ch.file.isNotEmpty()) {
                val full by produceState<Bitmap?>(null, ch.file) { value = withContext(Dispatchers.IO) { c.vault.full(ch.file) } }
                full?.let { b ->
                    Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().padding(16.dp).aspectRatio(b.width / b.height.toFloat()).clip(RoundedCornerShape(10.dp)))
                }
            } else {
                val col = C.tier(Tier.of(ch.tier))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.width(200.dp).height(200.dp)) {
                        drawCircle(col, radius = size.minDimension * .4f, style = Stroke(3.dp.toPx()))
                        sigil(deed?.sigil ?: 0, col, size.minDimension * .42f)
                    }
                    Text(deed?.title ?: "", style = F.title)
                    Text(stringResource(R.string.grimoire_seal) + " · " + stringResource(Tier.of(ch.tier).title), style = F.italic.copy(color = col))
                }
            }
        }
    }
}

@Composable
private fun Bestiary(w: World) {
    val visits = w.charms.filter { it.visitor >= 0 && !it.blot }.groupingBy { it.visitor }.eachCount()
    // Every card the same size, met or not: rows of two, a fixed height.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.bestiary_hint), style = F.small, modifier = Modifier.padding(bottom = 4.dp))
        Creature.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(306.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cr -> CreatureCard(cr, visits[cr.ordinal] ?: 0, w, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun CreatureCard(cr: Creature, n: Int, w: World, modifier: Modifier) {
    val patronOf = w.deeds.filter { it.creature == cr.ordinal }.joinToString(", ") { it.title }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(C.panel)
            .border(1.dp, if (n > 0) C.gold.copy(alpha = .6f) else C.line, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CreatureArt(cr, if (n > 0) C.goldLight else C.line, 96.dp)
        Spacer(Modifier.height(6.dp))
        if (n > 0) {
            Text(stringResource(cr.title), style = F.heading.copy(color = C.goldLight), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(cr.about), style = F.small, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.bestiary_visits, n), style = F.small.copy(color = C.gold))
        } else {
            Text("?", style = F.title.copy(color = C.faint))
            Text(stringResource(R.string.bestiary_locked), style = F.small, textAlign = TextAlign.Center)
        }
        if (patronOf.isNotEmpty()) Text(stringResource(R.string.bestiary_patron, patronOf), style = F.small.copy(color = C.faint), textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}
