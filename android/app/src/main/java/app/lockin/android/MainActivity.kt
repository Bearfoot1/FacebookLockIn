package app.lockin.android

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { LockPrefs(this) }

    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var serviceText: TextView
    private lateinit var openAccessibilityButton: Button
    private lateinit var openMarketplaceButton: Button
    private lateinit var turnOffButton: Button
    private lateinit var turnOnButton: Button
    private lateinit var confirmGroup: LinearLayout
    private lateinit var confirmPromptText: TextView
    private lateinit var confirmInput: EditText
    private lateinit var confirmOffButton: Button
    private lateinit var cancelOffButton: Button

    private val confirmPhrase: String by lazy { getString(R.string.confirm_phrase) }

    private fun <T : View> bind(id: Int): T = checkNotNull(findViewById<T>(id))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = bind(R.id.status_text)
        countText = bind(R.id.count_text)
        serviceText = bind(R.id.service_text)
        openAccessibilityButton = bind(R.id.open_accessibility_button)
        openMarketplaceButton = bind(R.id.open_marketplace_button)
        turnOffButton = bind(R.id.turn_off_button)
        turnOnButton = bind(R.id.turn_on_button)
        confirmGroup = bind(R.id.confirm_group)
        confirmPromptText = bind(R.id.confirm_prompt_text)
        confirmInput = bind(R.id.confirm_input)
        confirmOffButton = bind(R.id.confirm_off_button)
        cancelOffButton = bind(R.id.cancel_off_button)

        confirmPromptText.text = getString(R.string.confirm_prompt, confirmPhrase)

        openAccessibilityButton.setOnClickListener { openAccessibilitySettings() }
        openMarketplaceButton.setOnClickListener { MarketplaceLauncher.open(this) }

        turnOffButton.setOnClickListener {
            confirmGroup.visibility = View.VISIBLE
            confirmInput.requestFocus()
            updateConfirmButton()
        }
        cancelOffButton.setOnClickListener { hideConfirm() }
        confirmOffButton.setOnClickListener {
            if (isPhraseTyped()) {
                prefs.enabled = false
                hideConfirm()
                refresh()
            }
        }
        turnOnButton.setOnClickListener {
            prefs.enabled = true
            refresh()
        }

        confirmInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateConfirmButton()
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val state = prefs.snapshot()
        statusText.setText(if (state.enabled) R.string.status_on else R.string.status_off)
        countText.text = getString(R.string.blocked_count, state.blockCount)
        serviceText.setText(
            if (isAccessibilityServiceEnabled()) R.string.service_enabled else R.string.service_disabled,
        )
        turnOffButton.visibility = if (state.enabled) View.VISIBLE else View.GONE
        turnOnButton.visibility = if (state.enabled) View.GONE else View.VISIBLE
        hideConfirm()
    }

    private fun hideConfirm() {
        confirmInput.setText("")
        confirmGroup.visibility = View.GONE
        updateConfirmButton()
    }

    private fun isPhraseTyped(): Boolean = confirmInput.text?.toString()?.trim() == confirmPhrase

    private fun updateConfirmButton() {
        confirmOffButton.isEnabled = isPhraseTyped()
    }

    /** True when the lock service is in the list of enabled accessibility services. */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: SecurityException) {
            null
        }
        val target = ComponentName(this, LockAccessibilityService::class.java).flattenToString()
        return enabledServices?.split(':')?.any { it.equals(target, ignoreCase = true) } == true
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_settings_app, Toast.LENGTH_LONG).show()
        }
    }
}
