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

    /** Apps on this device that open any https link (browsers, including ones not in AppRules). */
    fun browserPackages(context: Context): Set<String> = try {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        context.packageManager.queryIntentActivities(web, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    } catch (e: RuntimeException) {
        emptySet()
    }

    /** Packages that are never redirected on this device, on top of RedirectRules.canRedirect. */
    fun unsafePackages(context: Context, browsers: Set<String> = browserPackages(context)): Set<String> {
        val pm = context.packageManager
        val out = HashSet<String>(browsers)
        try {
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

    /**
     * Switched-on redirect packages that count: installed and safe on this phone.
     * Uninstalled or unsafe apps neither use up free slots nor get redirected.
     */
    fun countedEnabled(context: Context, prefs: LockPrefs, unsafe: Set<String>): Set<String> =
        prefs.redirectPackages.filter { isSafe(it, unsafe) && isInstalled(context, it) }.toSet()

    /** The packages the service actually redirects right now. */
    fun active(context: Context, prefs: LockPrefs, unsafe: Set<String> = unsafePackages(context)): Set<String> =
        RedirectRules.activeRedirects(countedEnabled(context, prefs, unsafe), prefs.pro)
}
