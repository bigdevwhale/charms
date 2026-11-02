package app.charms.domain

import app.charms.R
import app.charms.data.Bell
import app.charms.data.Charm
import app.charms.data.Deed
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The four spells of a day: a part of the day is a spell, its deeds are the spell's runes. */
enum class Part(val title: Int, val spell: Int, val from: Int) {
    MORNING(R.string.part_morning, R.string.spell_morning, 0),
    DAY(R.string.part_day, R.string.spell_day, 11 * 60),
    EVENING(R.string.part_evening, R.string.spell_evening, 17 * 60),
    NIGHT(R.string.part_night, R.string.spell_night, 21 * 60);

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: DAY
        fun at(minutes: Int) = entries.last { minutes >= it.from }
    }
}

/** How strong a charm came out. */
enum class Tier(val value: Int, val title: Int) {
    INK(1, R.string.tier_ink), SILVER(2, R.string.tier_silver), GOLD(3, R.string.tier_gold);

    companion object {
        fun of(v: Int) = entries.firstOrNull { it.value == v } ?: INK
    }
}

/** The grimoire's creatures: each deed has a patron, who visits after three golds in a row. */
enum class Creature(val title: Int, val about: Int) {
    OWL(R.string.cr_owl, R.string.cr_owl_about),
    CAT(R.string.cr_cat, R.string.cr_cat_about),
    FOX(R.string.cr_fox, R.string.cr_fox_about),
    HARE(R.string.cr_hare, R.string.cr_hare_about),
    RAVEN(R.string.cr_raven, R.string.cr_raven_about),
    HEDGEHOG(R.string.cr_hedgehog, R.string.cr_hedgehog_about),
    SNAIL(R.string.cr_snail, R.string.cr_snail_about),
    FIREFLY(R.string.cr_firefly, R.string.cr_firefly_about);

    companion object {
        fun of(i: Int) = entries.getOrElse(i) { OWL }
    }
}

/** The young sorcerer's rank grows with the charms in the grimoire. */
enum class Rank(val title: Int, val from: Int) {
    APPRENTICE(R.string.rank_1, 0), JOURNEYMAN(R.string.rank_2, 25), SORCERER(R.string.rank_3, 80), ARCHMAGE(R.string.rank_4, 200);

    companion object {
        fun of(charms: Int) = entries.last { charms >= it.from }
        fun next(charms: Int) = entries.firstOrNull { it.from > charms }
    }
}

object Magic {
    fun zone(): ZoneId = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone())
    fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()
    fun minutesOf(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(zone()).let { it.hour * 60 + it.minute }
    fun at(day: LocalDate, minutes: Int): Long = day.atStartOfDay(zone()).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()

    fun asked(d: Deed, day: LocalDate): Boolean = !d.archived && (d.days shr (day.dayOfWeek.value - 1)) and 1 == 1

    /** The deeds asked for on a day, in the order of their magic hours. */
    fun deedsOn(deeds: List<Deed>, day: LocalDate) = deeds.filter { asked(it, day) }.sortedBy { it.magicHour }

    /** Gold before the page lights up, silver after, ink once the mentor has had to ring the bell. */
    fun tier(d: Deed, at: Long, bell: Bell?): Tier = when {
        bell != null && bell.at <= at -> Tier.INK
        minutesOf(at) <= d.magicHour -> Tier.GOLD
        else -> Tier.SILVER
    }

    /** A creature comes to visit the third gold in a row of the same deed (if no creature came in the last two). */
    fun visitor(d: Deed, tier: Tier, day: LocalDate, earlier: List<Charm>): Int {
        if (tier != Tier.GOLD) return -1
        val mine = earlier.filter { it.deedId == d.id && !it.blot }.associateBy { it.day }
        var run = 0
        var dd = day.minusDays(1)
        var guard = 0
        while (guard++ < 14) {
            if (!asked(d, dd)) { dd = dd.minusDays(1); continue }
            val c = mine[dd.toEpochDay()] ?: break
            if (c.tier != Tier.GOLD.value || c.visitor >= 0) break
            run++
            if (run == 2) return d.creature
            dd = dd.minusDays(1)
        }
        return -1
    }

    /** Which parts of the day have all their deeds charmed (with no blot): finished spells. */
    fun spells(deeds: List<Deed>, charms: List<Charm>, day: LocalDate): List<Part> {
        val on = deedsOn(deeds, day)
        val done = charms.filter { it.day == day.toEpochDay() && !it.blot }.map { it.deedId }.toSet()
        return Part.entries.filter { p -> on.any { it.part == p.name } && on.filter { it.part == p.name }.all { it.id in done } }
    }

    // ---- the cauldron ----

    fun monday(day: LocalDate): LocalDate = day.with(DayOfWeek.MONDAY)

    class Week(val monday: LocalDate, val points: Int, val max: Int, val days: List<DayFill>) {
        val fill: Float get() = if (max == 0) 0f else (points.toFloat() / max).coerceIn(0f, 1f)
        /** 0: not boiling yet, 1 small, 2 middling, 3 great. */
        val level: Int get() = when {
            max == 0 -> 0
            fill >= GREAT -> 3
            fill >= MIDDLING -> 2
            fill >= SMALL -> 1
            else -> 0
        }
    }

    class DayFill(val day: LocalDate, val gold: Int, val silver: Int, val ink: Int, val asked: Int, val rest: Boolean, val spells: Int)

    const val SMALL = .3f
    const val MIDDLING = .55f
    const val GREAT = .8f
    const val SPELL_BONUS = 2

    /**
     * A week of charms poured into the cauldron: gold 3, silver 2, ink 1, a blot nothing, a finished
     * spell 2 more. Rest days ask nothing; days still to come count only once they have come.
     */
    fun week(monday: LocalDate, deeds: List<Deed>, charms: List<Charm>, rests: Set<Long>, today: LocalDate): Week {
        var points = 0
        var max = 0
        val fills = (0..6).map { i ->
            val day = monday.plusDays(i.toLong())
            val e = day.toEpochDay()
            val on = deedsOn(deeds, day)
            val mine = charms.filter { it.day == e && !it.blot }
            val rest = e in rests
            val spells = if (rest) 0 else spells(deeds, charms, day).size
            if (!day.isAfter(today) && !rest) {
                val parts = on.map { it.part }.distinct().size
                // Today counts as it goes: what isn't done yet doesn't lower the cauldron before the day is over.
                if (day.isBefore(today)) max += on.size * 3 + parts * SPELL_BONUS
                else max += mine.size * 3 + spells * SPELL_BONUS
            }
            if (!rest) points += mine.sumOf { it.tier } + spells * SPELL_BONUS
            DayFill(day, mine.count { it.tier == 3 }, mine.count { it.tier == 2 }, mine.count { it.tier == 1 }, on.size, rest, spells)
        }
        // A week that has only just begun shouldn't look full after one gold.
        val whole = (0..6).sumOf { i ->
            val day = monday.plusDays(i.toLong())
            if (day.toEpochDay() in rests) 0 else deedsOn(deeds, day).let { on -> on.size * 3 + on.map { it.part }.distinct().size * SPELL_BONUS }
        }
        return Week(monday, points, if (monday.plusDays(6).isAfter(today)) whole else max, fills)
    }

    /** The cauldron can be brewed on Sunday, and all the next week if it was forgotten. */
    fun canBrew(monday: LocalDate, today: LocalDate) = !today.isBefore(monday.plusDays(6)) && today.isBefore(monday.plusDays(14))

    fun rank(charms: List<Charm>) = Rank.of(charms.count { !it.blot })
}

/** Deeds a family can start with; every one is editable later. */
class Preset(val title: Int, val hint: Int, val part: Part, val hour: Int, val photo: Boolean, val sigil: Int, val creature: Creature, val on: Boolean)

object Presets {
    val all = listOf(
        Preset(R.string.pr_bed, R.string.pr_bed_hint, Part.MORNING, 8 * 60, true, 2, Creature.CAT, true),
        Preset(R.string.pr_teeth_am, R.string.pr_teeth_hint, Part.MORNING, 8 * 60 + 10, true, 0, Creature.FOX, true),
        Preset(R.string.pr_breakfast, R.string.pr_breakfast_hint, Part.MORNING, 8 * 60 + 30, true, 3, Creature.HEDGEHOG, false),
        Preset(R.string.pr_homework, R.string.pr_homework_hint, Part.DAY, 17 * 60, true, 4, Creature.OWL, true),
        Preset(R.string.pr_walk, R.string.pr_walk_hint, Part.DAY, 16 * 60, true, 9, Creature.HARE, false),
        Preset(R.string.pr_toys, R.string.pr_toys_hint, Part.EVENING, 19 * 60 + 30, true, 5, Creature.HEDGEHOG, true),
        Preset(R.string.pr_bag, R.string.pr_bag_hint, Part.EVENING, 20 * 60, true, 6, Creature.RAVEN, true),
        Preset(R.string.pr_shower, R.string.pr_shower_hint, Part.EVENING, 20 * 60 + 30, true, 8, Creature.SNAIL, false),
        Preset(R.string.pr_teeth_pm, R.string.pr_teeth_hint, Part.NIGHT, 21 * 60, true, 0, Creature.FOX, true),
        Preset(R.string.pr_read, R.string.pr_read_hint, Part.NIGHT, 21 * 60 + 10, true, 4, Creature.OWL, false),
        Preset(R.string.pr_sleep, R.string.pr_sleep_hint, Part.NIGHT, 21 * 60 + 30, false, 7, Creature.FIREFLY, true),
    )

    class PotionPreset(val name: Int, val reward: Int, val level: Int, val hue: Int)

    val potions = listOf(
        PotionPreset(R.string.po_feast, R.string.po_feast_r, 1, 0),
        PotionPreset(R.string.po_tale, R.string.po_tale_r, 1, 1),
        PotionPreset(R.string.po_invis, R.string.po_invis_r, 2, 2),
        PotionPreset(R.string.po_owl, R.string.po_owl_r, 2, 3),
        PotionPreset(R.string.po_flight, R.string.po_flight_r, 3, 4),
    )
}
