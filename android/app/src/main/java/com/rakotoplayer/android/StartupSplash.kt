package com.rakotoplayer.android

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

/**
 * Short native launch identity using the owner's original logo, without a tap gate or fake scan.
 */
internal class StartupSplash(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val parent = activity.findViewById<ViewGroup>(android.R.id.content)
    private val panel =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(GasyTheme.primary)
            isClickable = true
            val image =
                ImageView(activity).apply {
                    setImageResource(R.drawable.rakoto_mark)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Logo officiel RAKOTO Player"
                    background = GasyTheme.shape(activity, GasyTheme.card, 28f)
                    val p = Ui.dp(activity, 12f)
                    setPadding(p, p, p, p)
                }
            addView(image, LinearLayout.LayoutParams(Ui.dp(activity, 120f), Ui.dp(activity, 120f)))
            addView(
                GasyTheme.label(activity, "RAKOTO PLAYER", 26f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, Ui.dp(activity, 20f), 0, Ui.dp(activity, 10f))
                }
            )
            addView(
                GasyTheme.label(
                        activity,
                        "Ny hira sy ny sarimihetsika · GASY GASY",
                        12f,
                        Color.WHITE,
                    )
                    .apply { gravity = Gravity.CENTER }
            )
            addView(
                LambaView(activity),
                LinearLayout.LayoutParams(Ui.dp(activity, 140f), Ui.dp(activity, 5f)).apply {
                    topMargin = Ui.dp(activity, 24f)
                },
            )
        }

    init {
        parent.addView(panel, ViewGroup.LayoutParams(-1, -1))
        panel.setOnClickListener { close() }
        handler.postDelayed({ close() }, 700)
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        parent.removeView(panel)
    }
}
