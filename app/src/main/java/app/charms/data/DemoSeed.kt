package app.charms.data

import android.content.Context
import android.graphics.BitmapFactory
import app.charms.AppContainer
import app.charms.domain.Magic
import app.charms.domain.Presets
import app.charms.domain.Tier
import java.io.InputStream
import kotlin.random.Random

/**
 * The demo build (`-Pdemo`): Masha's grimoire with two weeks of charms, engraved from the demo
 * pictures in assets/demo (one folder of pictures per deed sigil: "s<sigil>-*.jpg"), a brewed potion
 * and a creature or two.
 */
object DemoSeed {
    suspend fun seedIfEmpty(
        context: Context,
        c: AppContainer,
        list: () -> List<String> = { context.assets.list("demo")?.toList().orEmpty() },
        open: (String) -> InputStream = { context.assets.open(it) },
    ) {
        val dao = c.db.dao()
        if (c.settings.now().onboarded || dao.allDeeds().isNotEmpty()) return
        val ru = context.resources.configuration.locales[0].language == "ru"
        c.grimoire.start(context, if (ru) "Маша" else "Masha", "1234", Presets.all.indices.filter { Presets.all[it].on })
        val deeds = dao.deeds()
        val pics = list().filter { it.endsWith(".jpg") }.sorted()
        val today = Magic.today()
        val r = Random(11)
        for (back in 13 downTo 0) {
            val day = today.minusDays(back.toLong())
            for (d in Magic.deedsOn(deeds, day)) {
                // today: only the morning is done so far
                if (back == 0 && d.magicHour > 12 * 60) continue
                if (r.nextInt(10) == 0) continue
                val roll = r.nextInt(10)
                val tier = when {
                    back < 4 && d.title.isNotEmpty() && roll < 8 -> Tier.GOLD
                    roll < 6 -> Tier.GOLD
                    roll < 9 -> Tier.SILVER
                    else -> Tier.INK
                }
                val at = Magic.at(day, d.magicHour + if (tier == Tier.GOLD) -12 else 25)
                val earlier = dao.charms(day.minusDays(20).toEpochDay(), day.toEpochDay() - 1)
                val visitor = Magic.visitor(d, tier, day, earlier)
                // Engrave only the last few days; older pages are seals so the demo starts quickly.
                val mine = pics.filter { it.startsWith("s${d.sigil}-") }
                val file = if (d.photo && back <= 3 && mine.isNotEmpty()) {
                    val name = mine[(back + d.id.toInt()) % mine.size]
                    val bmp = open("demo/$name").use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) c.grimoire.engraveAndStore(context, bmp, d, tier, day, visitor) else ""
                } else ""
                dao.insert(Charm(deedId = d.id, day = day.toEpochDay(), at = at, tier = tier.value, file = file, visitor = visitor))
            }
        }
        dao.allPotions().firstOrNull { it.level == 2 }?.let { p ->
            dao.insert(Brew(week = Magic.monday(today).minusDays(7).toEpochDay(), potionId = p.id, level = 2, at = System.currentTimeMillis() - 86_400_000L))
        }
    }
}
