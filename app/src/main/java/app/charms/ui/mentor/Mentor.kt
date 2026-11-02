package app.charms.ui.mentor

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.charms.R
import app.charms.container
import app.charms.data.Deed
import app.charms.data.Potion
import app.charms.domain.Creature
import app.charms.domain.Magic
import app.charms.domain.Part
import app.charms.domain.Tier
import app.charms.art.Sigils
import app.charms.notify.Alarms
import app.charms.ui.T
import app.charms.ui.World
import app.charms.ui.cauldron.Bottle
import app.charms.ui.cauldron.dayShort
import app.charms.ui.cauldron.levelName
import app.charms.ui.components.CreatureArt
import app.charms.ui.components.GhostButton
import app.charms.ui.components.GoldButton
import app.charms.ui.components.NightSky
import app.charms.ui.components.SectionLabel
import app.charms.ui.components.Sigil
import app.charms.ui.components.rememberSmall
import app.charms.ui.grimoire.PageDialog
import app.charms.ui.theme.C
import app.charms.ui.theme.F
import app.charms.ui.today.PageState
import app.charms.ui.today.pageState

/** Four digits only the grown-up knows. */
@Composable
fun PinDialog(pin: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.night2,
        title = { Text(stringResource(R.string.mentor_pin), style = F.title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(if (wrong) stringResource(R.string.mentor_pin_wrong) else stringResource(R.string.mentor_pin_sub), style = F.small.copy(color = if (wrong) C.bad else C.dim))
                Spacer(Modifier.height(14.dp))
                PinPad(typed) { d ->
                    wrong = false
                    typed = if (d == -1) typed.dropLast(1) else (typed + d).take(4)
                    if (typed.length == 4) { if (typed == pin) onOk() else { wrong = true; typed = "" } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel), color = C.dim) } },
    )
}

/** Dots for what's typed and a pad of digits; -1 erases. */
@Composable
fun PinPad(typed: String, onKey: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < typed.length) C.gold else C.line)) }
        }
        Spacer(Modifier.height(16.dp))
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-2, 0, -1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(58.dp).clip(CircleShape)
                            .then(if (k == -2) Modifier else Modifier.border(1.dp, C.line, CircleShape).clickable { onKey(k) }),
                        contentAlignment = Alignment.Center,
                    ) { if (k >= 0) Text("$k", style = F.title.copy(fontFamily = F.sans, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp))) else if (k == -1) Text("⌫", style = F.heading.copy(color = C.dim)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MentorScreen(w: World, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    var editDeed by rememberSaveable { mutableLongStateOf(-1L) }
    var editPotion by rememberSaveable { mutableLongStateOf(-1L) }
    var openCharm by rememberSaveable { mutableLongStateOf(0L) }
    var changePin by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    val today = w.today
    val notifyAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Box(Modifier.fillMaxSize()) {
        NightSky()
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = C.text) }
                    Text(stringResource(R.string.mentor), style = F.display)
                }
                SectionLabel(stringResource(R.string.mentor_today) + " · " + T.day(ctx, today))
            }
            val on = Magic.deedsOn(w.deeds, today)
            items(on, key = { "t" + it.id }) { d ->
                val st = pageState(d, w)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sigil(d.sigil, C.dim, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = F.body)
                        Text(
                            when (st) {
                                PageState.Quiet -> stringResource(R.string.deed_before, T.minutes(ctx, d.magicHour))
                                PageState.Glowing -> stringResource(R.string.deed_glowing)
                                PageState.Rung -> stringResource(R.string.mentor_bell_done)
                                is PageState.Done -> stringResource(Tier.of(st.charm.tier).title) + if (st.charm.blot) " · " + stringResource(R.string.mentor_blotted) else ""
                            },
                            style = F.small,
                        )
                    }
                    when (st) {
                        is PageState.Done -> {
                            val img = if (st.charm.file.isNotEmpty()) rememberSmall(st.charm.file) { c.vault.small(it) } else null
                            if (img != null) Image(img, null, Modifier.size(width = 36.dp, height = 48.dp).clip(RoundedCornerShape(4.dp)).clickable { openCharm = st.charm.id }, contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { c.write { grimoire.blot(st.charm, !st.charm.blot) } }) {
                                Text(stringResource(if (st.charm.blot) R.string.mentor_unblot else R.string.mentor_blot), color = if (st.charm.blot) C.dim else C.bad)
                            }
                        }
                        PageState.Rung -> Icon(Icons.Outlined.NotificationsActive, null, tint = C.ink)
                        else -> TextButton(onClick = { c.write { grimoire.ring(ctx, d.id) } }) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = C.gold, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.mentor_bell), color = C.gold)
                        }
                    }
                }
            }
            item {
                val rest = today.toEpochDay() in w.rests
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.mentor_rest), style = F.body)
                        Text(stringResource(R.string.mentor_rest_sub), style = F.small)
                    }
                    Switch(rest, { v -> c.write { grimoire.rest(today, v) } }, colors = switchColors())
                }
            }
            val waiting = w.brews.filter { !it.given }
            if (waiting.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.mentor_rewards)) }
                items(waiting, key = { "w" + it.id }) { b ->
                    val p = w.potions.firstOrNull { it.id == b.potionId }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bottle(b.level, p?.hue ?: 0, Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(p?.name ?: "—", style = F.body); Text(p?.reward ?: "", style = F.small) }
                        TextButton(onClick = { c.write { db.dao().update(b.copy(given = true)) } }) { Text(stringResource(R.string.mentor_give), color = C.gold) }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.mentor_deeds)) }
            items(w.allDeeds, key = { "d" + it.id }) { d ->
                Row(Modifier.fillMaxWidth().clickable { editDeed = d.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sigil(d.sigil, if (d.archived) C.faint else C.gold, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = F.body.copy(color = if (d.archived) C.faint else C.text))
                        Text(
                            if (d.archived) stringResource(R.string.mentor_archived)
                            else listOf(T.minutes(ctx, d.magicHour), stringResource(Part.of(d.part).title), days(ctx, d.days)).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = F.small,
                        )
                    }
                }
            }
            item { GhostButton(stringResource(R.string.mentor_add_deed), Modifier.fillMaxWidth().padding(top = 6.dp)) { editDeed = 0 } }
            item { SectionLabel(stringResource(R.string.mentor_potions)) }
            items(w.potions, key = { "p" + it.id }) { p ->
                Row(Modifier.fillMaxWidth().clickable { editPotion = p.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bottle(p.level, p.hue, Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(p.name, style = F.body); Text(p.reward, style = F.small) }
                    Text(stringResource(levelName(p.level)), style = F.small.copy(color = C.gold))
                }
            }
            item { GhostButton(stringResource(R.string.mentor_add_potion), Modifier.fillMaxWidth().padding(top = 6.dp)) { editPotion = 0 } }
            item {
                SectionLabel(stringResource(R.string.mentor_settings))
                SettingRow(stringResource(R.string.mentor_name), w.settings.name) { editName = true }
                SettingRow(stringResource(R.string.mentor_change_pin), "••••") { changePin = true }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.mentor_engrave), style = F.body)
                        Text(stringResource(R.string.mentor_engrave_sub), style = F.small)
                    }
                    Switch(w.settings.engrave, { v -> c.write { settings.update { it.copy(engrave = v) } } }, colors = switchColors())
                }
                if (!Alarms.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) SettingRow(stringResource(R.string.mentor_notifications), "") { notifyAsk.launch(Manifest.permission.POST_NOTIFICATIONS) }
                Text(stringResource(R.string.mentor_language), style = F.body, modifier = Modifier.padding(top = 12.dp))
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("" to stringResource(R.string.lang_system), "ru" to "Русский", "en" to "English").forEach { (tag, label) ->
                        Chip(label, current == tag) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                    }
                }
                SectionLabel(stringResource(R.string.mentor_how))
                Text(stringResource(R.string.mentor_how_text), style = F.small)
            }
        }
    }

    if (editDeed >= 0) DeedEditor(w.allDeeds.firstOrNull { it.id == editDeed }, onDismiss = { editDeed = -1 }) { d ->
        editDeed = -1
        c.write { if (d.id == 0L) db.dao().insert(d.copy(sort = w.allDeeds.size)) else db.dao().update(d) }
    }
    if (editPotion >= 0) PotionEditor(w.potions.firstOrNull { it.id == editPotion }, onDismiss = { editPotion = -1 }) { p ->
        editPotion = -1
        c.write { if (p.id == 0L) db.dao().insert(p) else db.dao().update(p) }
    }
    if (openCharm > 0) w.charms.firstOrNull { it.id == openCharm }?.let { PageDialog(it, w) { openCharm = 0 } }
    if (changePin) NewPinDialog(onDismiss = { changePin = false }) { p -> changePin = false; c.write { settings.update { it.copy(pin = p) } } }
    if (editName) TextDialog(stringResource(R.string.mentor_name), w.settings.name, onDismiss = { editName = false }) { n -> editName = false; c.write { settings.update { it.copy(name = n.trim()) } } }
}

private fun days(ctx: android.content.Context, mask: Int): String =
    if (mask == 0b1111111) "" else (1..7).filter { (mask shr (it - 1)) and 1 == 1 }.joinToString(" ") { ctx.getString(dayShort(it)) }

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = F.body, modifier = Modifier.weight(1f))
        Text(value, style = F.body.copy(color = C.gold))
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, if (on) C.gold else C.line, RoundedCornerShape(14.dp))
            .background(if (on) C.gold.copy(alpha = .14f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, style = F.small.copy(color = if (on) C.gold else C.dim)) }
}

@Composable
fun switchColors() = SwitchDefaults.colors(checkedThumbColor = C.night, checkedTrackColor = C.gold, uncheckedTrackColor = C.panel2, uncheckedBorderColor = C.line, uncheckedThumbColor = C.dim)

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.gold, unfocusedBorderColor = C.line, focusedLabelColor = C.gold, unfocusedLabelColor = C.dim,
    cursorColor = C.gold, focusedTextColor = C.text, unfocusedTextColor = C.text,
    focusedContainerColor = C.night2, unfocusedContainerColor = C.night2,
)

@Composable
private fun TextDialog(title: String, value: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.night2,
        title = { Text(title, style = F.title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, colors = fieldColors()) },
        confirmButton = { TextButton(onClick = { onOk(t) }) { Text(stringResource(R.string.edit_save), color = C.gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel), color = C.dim) } },
    )
}

@Composable
private fun NewPinDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var first by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.night2,
        title = { Text(stringResource(if (first.isEmpty()) R.string.mentor_change_pin else R.string.ob_pin_again), style = F.title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PinPad(typed) { d ->
                    typed = if (d == -1) typed.dropLast(1) else (typed + d).take(4)
                    if (typed.length == 4) {
                        if (first.isEmpty()) { first = typed; typed = "" }
                        else if (typed == first) onOk(typed) else { first = ""; typed = "" }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel), color = C.dim) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeedEditor(deed: Deed?, onDismiss: () -> Unit, onSave: (Deed) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(deed?.title ?: "") }
    var part by remember { mutableStateOf(deed?.part ?: Part.DAY.name) }
    var days by remember { mutableIntStateOf(deed?.days ?: 0b1111111) }
    var photo by remember { mutableStateOf(deed?.photo ?: true) }
    var hint by remember { mutableStateOf(deed?.hint ?: "") }
    var sigil by remember { mutableIntStateOf(deed?.sigil ?: 5) }
    var creature by remember { mutableIntStateOf(deed?.creature ?: 0) }
    var archived by remember { mutableStateOf(deed?.archived ?: false) }
    val time = rememberTimePickerState((deed?.magicHour ?: (18 * 60)) / 60, (deed?.magicHour ?: (18 * 60)) % 60, is24Hour = android.text.format.DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.night2,
        title = { Text(stringResource(R.string.edit_deed), style = F.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.edit_title)) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Label(stringResource(R.string.edit_part))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Part.entries.forEach { p -> Chip(stringResource(p.title), part == p.name) { part = p.name } }
                }
                Label(stringResource(R.string.edit_hour))
                Text(stringResource(R.string.edit_hour_sub), style = F.small)
                TimeInput(time)
                Label(stringResource(R.string.edit_days))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { i -> Chip(stringResource(dayShort(i)), (days shr (i - 1)) and 1 == 1) { days = days xor (1 shl (i - 1)) } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.edit_photo), style = F.body, modifier = Modifier.weight(1f))
                    Switch(photo, { photo = it }, colors = switchColors())
                }
                if (photo) OutlinedTextField(hint, { hint = it }, label = { Text(stringResource(R.string.edit_hint)) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Label(stringResource(R.string.edit_sigil))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sigils.all.indices.forEach { i ->
                        Box(Modifier.size(44.dp).clip(CircleShape).border(1.dp, if (sigil == i) C.gold else C.line, CircleShape).clickable { sigil = i }, contentAlignment = Alignment.Center) {
                            Sigil(i, if (sigil == i) C.gold else C.dim, 28.dp)
                        }
                    }
                }
                Label(stringResource(R.string.edit_creature))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Creature.entries.forEach { cr ->
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, if (creature == cr.ordinal) C.gold else C.line, RoundedCornerShape(12.dp)).clickable { creature = cr.ordinal }, contentAlignment = Alignment.Center) {
                            CreatureArt(cr, if (creature == cr.ordinal) C.gold else C.dim, 40.dp, bg = C.night2)
                        }
                    }
                }
                Text(stringResource(Creature.of(creature).title), style = F.small.copy(color = C.gold), modifier = Modifier.padding(top = 4.dp))
                if (deed != null) TextButton(onClick = { archived = !archived }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(if (archived) R.string.edit_restore else R.string.edit_archive), color = if (archived) C.gold else C.bad)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && days != 0, onClick = {
                onSave(
                    (deed ?: Deed(title = "", part = part, magicHour = 0)).copy(
                        title = title.trim(), part = part, magicHour = time.hour * 60 + time.minute, days = days, photo = photo,
                        hint = hint.trim(), sigil = sigil, creature = creature, archived = archived,
                    )
                )
            }) { Text(stringResource(R.string.edit_save), color = C.gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel), color = C.dim) } },
    )
}

@Composable
private fun Label(text: String) = Text(text, style = F.label, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PotionEditor(potion: Potion?, onDismiss: () -> Unit, onSave: (Potion) -> Unit) {
    var name by remember { mutableStateOf(potion?.name ?: "") }
    var reward by remember { mutableStateOf(potion?.reward ?: "") }
    var level by remember { mutableIntStateOf(potion?.level ?: 1) }
    var hue by remember { mutableIntStateOf(potion?.hue ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.night2,
        title = { Text(stringResource(R.string.edit_potion), style = F.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.edit_potion_name)) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(reward, { reward = it }, label = { Text(stringResource(R.string.edit_potion_reward)) }, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Label(stringResource(R.string.edit_potion_level))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..3).forEach { l -> Chip(stringResource(levelName(l)), level == l) { level = l } } }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    C.potions.indices.forEach { i ->
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, if (hue == i) C.gold else C.line, RoundedCornerShape(10.dp)).clickable { hue = i }, contentAlignment = Alignment.Center) {
                            Bottle(level, i, Modifier.size(38.dp))
                        }
                    }
                }
                if (potion != null) TextButton(onClick = { onSave(potion.copy(archived = true)) }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.edit_remove), color = C.bad) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave((potion ?: Potion(name = "", reward = "", level = 1)).copy(name = name.trim(), reward = reward.trim(), level = level, hue = hue))
            }) { Text(stringResource(R.string.edit_save), color = C.gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel), color = C.dim) } },
    )
}

