package app.charms.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.charms.R
import app.charms.container
import app.charms.domain.Presets
import app.charms.ui.T
import app.charms.ui.components.GoldButton
import app.charms.ui.components.Sigil
import app.charms.ui.components.Star
import app.charms.ui.mentor.PinPad
import app.charms.ui.mentor.fieldColors
import app.charms.ui.theme.C
import app.charms.ui.theme.F

@Composable
fun OnboardingScreen() {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(Presets.all.indices.filter { Presets.all[it].on }.joinToString(",")) }
    var first by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("") }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val chosen = picked.split(',').mapNotNull { it.toIntOrNull() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            0 -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Spacer(Modifier.height(40.dp))
                    Star(C.goldLight, 54.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(R.string.ob_title), style = F.display.copy(fontSize = F.display.fontSize * 1.5f, color = C.goldLight))
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.ob_lead), style = F.italic.copy(color = C.text), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(36.dp))
                    Text(stringResource(R.string.ob_name), style = F.heading)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(name, { name = it }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                }
                GoldButton(stringResource(R.string.ob_next), Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { step = 1 }
            }
            1 -> {
                Text(stringResource(R.string.ob_deeds), style = F.title, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.ob_deeds_sub), style = F.small, textAlign = TextAlign.Center)
                Column(Modifier.weight(1f).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                    Presets.all.forEachIndexed { i, p ->
                        val on = i in chosen
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                picked = (if (on) chosen - i else chosen + i).sorted().joinToString(",")
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, if (on) C.gold else C.line, CircleShape), contentAlignment = Alignment.Center) {
                                Sigil(p.sigil, if (on) C.gold else C.faint, 24.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(p.title), style = F.heading.copy(color = if (on) C.text else C.dim))
                                Text(T.minutes(ctx, p.hour) + " · " + stringResource(p.part.title), style = F.small)
                            }
                            Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = C.gold, checkmarkColor = C.night, uncheckedColor = C.line))
                        }
                    }
                }
                GoldButton(stringResource(R.string.ob_next), Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty()) { step = 2 }
            }
            else -> {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(if (first.isEmpty()) R.string.ob_pin else R.string.ob_pin_again), style = F.title)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.ob_pin_sub), style = F.small, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    PinPad(typed) { d ->
                        typed = if (d == -1) typed.dropLast(1) else (typed + d).take(4)
                        if (typed.length == 4) {
                            if (first.isEmpty()) { first = typed; typed = "" }
                            else if (typed != first) { first = ""; typed = "" }
                        }
                    }
                }
                val ready = first.length == 4 && typed == first
                GoldButton(stringResource(R.string.ob_start), Modifier.fillMaxWidth(), enabled = ready) {
                    if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    ctx.container.write { grimoire.start(ctx, name, first, chosen) }
                }
            }
        }
    }
}
