package com.mytvsetup.launcher.audio

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.mytvsetup.launcher.tv.LgTv
import kotlinx.coroutines.launch

/**
 * The hold-Back quick menu, drawn as a plain view so the accessibility helper can show it over any app.
 * It never takes focus: [RemoteShortcuts] reads the buttons and this only shows the choice.
 */
class QuickMenuView(context: Context) : FrameLayout(context) {
    private val rows = mutableListOf<TextView>()
    private val dp = context.resources.displayMetrics.density

    init {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(28), px(22), px(28), px(18))
            background = GradientDrawable().apply { cornerRadius = px(28).toFloat(); setColor(Color.argb(235, 16, 26, 42)) }
        }
        repeat(2) {
            val row = TextView(context).apply {
                textSize = 24f; setTextColor(Color.WHITE); setPadding(px(22), px(12), px(22), px(12))
                typeface = Typeface.DEFAULT_BOLD
            }
            rows += row
            card.addView(row, LinearLayout.LayoutParams(px(360), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(6) })
        }
        card.addView(TextView(context).apply {
            text = "OK to choose · Back to close"; textSize = 15f; setTextColor(Color.argb(190, 255, 255, 255))
            setPadding(px(22), px(14), px(22), 0)
        })
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun show(muted: Boolean, selected: Int) {
        rows[0].text = if (muted) "🔊   Unmute" else "🔇   Mute"
        rows[1].text = "☾   Screen off"
        rows.forEachIndexed { i, row ->
            row.background = GradientDrawable().apply {
                cornerRadius = px(18).toFloat()
                setColor(if (i == selected) Color.WHITE else Color.argb(30, 255, 255, 255))
            }
            row.setTextColor(if (i == selected) Color.rgb(13, 21, 34) else Color.WHITE)
        }
    }

    private fun px(v: Int) = (v * dp).toInt()
}

/** What the two menu items do; [onScreenOff] differs between the launcher and the helper. */
object QuickMenuChoice {
    fun choose(context: Context, selected: Int, onScreenOff: () -> Unit) {
        if (selected == 0) Mute.toggle(context) else onScreenOff()
    }

    /** From another app: the picture goes off and the music keeps playing in that app. */
    fun screenOffBehindApps(context: Context, onFailed: () -> Unit) {
        val tv = LgTv.get(context)
        tv.scope.launch { if (!tv.screenOff {}) onFailed() }
    }
}
