package app.charms

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.charms.data.CharmsDatabase
import app.charms.data.DemoSeed
import app.charms.data.SettingsStore
import app.charms.data.Vault
import app.charms.notify.Alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(val context: Context) {
    val db = CharmsDatabase.create(context)
    val settings = SettingsStore(context)
    val vault = Vault(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val grimoire = app.charms.data.Grimoire(this)

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch {
            block()
            changed()
        }
    }

    /** Something changed: the next page to light up may be a different one now. */
    suspend fun changed() = Alarms.reschedule(context)
}

class CharmsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Alarms.ensureChannels(this)
        // Re-arm on every start: OEM task killers (HyperOS & co.) silently drop alarms.
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(this@CharmsApp, container)
            container.changed()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as CharmsApp).container

/** Resources in the in-app language, for receivers that AppCompat doesn't localise before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}
