package app.lockin.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import app.lockin.core.WebRules

/** Opens Marketplace: first in the Facebook app, then its launch intent, then the default browser. */
object MarketplaceLauncher {

    private const val FACEBOOK_PACKAGE = "com.facebook.katana"

    /** How long after open() the lock gives Facebook to reach Marketplace before blocking again. */
    const val GRACE_MS = 4000L

    @Volatile
    private var requestedAt = 0L

    /**
     * True for GRACE_MS after open(). When Facebook is already running, the Marketplace
     * link only brings its current screen (the feed, say) to the front; the lock uses
     * this window to tap the Marketplace tab itself instead of blocking straight away.
     */
    fun recentlyRequested(): Boolean =
        requestedAt != 0L && SystemClock.uptimeMillis() - requestedAt < GRACE_MS

    /** Ends the grace window once Marketplace is on screen. */
    fun arrived() {
        requestedAt = 0L
    }

    fun open(context: Context) {
        requestedAt = SystemClock.uptimeMillis()
        val marketplace = Uri.parse(WebRules.MARKETPLACE_URL)

        val inApp = Intent(Intent.ACTION_VIEW, marketplace)
            .setPackage(FACEBOOK_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(context, inApp)) return

        val launch = context.packageManager.getLaunchIntentForPackage(FACEBOOK_PACKAGE)
        if (launch != null && tryStart(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return

        tryStart(context, Intent(Intent.ACTION_VIEW, marketplace).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
