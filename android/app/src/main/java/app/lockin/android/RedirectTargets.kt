package app.lockin.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import app.lockin.core.RedirectRules

/**
 * Device-specific view of the redirect apps: which switched-on apps are installed, and
 * which packages must never be redirected on this phone even if picked (any browser,
 * since Marketplace may open in it and loop; the home screen; the phone dialer).
 */
object RedirectTargets {

    /** Packages that are never redirected on this device, on top of RedirectRules.canRedirect. */
    fun unsafePackages(context: Context): Set<String> {
        val pm = context.packageManager
        val out = HashSet<String>()
        try {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            pm.queryIntentActivities(web, PackageManager.MATCH_ALL).forEach { out += it.activityInfo.packageName }
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(home, 0).forEach { out += it.activityInfo.packageName }
        } catch (e: RuntimeException) {
            // Keep what was found.
        }
        try {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
                ?.defaultDialerPackage?.let { out += it }
        } catch (e: RuntimeException) {
            // No telephony.
        }
        // Facebook answers https links too, but it is handled by AppRules, never redirected.
        return out
    }

    /** True when [pkg] may be offered as, or acted on as, a redirect app on this device. */
    fun isSafe(pkg: String, unsafe: Set<String>): Boolean = RedirectRules.canRedirect(pkg) && pkg !in unsafe

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Switched-on redirect packages that are installed (uninstalled apps do not use up free slots). */
    fun installedEnabled(context: Context, prefs: LockPrefs): Set<String> =
        prefs.redirectPackages.filter { isInstalled(context, it) }.toSet()

    /** The packages the service actually redirects right now. */
    fun active(context: Context, prefs: LockPrefs, unsafe: Set<String> = unsafePackages(context)): Set<String> =
        RedirectRules.activeRedirects(installedEnabled(context, prefs), prefs.pro)
            .filter { isSafe(it, unsafe) }
            .toSet()
}
