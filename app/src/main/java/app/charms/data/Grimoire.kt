package app.charms.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import app.charms.AppContainer
import app.charms.R
import app.charms.art.Engraver
import app.charms.domain.Creature
import app.charms.domain.Magic
import app.charms.domain.Presets
import app.charms.domain.Tier
import app.charms.localized
import app.charms.notify.Alarms
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a charm came to: shown on the reveal. */
class Cast(val charm: Charm, val deed: Deed, val spells: List<app.charms.domain.Part>)

/** Everything that changes the grimoire. */
class Grimoire(private val c: AppContainer) {
    private val dao get() = c.db.dao()

    fun fonts(context: Context) = Engraver.Fonts(
        ResourcesCompat.getFont(context, R.font.cormorant_bold)!!,
        ResourcesCompat.getFont(context, R.font.cormorant_italic)!!,
    )

    fun dateText(context: Context, day: LocalDate): String {
        val l = context.localized()
        return day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", l.resources.configuration.locales[0] ?: Locale.getDefault()))
    }

    /** Engraves a picture of a done deed (and deletes the picture). Null if the picture can't be read. */
    suspend fun charm(context: Context, deedId: Long, photo: File, at: Long = System.currentTimeMillis()): Cast? {
        val deed = dao.deed(deedId) ?: return null
        val bmp = Vault.decodeUpright(photo, 1600) ?: run { photo.delete(); return null }
        val day = Magic.dayOf(at)
        val tier = Magic.tier(deed, at, dao.bell(deedId, day.toEpochDay()))
        val visitor = Magic.visitor(deed, tier, day, dao.charms(day.minusDays(20).toEpochDay(), day.toEpochDay() - 1))
        val name = engraveAndStore(context, bmp, deed, tier, day, visitor)
        photo.delete()
        return save(deed, day, at, tier, name, visitor)
    }

    fun engraveAndStore(context: Context, bmp: Bitmap, deed: Deed, tier: Tier, day: LocalDate, visitor: Int): String {
        val l = context.localized()
        val v = if (visitor >= 0) Creature.of(visitor) else null
        val spec = Engraver.Spec(
            title = deed.title, date = dateText(context, day), tierLabel = l.getString(tier.title), tier = tier,
            sigil = deed.sigil, patron = Creature.of(deed.creature), visitor = v,
            visitorLabel = v?.let { l.getString(R.string.charm_visitor_short, l.getString(it.title)) } ?: "",
        )
        val page = Engraver.engrave(bmp, spec, fonts(context))
        bmp.recycle()
        val name = c.vault.put(page)
        page.recycle()
        return name
    }

    /** A deed without a picture: sealed with a touch. */
    suspend fun seal(deedId: Long, at: Long = System.currentTimeMillis()): Cast? {
        val deed = dao.deed(deedId) ?: return null
        val day = Magic.dayOf(at)
        val tier = Magic.tier(deed, at, dao.bell(deedId, day.toEpochDay()))
        val visitor = Magic.visitor(deed, tier, day, dao.charms(day.minusDays(20).toEpochDay(), day.toEpochDay() - 1))
        return save(deed, day, at, tier, "", visitor)
    }

    private suspend fun save(deed: Deed, day: LocalDate, at: Long, tier: Tier, file: String, visitor: Int): Cast {
        dao.charm(deed.id, day.toEpochDay())?.let { old -> c.vault.delete(old.file); dao.delete(old) }
        val charm = Charm(deedId = deed.id, day = day.toEpochDay(), at = at, tier = tier.value, file = file, visitor = visitor)
        val id = dao.insert(charm)
        val all = dao.charms(day.toEpochDay(), day.toEpochDay())
        val spells = Magic.spells(dao.deeds(), all, day)
        // Only the spell this charm finished is news.
        val before = Magic.spells(dao.deeds(), all.filter { it.deedId != deed.id }, day)
        c.changed()
        return Cast(charm.copy(id = id), deed, spells - before.toSet())
    }

    suspend fun ring(context: Context, deedId: Long) {
        val deed = dao.deed(deedId) ?: return
        val day = Magic.today().toEpochDay()
        if (dao.charm(deedId, day) != null) return
        dao.insert(Bell(deedId = deedId, day = day, at = System.currentTimeMillis()))
        Alarms.bell(context, deed)
    }

    suspend fun blot(charm: Charm, on: Boolean) = dao.update(charm.copy(blot = on))

    suspend fun erase(charm: Charm) {
        c.vault.delete(charm.file)
        dao.delete(charm)
        c.changed()
    }

    suspend fun rest(day: LocalDate, on: Boolean) {
        if (on) dao.insert(Rest(day.toEpochDay())) else dao.delete(Rest(day.toEpochDay()))
        c.changed()
    }

    suspend fun brew(week: LocalDate, potion: Potion, level: Int) {
        dao.insert(Brew(week = week.toEpochDay(), potionId = potion.id, level = level, at = System.currentTimeMillis()))
    }

    /** The first deeds and potions, in the language the family set the grimoire up in. */
    suspend fun start(context: Context, name: String, pin: String, presets: List<Int>) {
        val l = context.localized()
        presets.forEachIndexed { k, i ->
            val p = Presets.all[i]
            dao.insert(
                Deed(
                    title = l.getString(p.title), part = p.part.name, magicHour = p.hour, photo = p.photo, hint = l.getString(p.hint),
                    sigil = p.sigil, creature = p.creature.ordinal, sort = k,
                )
            )
        }
        if (dao.allPotions().isEmpty()) Presets.potions.forEach { p ->
            dao.insert(Potion(name = l.getString(p.name), reward = l.getString(p.reward), level = p.level, hue = p.hue))
        }
        c.settings.update { it.copy(onboarded = true, name = name.trim(), pin = pin) }
        c.changed()
    }
}
