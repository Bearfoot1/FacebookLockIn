package app.lockin.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.lockin.core.WebRules

/** Opens Marketplace: first in the Facebook app, then its launch intent, then the default browser. */
object MarketplaceLauncher {

    private const val FACEBOOK_PACKAGE = "com.facebook.katana"

    fun open(context: Context) {
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
