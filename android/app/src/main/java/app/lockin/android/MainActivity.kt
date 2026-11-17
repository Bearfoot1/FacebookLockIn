package app.lockin.android

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import app.lockin.core.QuoteRotator
import app.lockin.core.RedirectRules
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { LockPrefs(this) }
    private val quotes by lazy { QuoteRotator() }
    private var lastQuoteShown = -1
    private lateinit var billing: BillingManager
    /** The theme this activity was created with; a different current theme means recreate. */
    private var appliedTheme: LockTheme = LockTheme.HAZARD
    private var proDialog: AlertDialog? = null
    private var pickerDialog: AlertDialog? = null

    private lateinit var statusText: TextView
    private lateinit var statusBar: View
    private lateinit var quoteText: TextView
    private lateinit var countText: TextView
    private lateinit var serviceText: TextView
    private lateinit var serviceDot: View
    private lateinit var serviceCard: View
    private lateinit var lockCard: View
    private lateinit var lockSwitch: SwitchCompat
    private lateinit var lockSub: TextView
    private lateinit var openMarketplaceButton: Button
    private lateinit var confirmGroup: LinearLayout
    private lateinit var confirmInput: EditText
    private lateinit var confirmOffButton: Button
    private lateinit var redirectList: LinearLayout
    /** Packages never redirected on this phone (browsers, home screen, dialer); refreshed with the list. */
    private var redirectUnsafe: Set<String> = emptySet()
    private lateinit var redirectEmptyText: TextView
    private lateinit var redirectCountText: TextView
    private lateinit var themeList: LinearLayout
    private lateinit var themeNameText: TextView
    private lateinit var proCard: View
    private lateinit var proBadge: View
    private lateinit var goProButton: Button
    private lateinit var messengerSwitch: SwitchCompat
    private lateinit var messengerSub: TextView

    private val confirmPhrase: String by lazy { getString(R.string.confirm_phrase) }

    private fun <T : View> bind(id: Int): T = checkNotNull(findViewById<T>(id))

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = LockTheme.current(prefs)
        setTheme(appliedTheme.styleRes)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        billing = BillingManager(this) { runOnUiThread { onProChanged() } }

        statusText = bind(R.id.status_text)
        statusBar = bind(R.id.status_bar)
        quoteText = bind(R.id.quote_text)
        countText = bind(R.id.count_text)
        serviceText = bind(R.id.service_text)
        serviceDot = bind(R.id.service_dot)
        serviceCard = bind(R.id.service_card)
        lockCard = bind(R.id.lock_card)
        lockSwitch = bind(R.id.lock_switch)
        lockSub = bind(R.id.lock_toggle_sub)
        openMarketplaceButton = bind(R.id.open_marketplace_button)
        confirmGroup = bind(R.id.confirm_group)
        confirmInput = bind(R.id.confirm_input)
        confirmOffButton = bind(R.id.confirm_off_button)
        redirectList = bind(R.id.redirect_list)
        redirectEmptyText = bind(R.id.redirect_empty_text)
        redirectCountText = bind(R.id.redirect_count_text)
        themeList = bind(R.id.theme_list)
        themeNameText = bind(R.id.theme_name_text)
        proCard = bind(R.id.pro_card)
        proBadge = bind(R.id.pro_badge)
        goProButton = bind(R.id.go_pro_button)
        goProButton.setOnClickListener { showProDialog(getString(R.string.pro_generic_reason)) }
        proBadge.setOnClickListener { onProBadgeTapped() }
        messengerSwitch = bind(R.id.messenger_switch)
        messengerSub = bind(R.id.messenger_sub)
        bind<View>(R.id.messenger_card).setOnClickListener { messengerSwitch.toggle() }
        messengerSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.blockMessenger = checked
            showMessengerState(checked)
        }

        bind<TextView>(R.id.confirm_prompt_text).text = getString(R.string.confirm_prompt, confirmPhrase)
        bind<Button>(R.id.open_accessibility_button).setOnClickListener { openAccessibilitySettings() }
        bind<Button>(R.id.add_redirect_button).setOnClickListener { showAppPicker() }
        bind<Button>(R.id.cancel_off_button).setOnClickListener { hideConfirm() }
        openMarketplaceButton.setOnClickListener { MarketplaceLauncher.open(this) }
        quoteText.setOnClickListener { showNextQuote() }
        lockCard.setOnClickListener { lockSwitch.toggle() }
        lockSwitch.setOnCheckedChangeListener { _, checked -> onLockToggled(checked) }

        confirmOffButton.setOnClickListener {
            if (isPhraseTyped()) {
                prefs.enabled = false
                hideConfirm()
                refresh()
            }
        }
        confirmInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateConfirmButton()
        })
    }

    override fun onResume() {
        super.onResume()
        if (LockTheme.current(prefs) != appliedTheme) {
            recreate()
            return
        }
        billing.connect()
        hideConfirm()
        showNextQuote()
        refresh()
    }

    override fun onDestroy() {
        proDialog?.dismiss()
        pickerDialog?.dismiss()
        billing.disconnect()
        super.onDestroy()
    }

    /** Pro turned on or off (purchase, lapse, or the debug switch). */
    private fun onProChanged() {
        if (isFinishing || isDestroyed) return
        if (LockTheme.current(prefs) != appliedTheme) recreate() else refresh()
    }

    /**
     * Turning the lock on is instant. Turning it off flips the toggle straight back and
     * opens the confirm card; only the typed phrase really turns it off.
     */
    private fun onLockToggled(checked: Boolean) {
        if (checked) {
            prefs.enabled = true
            hideConfirm()
            refresh()
        } else if (prefs.enabled) {
            setLockSwitch(true)
            showConfirm()
        }
    }

    private fun setLockSwitch(checked: Boolean) {
        lockSwitch.setOnCheckedChangeListener(null)
        lockSwitch.isChecked = checked
        lockSwitch.setOnCheckedChangeListener { _, c -> onLockToggled(c) }
    }

    private fun showNextQuote() {
        val (index, quote) = quotes.next(lastQuoteShown)
        lastQuoteShown = index
        quoteText.text = quote
    }

    private fun refresh() {
        val state = prefs.snapshot()
        val serviceOn = isAccessibilityServiceEnabled()
        val locked = state.enabled && serviceOn
        // "LOCKED IN" only when the service is actually running; switched on without it
        // nothing is blocked, so say so.
        statusText.setText(
            when {
                locked -> R.string.status_on
                state.enabled -> R.string.status_unprotected
                else -> R.string.status_off
            },
        )
        statusText.setTextColor(getColor(if (locked) R.color.text_primary else R.color.danger))
        statusBar.backgroundTintList = ColorStateList.valueOf(if (locked) LockTheme.accent(this) else getColor(R.color.danger))
        countText.text = state.blockCount.toString()
        serviceText.setText(if (serviceOn) R.string.service_enabled else R.string.service_off_short)
        serviceDot.backgroundTintList = ColorStateList.valueOf(getColor(if (serviceOn) R.color.ok else R.color.danger))
        serviceCard.visibility = if (serviceOn) View.GONE else View.VISIBLE
        openMarketplaceButton.visibility = if (serviceOn) View.VISIBLE else View.GONE
        setLockSwitch(state.enabled)
        lockSub.setText(if (state.enabled) R.string.lock_toggle_sub_on else R.string.lock_toggle_sub_off)
        lockCard.setBackgroundResource(
            if (state.enabled) R.drawable.card_active_background else R.drawable.card_danger_background,
        )
        messengerSwitch.isChecked = prefs.blockMessenger
        showMessengerState(prefs.blockMessenger)
        buildRedirectRows()
        buildThemeSwatches()
        val pro = prefs.pro
        proCard.visibility = if (pro) View.GONE else View.VISIBLE
        proBadge.visibility = if (pro) View.VISIBLE else View.GONE
        goProButton.text = getString(R.string.pro_button, billing.price ?: getString(R.string.pro_price_fallback))
    }

    private fun showMessengerState(blocked: Boolean) {
        messengerSub.setText(if (blocked) R.string.messenger_sub_on else R.string.messenger_sub_off)
    }

    /** One square swatch per theme; Pro themes show a lock without Pro. */
    private fun buildThemeSwatches() {
        themeList.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (64 * density).toInt()
        val gap = (12 * density).toInt()
        val pro = prefs.pro
        val current = LockTheme.current(prefs)
        themeNameText.text = current.label
        for (theme in LockTheme.entries) {
            val locked = theme.premium && !pro
            val swatch = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
                background = GradientDrawable().apply {
                    setColor(getColor(theme.colorRes))
                    if (theme == current) setStroke((4 * density).toInt(), getColor(R.color.text_primary))
                }
                contentDescription = theme.label
                setOnClickListener { onThemePicked(theme) }
            }
            if (theme != current) {
                // Dim the ones not in use so the current theme pops; locked ones more.
                swatch.addView(
                    View(this).apply {
                        setBackgroundColor(if (locked) 0x99000000.toInt() else 0x33000000)
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    },
                )
            }
            if (locked) {
                swatch.addView(
                    TextView(this).apply {
                        text = "🔒"
                        textSize = 20f
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.CENTER,
                        )
                    },
                )
            }
            themeList.addView(swatch)
        }
    }

    private fun onThemePicked(theme: LockTheme) {
        if (theme.premium && !prefs.pro) {
            showProDialog(getString(R.string.pro_theme_reason, theme.label))
            return
        }
        if (theme == appliedTheme) return
        prefs.themeId = theme.id
        recreate()
    }

    /** Release: opens Play's subscription page (cancel, change card). Debug: the upgrade screen with the test switch. */
    private fun onProBadgeTapped() {
        if (BuildConfig.DEBUG) {
            showProDialog(getString(R.string.pro_generic_reason))
            return
        }
        val url = "https://play.google.com/store/account/subscriptions?sku=" +
            BillingManager.PRO_PRODUCT_ID + "&package=" + packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_settings_app, Toast.LENGTH_LONG).show()
        }
    }

    /** The upgrade screen. [reason] says what the owner just tried to do. */
    private fun showProDialog(reason: String) {
        proDialog?.dismiss()
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pro, null)
        val dialog = MaterialAlertDialogBuilder(this).setView(view).create()
        view.findViewById<TextView>(R.id.pro_reason_text).text = reason
        view.findViewById<Button>(R.id.pro_buy_button).apply {
            text = getString(R.string.pro_button, billing.price ?: getString(R.string.pro_price_fallback))
            setOnClickListener {
                if (billing.buy(this@MainActivity)) {
                    dialog.dismiss()
                } else {
                    Toast.makeText(this@MainActivity, R.string.pro_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
        view.findViewById<Button>(R.id.pro_close_button).setOnClickListener { dialog.dismiss() }
        if (BuildConfig.DEBUG) {
            view.findViewById<Button>(R.id.pro_dev_button).apply {
                visibility = View.VISIBLE
                setText(if (prefs.pro) R.string.pro_dev_lock else R.string.pro_dev_unlock)
                setOnClickListener {
                    val unlock = !prefs.pro
                    prefs.devPro = unlock
                    prefs.pro = unlock
                    dialog.dismiss()
                    onProChanged()
                }
            }
        }
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply { setColor(getColor(R.color.ink)) })
        proDialog = dialog
        dialog.show()
    }

    private fun showConfirm() {
        confirmGroup.visibility = View.VISIBLE
        confirmInput.requestFocus()
        val scroll = findViewById<ScrollView>(R.id.main_scroll)
        scroll?.post { scroll.smoothScrollTo(0, confirmGroup.top - 40) }
        updateConfirmButton()
    }

    private fun hideConfirm() {
        confirmInput.setText("")
        confirmGroup.visibility = View.GONE
        updateConfirmButton()
    }

    private fun isPhraseTyped(): Boolean = confirmInput.text?.toString()?.trim() == confirmPhrase

    private fun updateConfirmButton() {
        confirmOffButton.isEnabled = isPhraseTyped()
        confirmOffButton.setTextColor(getColor(if (confirmOffButton.isEnabled) R.color.danger else R.color.surface_line))
    }

    /** An installed app the user can pick. */
    private class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

    /**
     * Fills the redirect list: a toggle for each installed common app, then a toggle
     * and remove button for each custom app. Rebuilt on every refresh, so apps installed
     * or removed while the screen was away show up.
     */
    private fun buildRedirectRows() {
        redirectUnsafe = RedirectTargets.unsafePackages(this)
        // Computed once per build; every toggle rebuilds the list, because one app's
        // switch can move another between Redirecting and Paused.
        val active = RedirectTargets.active(this, prefs, redirectUnsafe)
        redirectList.removeAllViews()
        val enabled = prefs.redirectPackages

        val common = RedirectRules.commonApps.mapNotNull { app ->
            val installed = app.packages.firstOrNull { RedirectTargets.isInstalled(this, it) } ?: return@mapNotNull null
            app to installed
        }
        for ((app, installed) in common) {
            val on = app.packages.any { it in enabled }
            addRow(active,installed, app.packages.toSet(), app.name, on, removable = false) { checked ->
                val now = prefs.redirectPackages
                prefs.redirectPackages = if (checked) now + app.packages else now - app.packages.toSet()
            }
        }

        val custom = prefs.customPackages
            .filter { RedirectRules.commonAppFor(it) == null }
            .map { it to (labelOf(it) ?: it) }
            .sortedBy { it.second.lowercase() }
        for ((pkg, label) in custom) {
            val on = pkg in enabled
            addRow(active, pkg, setOf(pkg), label, on, removable = true) { checked ->
                val now = prefs.redirectPackages
                prefs.redirectPackages = if (checked) now + pkg else now - pkg
            }
        }

        // No divider under the last row.
        if (redirectList.childCount > 0) {
            redirectList.getChildAt(redirectList.childCount - 1).findViewById<View>(R.id.app_divider).visibility = View.GONE
        }
        // Same count the free limit uses: installed, safe apps only.
        redirectCountText.text = redirectCountLabel(
            RedirectRules.redirectAppCount(RedirectTargets.countedEnabled(this, prefs, redirectUnsafe)),
        )
        redirectEmptyText.visibility = if (common.isEmpty() && custom.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addRow(
        activeRedirects: Set<String>,
        pkg: String,
        packages: Set<String>,
        label: String,
        on: Boolean,
        removable: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val item = LayoutInflater.from(this).inflate(R.layout.item_redirect_app, redirectList, false)
        item.findViewById<TextView>(R.id.app_name).text = label
        item.findViewById<ImageView>(R.id.app_icon).setImageDrawable(iconOf(pkg))
        val state = item.findViewById<TextView>(R.id.app_state)
        val switch = item.findViewById<SwitchCompat>(R.id.app_switch)
        // Show what the service really does: a switched-on app can be ignored when it is
        // uninstalled, unsafe on this phone, or past the free limit after Pro lapsed.
        val active = on && packages.any { it in activeRedirects }
        when {
            !on -> state.setText(R.string.redirect_off)
            active -> state.setText(R.string.redirect_on)
            packages.none { RedirectTargets.isInstalled(this, it) } -> state.setText(R.string.redirect_not_installed)
            packages.none { RedirectTargets.isSafe(it, redirectUnsafe) } -> state.setText(R.string.redirect_unsafe)
            else -> state.setText(R.string.redirect_paused)
        }
        state.setTextColor(
            when {
                active -> LockTheme.accent(this)
                on -> getColor(R.color.danger)
                else -> getColor(R.color.text_muted)
            },
        )
        switch.isChecked = on
        switch.contentDescription = label
        switch.setOnCheckedChangeListener(object : android.widget.CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(button: android.widget.CompoundButton, checked: Boolean) {
                if (checked && !RedirectRules.canEnableAnother(RedirectTargets.countedEnabled(this@MainActivity, prefs, redirectUnsafe), prefs.pro)) {
                    // Free limit reached: snap back and offer Pro.
                    button.setOnCheckedChangeListener(null)
                    button.isChecked = false
                    button.setOnCheckedChangeListener(this)
                    showProDialog(getString(R.string.pro_limit_reason))
                    return
                }
                onChange(checked)
                buildRedirectRows()
            }
        })
        item.findViewById<View>(R.id.app_row).setOnClickListener { switch.toggle() }
        if (removable) {
            item.findViewById<TextView>(R.id.app_remove).apply {
                visibility = View.VISIBLE
                contentDescription = getString(R.string.redirect_remove) + " " + label
                setOnClickListener {
                    prefs.redirectPackages = prefs.redirectPackages - pkg
                    prefs.customPackages = prefs.customPackages - pkg
                    buildRedirectRows()
                }
            }
        }
        redirectList.addView(item)
    }

    /** "3 on" with Pro, "3/5 on" without. */
    private fun redirectCountLabel(count: Int): String =
        if (prefs.pro) getString(R.string.redirect_count, count)
        else getString(R.string.redirect_count_free, count, RedirectRules.FREE_REDIRECT_LIMIT)

    /** Lists launchable apps that are not already listed and adds the one picked, switched on. */
    private fun showAppPicker() {
        val listed = prefs.customPackages
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { pkg ->
                pkg != packageName &&
                    // Never browsers (Marketplace may open in one and loop), the home screen or the dialer.
                    RedirectTargets.isSafe(pkg, redirectUnsafe) &&
                    pkg !in listed &&
                    // Common apps already have their own toggle.
                    RedirectRules.commonAppFor(pkg) == null
            }
            .map { AppEntry(it, labelOf(it) ?: it, iconOf(it)) }
            .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.redirect_pick_none, Toast.LENGTH_SHORT).show()
            return
        }
        val adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = convertView ?: LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_redirect_app, parent, false).also {
                        it.findViewById<View>(R.id.app_switch).visibility = View.GONE
                        it.findViewById<View>(R.id.app_state).visibility = View.GONE
                        it.findViewById<View>(R.id.app_divider).visibility = View.GONE
                        // The dialog list handles taps; the row must not swallow them.
                        it.findViewById<View>(R.id.app_row).apply {
                            isClickable = false
                            background = null
                        }
                        val pad = (24 * resources.displayMetrics.density).toInt()
                        it.setPadding(pad, 0, pad, 0)
                    }
                val app = apps[position]
                item.findViewById<TextView>(R.id.app_name).text = app.label
                item.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.icon)
                return item
            }
        }
        pickerDialog?.dismiss()
        pickerDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.redirect_pick_title)
            .setAdapter(adapter) { _, which ->
                val pkg = apps[which].packageName
                val fits = RedirectRules.canEnableAnother(RedirectTargets.countedEnabled(this, prefs, redirectUnsafe), prefs.pro)
                prefs.customPackages = prefs.customPackages + pkg
                if (fits) prefs.redirectPackages = prefs.redirectPackages + pkg
                buildRedirectRows()
                if (!fits) showProDialog(getString(R.string.pro_limit_reason))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun labelOf(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun iconOf(pkg: String): Drawable? = try {
        packageManager.getApplicationIcon(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** True when the lock service is in the list of enabled accessibility services. */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: SecurityException) {
            null
        }
        // The system may store the short form (app.lockin.android/.LockAccessibilityService),
        // so compare parsed component names, not strings.
        val target = ComponentName(this, LockAccessibilityService::class.java)
        return enabledServices?.split(':')?.any { ComponentName.unflattenFromString(it.trim()) == target } == true
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_settings_app, Toast.LENGTH_LONG).show()
        }
    }
}
