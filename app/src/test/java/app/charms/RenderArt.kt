package app.charms

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import app.charms.art.Engraver
import app.charms.domain.Creature
import app.charms.domain.Tier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Engraves every picture in -Pphotos=… (or the demo pictures) in each metal, to app/build/art/engravings (run with `-Prender`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArt {
    @Test
    fun engravings() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val out = File(System.getProperty("render.out") ?: "build/art", "engravings").apply { mkdirs() }
        val photos = File(System.getProperty("render.photos")!!).listFiles { f -> f.name.endsWith(".jpg") }!!.sorted()
        val fonts = Engraver.Fonts(ResourcesCompat.getFont(ctx, R.font.cormorant_bold)!!, ResourcesCompat.getFont(ctx, R.font.cormorant_italic)!!)
        photos.forEachIndexed { i, f ->
            val tier = Tier.entries[2 - i % 3]
            val bmp = BitmapFactory.decodeFile(f.path)
            val spec = Engraver.Spec(
                title = listOf("Заправить кровать", "Почистить зубы", "Сделать уроки", "Убрать игрушки", "Собрать портфель", "Погулять")[i % 6],
                date = "2 ноября 2026", tierLabel = listOf("золото", "серебро", "тушь")[i % 3], tier = tier, sigil = i % 12,
                patron = Creature.entries[i % 8], visitor = if (i % 4 == 1) Creature.entries[(i + 3) % 8] else null,
                visitorLabel = if (i % 4 == 1) "в гостях: Лунный заяц" else "",
            )
            val t0 = System.currentTimeMillis()
            val page = Engraver.engrave(bmp, spec, fonts)
            println("engraved ${f.name} in ${System.currentTimeMillis() - t0} ms")
            File(out, f.nameWithoutExtension + ".png").outputStream().use { page.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
