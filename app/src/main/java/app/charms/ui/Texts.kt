package app.charms.ui

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Words and dates in the app's language. */
object T {
    private fun locale(c: Context): Locale = c.resources.configuration.locales[0]

    /** "Monday, 2 November". */
    fun weekday(c: Context, d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale(c))).replaceFirstChar { it.titlecase(locale(c)) }

    /** "2 November". */
    fun day(c: Context, d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMMM", locale(c)))

    /** "2–8 Nov". */
    fun week(c: Context, monday: LocalDate): String {
        val end = monday.plusDays(6)
        val f = DateTimeFormatter.ofPattern("d MMM", locale(c))
        return if (monday.month == end.month) "${monday.dayOfMonth}–" + end.format(f) else monday.format(f) + " – " + end.format(f)
    }

    fun minutes(c: Context, m: Int): String =
        LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(c)))
}
