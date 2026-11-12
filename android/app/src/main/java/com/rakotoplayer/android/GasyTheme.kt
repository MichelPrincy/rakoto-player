package com.rakotoplayer.android

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.*
import android.os.Build
import android.view.*
import android.widget.*

/** Native palette and small vector views from the supplied lamba mockup. */
internal object GasyTheme {
    @JvmField var dark = false
    @JvmField var primary = Color.rgb(181, 68, 46)
    @JvmField var gold = Color.rgb(227, 176, 75)
    @JvmField var forest = Color.rgb(47, 93, 67)
    @JvmField var background = Color.rgb(246, 238, 220)
    @JvmField var card = Color.rgb(255, 250, 240)
    @JvmField var text = Color.rgb(59, 36, 24)
    @JvmField var muted = Color.rgb(138, 111, 94)
    @JvmField var line = Color.rgb(230, 216, 188)

    @JvmStatic
    fun configure(context: Context) {
        val preference =
            context
                .getSharedPreferences("rakoto", Context.MODE_PRIVATE)
                .getString("appearance", "system")
        dark =
            preference == "dark" ||
                preference == "system" &&
                    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES)
        primary = Color.parseColor(if (dark) "#D9644A" else "#B5442E")
        background = Color.parseColor(if (dark) "#1C1410" else "#F6EEDC")
        card = Color.parseColor(if (dark) "#2A1F19" else "#FFFAF0")
        text = Color.parseColor(if (dark) "#F3E7D3" else "#3B2418")
        muted = Color.parseColor(if (dark) "#B9A48F" else "#8A6F5E")
        line = Color.parseColor(if (dark) "#3A2C23" else "#E6D8BC")
        Ui.BLUE = primary
        Ui.NAVY = text
        Ui.INK = text
        Ui.MUTED = muted
        Ui.PAPER = background
        Ui.SOFT = card
    }

    @JvmStatic
    fun window(activity: Activity) {
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = card
        var flags = activity.window.decorView.systemUiVisibility
        flags =
            if (dark) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26)
            flags =
                if (dark) flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                else flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        activity.window.decorView.systemUiVisibility = flags
    }

    fun dp(context: Context, value: Float) = Ui.dp(context, value)

    fun shape(
        context: Context,
        color: Int = card,
        radius: Float = 18f,
        border: Boolean = false,
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radius).toFloat()
            if (border) setStroke(dp(context, 1f), line)
        }

    fun ripple(
        context: Context,
        color: Int = card,
        radius: Float = 18f,
        border: Boolean = false,
    ): Drawable =
        RippleDrawable(
            ColorStateList.valueOf((primary and 0x00ffffff) or 0x22000000),
            shape(context, color, radius, border),
            null,
        )

    fun label(
        context: Context,
        value: String,
        size: Float = 14f,
        color: Int = text,
        bold: Boolean = false,
    ) =
        TextView(context).apply {
            this.text = value
            setTextColor(color)
            textSize = size
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun icon(context: Context, kind: String, label: String, solid: Boolean = false) =
        ImageButton(context).apply {
            contentDescription = label
            setImageDrawable(Ui.Glyph(kind, if (solid) Color.WHITE else primary))
            setPadding(dp(context, 11f), dp(context, 11f), dp(context, 11f), dp(context, 11f))
            background = ripple(context, if (solid) primary else Color.TRANSPARENT, 50f)
        }

    fun button(context: Context, title: String, solid: Boolean = true) =
        Button(context).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(if (solid) Color.WHITE else GasyTheme.text)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = ripple(context, if (solid) primary else card, 24f, !solid)
            setPadding(dp(context, 18f), 0, dp(context, 18f), 0)
            minimumWidth = 0
            minWidth = 0
        }

    fun duration(value: Long) = if (value > 0) Ui.time(value) else "Durée inconnue"

    fun bytes(value: Long): String =
        when {
            value <= 0 -> "Taille inconnue"
            value >= 1_000_000_000 -> String.format(java.util.Locale.FRANCE, "%.1f Go", value / 1e9)
            value >= 1_000_000 -> String.format(java.util.Locale.FRANCE, "%.1f Mo", value / 1e6)
            else -> String.format(java.util.Locale.FRANCE, "%.0f Ko", value / 1e3)
        }
}

internal class LambaView(context: Context) : View(context) {
    private val paint = Paint()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = resources.displayMetrics.density
        var x = 0f
        while (x < width) {
            paint.color = GasyTheme.primary
            canvas.drawRect(x, 0f, x + 10 * unit, height.toFloat(), paint)
            paint.color = GasyTheme.gold
            canvas.drawRect(x + 10 * unit, 0f, x + 14 * unit, height.toFloat(), paint)
            paint.color = GasyTheme.forest
            canvas.drawRect(x + 14 * unit, 0f, x + 24 * unit, height.toFloat(), paint)
            paint.color = GasyTheme.gold
            canvas.drawRect(x + 24 * unit, 0f, x + 28 * unit, height.toFloat(), paint)
            x += 28 * unit
        }
    }
}

internal class CoverDrawable(
    private val name: String,
    private val video: Boolean = false,
    private val locked: Boolean = false,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letters =
        if (locked) "•"
        else
            name.trim().let {
                if (it.isEmpty()) "R"
                else
                    it.substring(0, Character.offsetByCodePoints(it, 0, 1))
                        .uppercase(java.util.Locale.FRENCH)
            }
    private var gradient: Shader? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val hue = ((name.hashCode().toLong() and 0x7fffffff) % 360).toFloat()
        val first =
            if (locked) GasyTheme.forest else Color.HSVToColor(floatArrayOf(hue, .48f, .55f))
        val second =
            if (locked) Color.rgb(28, 62, 43)
            else Color.HSVToColor(floatArrayOf((hue + 40) % 360, .45f, .32f))
        gradient =
            LinearGradient(
                0f,
                0f,
                bounds.width().toFloat(),
                bounds.height().toFloat(),
                first,
                second,
                Shader.TileMode.CLAMP,
            )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.shader = gradient
        canvas.drawRoundRect(
            b.left.toFloat(),
            b.top.toFloat(),
            b.right.toFloat(),
            b.bottom.toFloat(),
            b.width() * .17f,
            b.width() * .17f,
            paint,
        )
        paint.shader = null
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = b.height() * (if (video) .42f else .46f)
        canvas.drawText(
            if (video && !locked) "▶" else letters,
            b.exactCenterX(),
            b.exactCenterY() - (paint.ascent() + paint.descent()) / 2,
            paint,
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
    }

    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
