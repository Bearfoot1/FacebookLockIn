package app.lockin.android

import android.content.Context
import android.content.SharedPreferences
import app.lockin.core.LockState
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
        private const val DEFAULT_ENABLED = true
        private const val DEFAULT_LAST_INDEX = -1
    }
}
