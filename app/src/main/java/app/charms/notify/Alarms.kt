package app.charms.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.charms.MainActivity
import app.charms.R
import app.charms.container
import app.charms.data.Deed
import app.charms.domain.Magic
import app.charms.localized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A page lights up at its magic hour if it isn't charmed yet: that is the app's reminder, so the
 * grown-ups don't have to be. One alarm, set for the next page to light; each page lights once a day.
 */
object Alarms {
    private const val PAGES = "pages_v1"
    private const val BELL = "bell_v1"
    private const val ACTION = "app.charms.notify.GLOW"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val l = context.localized()
        if (nm.getNotificationChannel(PAGES) == null) nm.createNotificationChannel(
            NotificationChannel(PAGES, l.getString(R.string.channel_pages), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = l.getString(R.string.channel_pages_desc)
            }
        )
        if (nm.getNotificationChannel(BELL) == null) nm.createNotificationChannel(
            NotificationChannel(BELL, l.getString(R.string.channel_bell), NotificationManager.IMPORTANCE_HIGH).apply {
                description = l.getString(R.string.channel_bell_desc)
                enableVibration(true)
            }
        )
    }

    private fun pi(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, AlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun key(day: Long, deed: Long) = "$day:$deed"

    /** Pages that will light (or have lit) today and tomorrow, not yet charmed: (time, deed). */
    private suspend fun plan(context: Context): List<Pair<Long, Deed>> {
        val c = context.container
        val s = c.settings.now()
        if (!s.onboarded) return emptyList()
        val dao = c.db.dao()
        val today = Magic.today()
        val rests = dao.rests().map { it.day }.toSet()
        val deeds = dao.deeds()
        val charms = dao.charms(today.toEpochDay(), today.toEpochDay() + 1).map { key(it.day, it.deedId) }.toSet()
        val fired = s.fired.split(',').toSet()
        return listOf(today, today.plusDays(1)).flatMap { day ->
            if (day.toEpochDay() in rests) emptyList()
            else Magic.deedsOn(deeds, day)
                .filter { key(day.toEpochDay(), it.id) !in charms && key(day.toEpochDay(), it.id) !in fired }
                .map { Magic.at(day, it.magicHour) to it }
        }.sortedBy { it.first }
    }

    suspend fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        val now = System.currentTimeMillis()
        val next = plan(context).firstOrNull { it.first > now }?.first
        if (next == null) { am.cancel(p); return }
        // A page needn't light to the second; this avoids the exact-alarm permission.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, p)
    }

    /** Lights every page whose hour has come (within the last two hours: late is better than never, stale is noise). */
    suspend fun fire(context: Context) {
        val c = context.container
        val now = System.currentTimeMillis()
        val due = plan(context).filter { it.first <= now + 60_000 }
        if (due.isEmpty()) return
        val today = Magic.today().toEpochDay()
        val keys = due.map { key(Magic.dayOf(it.first).toEpochDay(), it.second.id) }
        c.settings.update { s ->
            // Forget old days so the list stays short.
            val kept = s.fired.split(',').filter { it.substringBefore(':').toLongOrNull()?.let { d -> d >= today - 1 } == true }
            s.copy(fired = (kept + keys).distinct().joinToString(","))
        }
        val fresh = due.filter { now - it.first < 2 * 3_600_000L }
        if (fresh.isEmpty() || !canNotify(context)) return
        val l = context.localized()
        if (fresh.size == 1) post(context, 10, PAGES, l.getString(R.string.notify_glow, fresh[0].second.title), l.getString(R.string.notify_glow_text))
        else post(context, 10, PAGES, l.getString(R.string.notify_glow_many, fresh.size), fresh.joinToString(" · ") { it.second.title })
    }

    /** The mentor rang: a quiet ding on the child's phone instead of a raised voice. */
    fun bell(context: Context, deed: Deed) {
        if (!canNotify(context)) return
        val l = context.localized()
        post(context, 20, BELL, l.getString(R.string.notify_bell, deed.title), l.getString(R.string.notify_bell_text))
    }

    private fun post(context: Context, id: Int, channel: String, title: String, text: String) {
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 100 + id,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    internal const val GLOW = ACTION
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Alarms.GLOW) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Alarms.fire(context)
                Alarms.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pending = goAsync()
        receiverScope.launch {
            try { Alarms.reschedule(context) } finally { pending.finish() }
        }
    }
}
