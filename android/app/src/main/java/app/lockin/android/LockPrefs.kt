package app.lockin.android

import android.content.Context
import android.content.SharedPreferences
import app.lockin.core.LockState
import app.lockin.core.RedirectRules
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SharedPreferences wrapper for the lock state.
 *
 * Reads fail closed: if preferences cannot be read, the lock is treated as enabled.
 * Writes that fail are dropped quietly, so the lock state stays as it was last read.
 */
class LockPrefs(context: Context) {

    fun interface Listener {
        fun onEnabledChanged(enabled: Boolean)
    }

    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    } catch (e: RuntimeException) {
        null
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    // Kept as a field: SharedPreferences holds listeners weakly, so a local lambda could be collected.
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ENABLED) {
            val now = enabled
            listeners.forEach { it.onEnabledChanged(now) }
        }
    }

    var enabled: Boolean
        get() = read(DEFAULT_ENABLED) { getBoolean(KEY_ENABLED, DEFAULT_ENABLED) }
        set(value) = write { putBoolean(KEY_ENABLED, value) }

    var blockCount: Int
        get() = read(0) { getInt(KEY_BLOCK_COUNT, 0) }
        set(value) = write { putInt(KEY_BLOCK_COUNT, value) }

    var lastQuote: Int
        get() = read(DEFAULT_LAST_INDEX) { getInt(KEY_LAST_QUOTE, DEFAULT_LAST_INDEX) }
        set(value) = write { putInt(KEY_LAST_QUOTE, value) }

    var lastTip: Int
        get() = read(DEFAULT_LAST_INDEX) { getInt(KEY_LAST_TIP, DEFAULT_LAST_INDEX) }
        set(value) = write { putInt(KEY_LAST_TIP, value) }

    /** Package names of the apps that are sent to Marketplace when opened (common and custom). */
    var redirectPackages: Set<String>
        get() = read(emptySet()) { getStringSet(KEY_REDIRECT_PACKAGES, null)?.toSet() ?: emptySet() }
        // A copy: SharedPreferences must not be handed a set it already owns.
        set(value) = write { putStringSet(KEY_REDIRECT_PACKAGES, HashSet(value)) }

    /**
     * Apps the owner added by hand (switched on or off). Apps added by the first
     * redirect build were stored only in redirectPackages; they are folded in here.
     */
    var customPackages: Set<String>
        get() {
            val stored = read(emptySet()) { getStringSet(KEY_CUSTOM_PACKAGES, null)?.toSet() ?: emptySet() }
            val legacy = redirectPackages.filter { RedirectRules.commonAppFor(it) == null }
            if (stored.containsAll(legacy)) return stored
            val merged = stored + legacy
            customPackages = merged
            return merged
        }
        set(value) = write { putStringSet(KEY_CUSTOM_PACKAGES, HashSet(value)) }

    /** Block the Messenger app too. Off by default (owner's choice, 2026-11-16). */
    var blockMessenger: Boolean
        get() = read(false) { getBoolean(KEY_BLOCK_MESSENGER, false) }
        set(value) = write { putBoolean(KEY_BLOCK_MESSENGER, value) }

    /** True while a Lock-In Pro subscription is active (kept in sync by BillingManager). */
    var pro: Boolean
        get() = read(false) { getBoolean(KEY_PRO, false) }
        set(value) = write { putBoolean(KEY_PRO, value) }

    /** Debug builds only: Pro unlocked by hand for testing, never revoked by billing. */
    var devPro: Boolean
        get() = read(false) { getBoolean(KEY_DEV_PRO, false) }
        set(value) = write { putBoolean(KEY_DEV_PRO, value) }

    /** Id of the chosen LockTheme. */
    var themeId: String
        get() = read(DEFAULT_THEME) { getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME }
        set(value) = write { putString(KEY_THEME, value) }

    /** Current values. LockState has no lastTip field in core, so lastTip is read separately. */
    fun snapshot(): LockState = LockState(enabled, blockCount, lastQuote)

    /** Counts one blocked screen and remembers the quote and tip that were shown. */
    fun recordBlock(quoteIndex: Int, tipIndex: Int) {
        val count = blockCount + 1
        write {
            putInt(KEY_BLOCK_COUNT, count)
            putInt(KEY_LAST_QUOTE, quoteIndex)
            putInt(KEY_LAST_TIP, tipIndex)
        }
    }

    /** Counts one redirect from a chosen app as a blocked distraction. */
    fun recordRedirect() {
        val count = blockCount + 1
        write { putInt(KEY_BLOCK_COUNT, count) }
    }

    fun registerListener(listener: Listener) {
        val first = listeners.isEmpty()
        listeners.add(listener)
        if (first) {
            try {
                prefs?.registerOnSharedPreferenceChangeListener(changeListener)
            } catch (e: RuntimeException) {
                // Nothing to do: the service still reads the state on each event.
            }
        }
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            try {
                prefs?.unregisterOnSharedPreferenceChangeListener(changeListener)
            } catch (e: RuntimeException) {
                // Ignored: the listener is already gone.
            }
        }
    }

    private inline fun <T> read(default: T, crossinline block: SharedPreferences.() -> T): T {
        return try {
            prefs?.block() ?: default
        } catch (e: RuntimeException) {
            default
        }
    }

    private inline fun write(crossinline change: SharedPreferences.Editor.() -> Unit) {
        val p = prefs ?: return
        try {
            p.edit().also { it.change() }.apply()
        } catch (e: RuntimeException) {
            // Dropped on purpose; see the class comment.
        }
    }

    companion object {
        private const val FILE_NAME = "lock_state"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_BLOCK_COUNT = "block_count"
        private const val KEY_LAST_QUOTE = "last_quote"
        private const val KEY_LAST_TIP = "last_tip"
        private const val KEY_REDIRECT_PACKAGES = "redirect_packages"
        private const val KEY_CUSTOM_PACKAGES = "custom_packages"
        private const val KEY_PRO = "pro"
        private const val KEY_BLOCK_MESSENGER = "block_messenger"
        private const val KEY_DEV_PRO = "dev_pro"
        private const val KEY_THEME = "theme"
        private const val DEFAULT_THEME = "hazard"
        private const val DEFAULT_ENABLED = true
        private const val DEFAULT_LAST_INDEX = -1
    }
}
