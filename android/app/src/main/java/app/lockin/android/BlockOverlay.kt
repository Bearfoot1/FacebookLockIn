package app.lockin.android

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Full-screen block overlay, added with TYPE_ACCESSIBILITY_OVERLAY so no
 * SYSTEM_ALERT_WINDOW permission is needed. Touches are consumed, so the screen
 * underneath cannot be used while the overlay is up.
 *
 * @param headline short shout at the top ("NOT TODAY."), from app.lockin.core.Motivation.headlines
 * @param quote motivational quote text (from app.lockin.core.QuoteRotator)
 * @param tip short tip text (from app.lockin.core.TipRotator), shown under the quote
 * @param blockCount how many times the lock has blocked a screen, including this one
 * @param onGoBack called for the Go back button
 * @param onGoMarketplace called for the Go to Marketplace button
 */
class BlockOverlay(
    service: AccessibilityService,
    headline: String,
    quote: String,
    tip: String,
    blockCount: Int,
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
        checkNotNull(root.findViewById<TextView>(R.id.headline_text)).text = headline
        checkNotNull(root.findViewById<TextView>(R.id.tip_text)).text = tip
        checkNotNull(root.findViewById<TextView>(R.id.count_text)).text =
            themed.getString(R.string.overlay_count, blockCount)
        checkNotNull(root.findViewById<MaterialButton>(R.id.go_marketplace)).setOnClickListener { onGoMarketplace() }
        checkNotNull(root.findViewById<MaterialButton>(R.id.go_back)).setOnClickListener { onGoBack() }

        // Consume touches that reach the background so the blocked screen is unusable.
        root.setOnTouchListener { _, _ -> true }
        // The window never takes key focus (FLAG_NOT_FOCUSABLE): a focused overlay
        // swallowed the first Home press on a Galaxy S9+ and left the user stuck
        // (2026-11-06). The hardware back key now reaches the app underneath, which
        // goes back a screen; the service then re-judges what is shown.
    }

    /** Adds the overlay. Returns false if the window manager refuses it. */
    fun show(): Boolean {
        if (attached) return true
        return try {
            windowManager.addView(root, layoutParams())
            attached = true
            animateIn()
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    /** Fades the screen in and slides the message up, so the block lands with some punch. */
    private fun animateIn() {
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(180).start()
        val message = root.findViewById<View>(R.id.message_block) ?: return
        val shift = 40f * root.resources.displayMetrics.density
        message.translationY = shift
        message.animate().translationY(0f).setDuration(320)
            .setInterpolator(DecelerateInterpolator(2f)).start()
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
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    )
}
