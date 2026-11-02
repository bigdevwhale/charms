package app.charms.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.charms.R
import app.charms.container
import app.charms.data.Settings
import app.charms.domain.Magic
import app.charms.ui.cauldron.CauldronTab
import app.charms.ui.charm.DeedSheet
import app.charms.ui.charm.RevealScreen
import app.charms.ui.components.NightSky
import app.charms.ui.grimoire.GrimoireTab
import app.charms.ui.mentor.MentorScreen
import app.charms.ui.mentor.PinDialog
import app.charms.ui.onboarding.OnboardingScreen
import app.charms.ui.theme.C
import app.charms.ui.theme.F
import app.charms.ui.today.TodayTab
import kotlinx.coroutines.delay

/** Everything the screens read, collected once. */
class World(
    val settings: Settings,
    val deeds: List<app.charms.data.Deed>,
    val allDeeds: List<app.charms.data.Deed>,
    val charms: List<app.charms.data.Charm>,
    val bells: List<app.charms.data.Bell>,
    val potions: List<app.charms.data.Potion>,
    val brews: List<app.charms.data.Brew>,
    val rests: Set<Long>,
    /** Now, ticking every half minute so pages light up on screen too. */
    val now: Long,
) {
    val today get() = Magic.dayOf(now)
}

@Composable
fun CharmsRoot(settings: Settings) {
    if (!settings.onboarded) {
        Box(Modifier.fillMaxSize()) { NightSky(); OnboardingScreen() }
        return
    }
    val ctx = LocalContext.current
    val c = ctx.container
    val dao = c.db.dao()
    val deeds by dao.deedsFlow().collectAsStateWithLifecycle(emptyList())
    val allDeeds by dao.allDeedsFlow().collectAsStateWithLifecycle(emptyList())
    val charms by dao.charmsFlow().collectAsStateWithLifecycle(emptyList())
    val bells by dao.bellsFlow().collectAsStateWithLifecycle(emptyList())
    val potions by dao.potionsFlow().collectAsStateWithLifecycle(emptyList())
    val brews by dao.brewsFlow().collectAsStateWithLifecycle(emptyList())
    val rests by dao.restsFlow().collectAsStateWithLifecycle(emptyList())
    var now by androidx.compose.runtime.remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val w = World(settings, deeds, allDeeds, charms, bells, potions, brews, rests.map { it.day }.toSet(), now)

    var tab by rememberSaveable { mutableStateOf(0) }
    var sheet by rememberSaveable { mutableLongStateOf(0L) }
    var shooting by rememberSaveable { mutableLongStateOf(0L) }
    var reveal by rememberSaveable { mutableLongStateOf(0L) }
    var revealSeal by rememberSaveable { mutableStateOf(false) }
    var pin by rememberSaveable { mutableStateOf(false) }
    var mentor by rememberSaveable { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && shooting > 0) { reveal = shooting; revealSeal = false }
        shooting = 0
    }

    fun shoot(deedId: Long) {
        val f = c.vault.capture().apply { delete() }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        shooting = deedId
        sheet = 0
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            shooting = 0
            Toast.makeText(ctx, R.string.deed_no_camera, Toast.LENGTH_LONG).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        NightSky()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayTab(w, onDeed = { sheet = it }, onMentor = { pin = true })
                    1 -> CauldronTab(w)
                    else -> GrimoireTab(w)
                }
            }
            BottomBar(tab) { tab = it }
        }
        if (sheet > 0) w.deeds.firstOrNull { it.id == sheet }?.let { d ->
            DeedSheet(d, w, onDismiss = { sheet = 0 }, onShoot = { shoot(d.id) }, onSealed = { sheet = 0; reveal = d.id; revealSeal = true })
        }
        if (reveal > 0) {
            RevealScreen(reveal, seal = revealSeal, onDone = { reveal = 0; tab = 0 })
        }
        if (mentor) {
            BackHandler { mentor = false }
            MentorScreen(w, onClose = { mentor = false })
        }
        if (pin) PinDialog(settings.pin, onDismiss = { pin = false }, onOk = { pin = false; mentor = true })
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf(
        Triple(Icons.Outlined.AutoAwesome, R.string.tab_today, 0),
        Triple(Icons.Outlined.Science, R.string.tab_cauldron, 1),
        Triple(Icons.Outlined.AutoStories, R.string.tab_grimoire, 2),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, C.night.copy(alpha = .95f), C.night)))
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 6.dp),
    ) {
        items.forEach { (icon, label, i) -> BarItem(icon, stringResource(label), tab == i, Modifier.weight(1f)) { onTab(i) } }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (on) C.gold else C.faint, modifier = Modifier.size(26.dp))
        Box(Modifier.height(2.dp))
        Text(label, style = F.small.copy(color = if (on) C.gold else C.faint))
    }
}
