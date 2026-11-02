package app.charms

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import app.charms.data.DemoSeed
import app.charms.domain.Magic
import app.charms.ui.World
import app.charms.ui.cauldron.CauldronTab
import app.charms.ui.charm.RevealScreen
import app.charms.ui.components.NightSky
import app.charms.ui.grimoire.GrimoireTab
import app.charms.ui.mentor.MentorScreen
import app.charms.ui.onboarding.OnboardingScreen
import app.charms.ui.theme.CharmsTheme
import app.charms.ui.today.TodayTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/**
 * Draws the main screens over the demo grimoire to app/build/art/screens-<lang> (run with `-Prender`),
 * then `python tools/shots.py` makes the README screenshots. "Today" is the coming Sunday at 19:40.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class RenderBase(private val lang: String) {
    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }
    private val photos = File(System.getProperty("render.photos") ?: "src/demo/assets/demo")

    private fun settle(ms: Long = 2500) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (t < ms) { Thread.sleep(40); rule.mainClock.advanceTimeBy(100); t += 100 }
    }

    private fun shot(name: String) {
        ShadowLooper.idleMainLooper()
        rule.mainClock.advanceTimeByFrame()
        ShadowLooper.idleMainLooper()
        val root = rule.activity.window.decorView
        val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun screens() {
        val app = ApplicationProvider.getApplicationContext<CharmsApp>()
        val sunday = Magic.today().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val now = Magic.at(sunday, 19 * 60 + 40)
        val c = app.container
        val w = runBlocking {
            DemoSeed.seedIfEmpty(app, c, list = { photos.list()?.toList().orEmpty() }, open = { File(photos, it.removePrefix("demo/")).inputStream() }, today = sunday)
            val dao = c.db.dao()
            World(
                c.settings.now(), dao.deeds(), dao.allDeeds(), dao.charmsFlow().first(), dao.bellsFlow().first(),
                dao.potionsFlow().first(), dao.brewsFlow().first(), emptySet(), now,
            )
        }
        val shown = w.charms.filter { it.day == sunday.toEpochDay() && it.file.isNotEmpty() }.maxByOrNull { it.at }
        var screen by androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            CharmsTheme {
                Box(Modifier.fillMaxSize()) {
                    NightSky()
                    when (screen) {
                        0 -> OnboardingScreen()
                        1 -> TodayTab(w, {}, {})
                        2 -> RevealScreen(shown?.deedId ?: 0, seal = true, onDone = {}, today = sunday)
                        3 -> CauldronTab(w)
                        4 -> GrimoireTab(w)
                        5 -> GrimoireTab(w, startView = 1)
                        else -> MentorScreen(w, onClose = {})
                    }
                }
            }
        }
        listOf("0-welcome", "1-today", "2-charm", "3-cauldron", "4-grimoire", "5-bestiary", "6-mentor").forEachIndexed { i, name ->
            screen = i
            settle(if (i == 2) 5000 else 2500)
            shot(name)
        }
        if (lang == "en") {
            val d = ContextCompat.getDrawable(app, R.mipmap.ic_launcher)!!
            val b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 512, 512); d.draw(Canvas(b))
            File(System.getProperty("render.out") ?: "build/art", "logo.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

@Config(sdk = [34], qualifiers = "en-w393dp-h852dp-xxhdpi")
class RenderScreensEn : RenderBase("en")

@Config(sdk = [34], qualifiers = "ru-w393dp-h852dp-xxhdpi")
class RenderScreensRu : RenderBase("ru")
