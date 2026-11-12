package com.rakotoplayer.android

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.widget.*

/** Native full music player, with controls wired to the one LibVLC service. */
internal class MusicPlayerView(
    private val activity: MainActivity,
    private val library: Library,
    private val artwork: MediaArtLoader,
    private val action: Actions,
) : ScrollView(activity) {
    fun interface Actions {
        fun run(command: String)
    }

    private fun dp(n: Int) = Ui.dp(context, n.toFloat())

    private fun lp(w: Int, h: Int) =
        LinearLayout.LayoutParams(if (w < 0) w else dp(w), if (h < 0) h else dp(h))

    private fun line() = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }

    private fun label(s: String, size: Float, color: Int = GasyTheme.text) =
        GasyTheme.label(context, s, size, color)

    private val image = ImageView(context)
    private val title = label("RAKOTO Player", 24f)
    private val artist = label("Votre musique locale", 13f, GasyTheme.muted)
    private val play = GasyTheme.icon(context, "play", "Lire / Pause", true)
    private val favorite = GasyTheme.icon(context, "favorite", "Ajouter / retirer des favoris")
    private val shuffle = GasyTheme.icon(context, "shuffle", "Lecture aléatoire")
    private val repeat = GasyTheme.icon(context, "repeat", "Répétition")
    private val position = label("0:00", 11f, GasyTheme.muted)
    private val length = label("0:00", 11f, GasyTheme.muted)
    private val progress = SeekBar(context)
    private var currentUri = ""
    private var dragging = false
    private var service: PlaybackService? = null

    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        setBackgroundColor(GasyTheme.background)
        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(24))
            }
        addView(body)
        val header = line()
        header.addView(
            GasyTheme.icon(context, "back", "Fermer le lecteur").apply {
                setOnClickListener { action.run("back") }
            },
            lp(48, 48),
        )
        header.addView(
            label("Lecture en cours", 12f, GasyTheme.muted).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        header.addView(
            GasyTheme.icon(context, "playlist", "File de lecture").apply {
                setOnClickListener { action.run("queue") }
            },
            lp(48, 48),
        )
        body.addView(header, lp(-1, 54))
        image.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GasyTheme.shape(context, GasyTheme.card, 28f)
            clipToOutline = true
            contentDescription = "Pochette du morceau"
        }
        val artBox =
            FrameLayout(context).apply {
                setPadding(0, dp(18), 0, dp(24))
                addView(image, FrameLayout.LayoutParams(dp(270), dp(270), Gravity.CENTER))
            }
        body.addView(artBox, lp(-1, -2))
        artBox.addOnLayoutChangeListener { view, l, t, r, b, ol, ot, or, ob ->
            val size = minOf(dp(300), r - l)
            if (size > 0 && image.layoutParams.width != size) {
                image.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            }
        }
        val identity = line()
        val names =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    title.apply {
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                )
                addView(
                    artist.apply {
                        maxLines = 2
                        setPadding(0, dp(7), 0, 0)
                    }
                )
            }
        identity.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
        identity.addView(favorite, lp(48, 48))
        body.addView(identity)
        favorite.setOnClickListener {
            currentUri
                .takeIf { it.isNotEmpty() }
                ?.let { uri ->
                    library.toggleFavorite(uri)
                    refreshFavorite()
                }
        }
        progress.max = 10000
        progress.progressTintList = ColorStateList.valueOf(GasyTheme.primary)
        progress.thumbTintList = ColorStateList.valueOf(GasyTheme.primary)
        body.addView(progress, lp(-1, 46))
        progress.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {
                    dragging = true
                }

                override fun onProgressChanged(bar: SeekBar, value: Int, user: Boolean) {
                    if (user)
                        service?.let {
                            position.text = Ui.time(maxOf(0, it.player.length) * value / 10000)
                        }
                }

                override fun onStopTrackingTouch(bar: SeekBar) {
                    service?.let { it.seek(maxOf(0, it.player.length) * bar.progress / 10000) }
                    dragging = false
                }
            }
        )
        val times = line()
        times.addView(position, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(length)
        body.addView(times)
        val transport =
            line().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(14))
            }
        transport.addView(shuffle, lp(44, 48))
        shuffle.setOnClickListener { action.run("shuffle") }
        transport.addView(
            GasyTheme.icon(context, "previous", "Précédent").apply {
                setOnClickListener { action.run("previous") }
            },
            lp(52, 56),
        )
        transport.addView(play, lp(72, 72))
        play.setOnClickListener { action.run("toggle") }
        transport.addView(
            GasyTheme.icon(context, "next", "Suivant").apply {
                setOnClickListener { action.run("next") }
            },
            lp(52, 56),
        )
        transport.addView(repeat, lp(44, 48))
        repeat.setOnClickListener { action.run("repeat") }
        body.addView(transport)
        val extra = line().apply { gravity = Gravity.CENTER }
        for ((name, command) in
            listOf("Volume" to "volume", "Égaliseur" to "equalizer", "Options" to "options")) extra
            .addView(
                GasyTheme.button(context, name, false).apply {
                    textSize = 11f
                    setOnClickListener { action.run(command) }
                },
                LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) },
            )
        body.addView(extra)
    }

    fun update(value: PlaybackService) {
        service = value
        val entry = value.current ?: return
        if (currentUri != entry.uri) {
            currentUri = entry.uri
            artwork.load(image, entry, 600)
            refreshFavorite()
        }
        title.text = entry.title
        artist.text =
            listOf(entry.artist, entry.album).filter { it.isNotBlank() }.joinToString(" · ")
        play.setImageDrawable(
            Ui.Glyph(if (value.player.isPlaying) "pause" else "play", Color.WHITE)
        )
        shuffle.alpha = if (value.shuffle) 1f else .45f
        repeat.alpha = if (value.repeat > 0) 1f else .45f
        repeat.contentDescription =
            when (value.repeat) {
                1 -> "Répéter le morceau"
                2 -> "Répéter la file"
                else -> "Répétition désactivée"
            }
        tick(value)
    }

    private fun refreshFavorite() {
        favorite.setImageDrawable(
            Ui.Glyph(
                "favorite",
                if (library.favorite(currentUri)) GasyTheme.primary else GasyTheme.muted,
            )
        )
        favorite.contentDescription =
            if (library.favorite(currentUri)) "Retirer des favoris" else "Ajouter aux favoris"
    }

    fun tick(value: PlaybackService) {
        if (dragging) return
        val total = maxOf(0, value.player.length)
        val now = maxOf(0, value.player.time)
        progress.progress = if (total > 0) minOf(10000, now * 10000 / total).toInt() else 0
        progress.isEnabled = total > 0 && value.player.isSeekable
        position.text = Ui.time(now)
        length.text = Ui.time(total)
    }
}
