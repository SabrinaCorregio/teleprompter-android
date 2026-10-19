package app.teleprompter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button

object Ui {
    val BG = Color.parseColor("#0A0E13")
    val SURFACE = Color.parseColor("#1A222D")
    val FG = Color.parseColor("#EDF1F5")
    val MUTED = Color.parseColor("#92A0B2")
    val ACCENT = Color.parseColor("#7BA0FF")
    val ACCENT_INK = Color.parseColor("#06102B")
    val DANGER = Color.parseColor("#FF8080")

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun button(ctx: Context, label: String, primary: Boolean): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.textSize = 16f
        b.setTextColor(if (primary) ACCENT_INK else FG)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(ctx, 12).toFloat()
        bg.setColor(if (primary) ACCENT else SURFACE)
        b.background = bg
        b.stateListAnimator = null
        b.minimumHeight = dp(ctx, 50)
        return b
    }
}
