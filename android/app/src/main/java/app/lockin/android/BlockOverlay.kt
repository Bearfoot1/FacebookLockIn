package app.lockin.android

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Full-screen block overlay, added with TYPE_ACCESSIBILITY_OVERLAY so no
 * SYSTEM_ALERT_WINDOW permission is needed. Touches are consumed, so the screen
 * underneath cannot be used while the overlay is up.
 *
 * @param quote motivational quote text (from app.lockin.core.QuoteRotator)
 * @param tip short tip text (from app.lockin.core.TipRotator), shown under the quote
 * @param onGoBack called for the Go back button and the hardware back key
 * @param onGoMarketplace called for the Go to Marketplace button
 */
class BlockOverlay(
    service: AccessibilityService,
    quote: String,
    tip: String,
    private val onGoBack: () -> Unit,
    private val onGoMarketplace: () -> Unit,
) {
    private val windowManager: WindowManager =
        checkNotNull(service.getSystemService(WindowManager::class.java)) { "WindowManager unavailable" }
    private val themed = ContextThemeWrapper(service, R.style.Theme_LockIn)
    private val root: View = LayoutInflater.from(themed).inflate(R.layout.block_overlay, null)
    private var attached = false

    init {
        checkNotNull(root.findViewById<TextView>(R.id.quote_text)).text = quote
        checkNotNull(root.findViewById<TextView>(R.id.tip_text)).text = themed.getString(R.string.overlay_tip, tip)
        checkNotNull(root.findViewById<MaterialButton>(R.id.go_marketplace)).setOnClickListener { onGoMarketplace() }
        checkNotNull(root.findViewById<MaterialButton>(R.id.go_back)).setOnClickListener { onGoBack() }

        // Consume touches that reach the background so the blocked screen is unusable.
        root.setOnTouchListener { _, _ -> true }

        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onGoBack()
                true
            } else {
                false
            }
        }
    }

    /** Adds the overlay. Returns false if the window manager refuses it. */
    fun show(): Boolean {
        if (attached) return true
        return try {
            windowManager.addView(root, layoutParams())
            attached = true
            root.requestFocus()
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    /** Removes the overlay if it is attached. Safe to call more than once. */
    fun dismiss() {
        if (!attached) return
        attached = false
        try {
            windowManager.removeViewImmediate(root)
        } catch (e: RuntimeException) {
            // The window is already gone.
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )
}
