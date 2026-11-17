package app.lockin.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** A strip of diagonal yellow-and-black hazard stripes. Purely decorative. */
class HazardStripeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LockTheme.accent(context) }
    private val path = Path()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        if (h <= 0f) return
        val stripe = h * 1.2f
        path.reset()
        var x = -h
        while (x < width + h) {
            path.moveTo(x, h)
            path.lineTo(x + h, 0f)
            path.lineTo(x + h + stripe, 0f)
            path.lineTo(x + stripe, h)
            path.close()
            x += stripe * 2
        }
        canvas.drawPath(path, stripePaint)
    }
}
