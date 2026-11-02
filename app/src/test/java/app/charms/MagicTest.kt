package app.charms

import app.charms.data.Bell
import app.charms.data.Charm
import app.charms.data.Deed
import app.charms.domain.Magic
import app.charms.domain.Part
import app.charms.domain.Tier
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MagicTest {
    private val bed = Deed(id = 1, title = "bed", part = Part.MORNING.name, magicHour = 8 * 60, creature = 1)
    private val teeth = Deed(id = 2, title = "teeth", part = Part.MORNING.name, magicHour = 8 * 60 + 10, creature = 2)
    private val day = LocalDate.of(2026, 11, 8)

    @Test
    fun goldBeforeTheHourSilverAfterInkAfterTheBell() {
        assertEquals(Tier.GOLD, Magic.tier(bed, Magic.at(day, 7 * 60 + 55), null))
        assertEquals(Tier.SILVER, Magic.tier(bed, Magic.at(day, 8 * 60 + 5), null))
        val bell = Bell(deedId = 1, day = day.toEpochDay(), at = Magic.at(day, 8 * 60 + 20))
        assertEquals(Tier.INK, Magic.tier(bed, Magic.at(day, 8 * 60 + 30), bell))
        // a charm cast before the bell rang keeps its own metal
        assertEquals(Tier.SILVER, Magic.tier(bed, Magic.at(day, 8 * 60 + 15), bell))
    }

    private fun gold(d: Deed, on: LocalDate, visitor: Int = -1) = Charm(deedId = d.id, day = on.toEpochDay(), at = 0, tier = 3, visitor = visitor)

    @Test
    fun theThirdGoldInARowBringsThePatron() {
        val two = listOf(gold(bed, day.minusDays(1)), gold(bed, day.minusDays(2)))
        assertEquals(1, Magic.visitor(bed, Tier.GOLD, day, two))
        assertEquals(-1, Magic.visitor(bed, Tier.SILVER, day, two))
        assertEquals(-1, Magic.visitor(bed, Tier.GOLD, day, two.take(1)))
        // the creature that just came doesn't come again the next day
        val after = listOf(gold(bed, day.minusDays(1), visitor = 1), gold(bed, day.minusDays(2)))
        assertEquals(-1, Magic.visitor(bed, Tier.GOLD, day, after))
    }

    @Test
    fun aFinishedSpellAddsTwoDrops() {
        val charms = listOf(gold(bed, day), gold(teeth, day))
        assertEquals(listOf(Part.MORNING), Magic.spells(listOf(bed, teeth), charms, day))
        val week = Magic.week(Magic.monday(day), listOf(bed, teeth), charms, emptySet(), day)
        assertEquals(3 + 3 + Magic.SPELL_BONUS, week.points)
    }

    @Test
    fun aRestDayAsksNothing() {
        val monday = Magic.monday(day)
        val all = Magic.week(monday, listOf(bed), emptyList(), emptySet(), day)
        val rested = Magic.week(monday, listOf(bed), emptyList(), setOf(monday.toEpochDay()), day)
        assertEquals(all.max - 3 - Magic.SPELL_BONUS, rested.max)
    }
}
