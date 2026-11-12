package com.rakotoplayer.android

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import java.util.Arrays

/**
 * Kotlin native views; the existing Java scanner, catalogue, player and vault remain authoritative.
 */
internal class GasySkin(private val screen: BrowserScreen) {
    private val c = screen.activity
    private val prefs = screen.library.preferences
    private val art = MediaArtLoader(c)
    private val recent = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private var grid = prefs.getBoolean("video_grid", false)
    private var sheet: AlertDialog? = null
    private var pinPanel: LinearLayout? = null
    @Volatile private var pinEpoch = 0
    private var pinNext: Runnable? = null
    private var replacePin = false
    private var pinBusy = false
    private var code = StringBuilder()
    private var confirmation: CharArray? = null

    private fun dp(n: Int) = Ui.dp(c, n.toFloat())

    private fun col() = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

    private fun row() = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }

    private fun lp(w: Int, h: Int) =
        LinearLayout.LayoutParams(if (w < 0) w else dp(w), if (h < 0) h else dp(h))

    private fun label(
        s: String,
        size: Float = 14f,
        color: Int = GasyTheme.text,
        bold: Boolean = false,
    ) = GasyTheme.label(c, s, size, color, bold)

    private fun button(s: String, action: () -> Unit) =
        GasyTheme.button(c, s).apply { setOnClickListener { action() } }

    fun attach() {
        screen.setBackgroundColor(GasyTheme.background)
        screen.setPadding(dp(20), 0, dp(20), 0)
        screen.heading.textSize = 26f
        screen.subtitle.textSize = 11f
        screen.search.background = GasyTheme.shape(c, GasyTheme.card, 24f, true)
        screen.search.setHintTextColor(GasyTheme.muted)
        screen.emptyAction.background = GasyTheme.ripple(c, GasyTheme.primary, 24f)
        screen.list.isVerticalScrollBarEnabled = false
        screen.list.setPadding(0, 0, 0, dp(8))
        screen.addView(recent, 2, lp(-1, -2))
        val header = screen.getChildAt(0) as LinearLayout
        // Keep the user's original logo; replace the old overflow by a theme shortcut.
        val last = header.getChildAt(header.childCount - 1) as ImageButton
        last.setImageDrawable(Ui.Glyph("settings", GasyTheme.primary))
        last.contentDescription = "Choisir le thème"
        last.background = GasyTheme.ripple(c, Color.TRANSPARENT, 24f)
        last.setOnClickListener { appearance() }
        screen.add.setImageDrawable(Ui.Glyph("plus", GasyTheme.primary))
        screen.add.background = GasyTheme.ripple(c, Color.TRANSPARENT, 24f)
        screen.add.setOnClickListener {
            if (screen.tab == 2) screen.withVault { importPicker() }
            else screen.actions.requestScan()
        }
        val more = GasyTheme.icon(c, "more", "Menu de la bibliothèque")
        header.addView(more, lp(40, 44))
        more.setOnClickListener { menu() }
        val tools = screen.pathLabel.parent as LinearLayout
        val sort = tools.getChildAt(1) as Button
        sort.background = GasyTheme.ripple(c, GasyTheme.card, 20f, true)
        sort.setTextColor(GasyTheme.text)
        val layout = GasyTheme.icon(c, "video", "Basculer liste / grille")
        tools.addView(layout, lp(42, 42))
        layout.tag = "layout"
        layout.setOnClickListener {
            grid = !grid
            prefs.edit().putBoolean("video_grid", grid).apply()
            screen.adapter.notifyDataSetChanged()
            pageChanged()
        }
        screen.navigation.elevation = 0f
        screen.navigation.background = GasyTheme.shape(c, GasyTheme.card, 0f)
        pageChanged()
    }

    fun pageChanged() {
        val titles = arrayOf("Musique", "Vidéos", "Coffre privé", "Réglages")
        screen.heading.text = titles[screen.tab]
        screen.add.setImageDrawable(
            Ui.Glyph(if (screen.tab == 2) "plus" else "scan", GasyTheme.primary)
        )
        screen.add.contentDescription =
            if (screen.tab == 2) "Importer dans le coffre" else "Scanner les médias"
        screen.search.visibility =
            if (screen.tab == 3 || screen.tab == 2 && !screen.vault.unlocked()) View.GONE
            else View.VISIBLE
        (screen.chips.parent as View).visibility = screen.search.visibility
        (screen.pathLabel.parent as View).visibility = screen.search.visibility
        screen.navigation.setPadding(0, dp(6), 0, dp(5))
        val names = arrayOf("Musique", "Vidéos", "Coffre", "Réglages")
        val icons = arrayOf("music", "video", "lock", "settings")
        for (i in 0..3) {
            val item = screen.navigation.getChildAt(i) as LinearLayout
            item.contentDescription = names[i]
            item.background = GasyTheme.ripple(c, Color.TRANSPARENT, 14f)
            val color = if (i == screen.tab) GasyTheme.primary else GasyTheme.muted
            (item.getChildAt(0) as ImageView).setImageDrawable(Ui.Glyph(icons[i], color))
            (item.getChildAt(1) as TextView).apply {
                text = names[i]
                setTextColor(color)
                textSize = 10f
            }
        }
        val labels =
            mapOf(
                "all" to if (screen.tab == 0) "Chansons" else "Toutes",
                "albums" to "Albums",
                "artists" to "Artistes",
                "folders" to "Dossiers",
                "playlists" to "Playlists",
                "favorites" to "Favoris",
                "recent" to "Récentes",
                "unread" to "Non lues",
                "import" to "Importer",
                "lock" to "Verrouiller",
            )
        for (i in 0 until screen.chips.childCount) {
            val chip = screen.chips.getChildAt(i) as? Button ?: continue
            val key = chip.tag as? String ?: continue
            chip.text = labels[key] ?: chip.text
            val selected = key == screen.mode
            chip.background =
                GasyTheme.ripple(
                    c,
                    if (selected) GasyTheme.primary else GasyTheme.card,
                    22f,
                    !selected,
                )
            chip.setTextColor(if (selected) Color.WHITE else GasyTheme.text)
            chip.textSize = 12f
        }
        val tools = screen.pathLabel.parent as ViewGroup
        tools.findViewWithTag<View>("layout")?.apply {
            visibility = if (screen.tab == 1) View.VISIBLE else View.GONE
            contentDescription = if (grid) "Afficher en liste" else "Afficher en grille"
        }
        if (screen.tab == 0 && screen.groupValue != null)
            screen.pathLabel.text = "‹ ${screen.groupValue.ifEmpty { "Sans information" }}"
        recent.visibility =
            if (screen.tab == 0 && screen.mode == "all" && screen.query.isEmpty()) View.VISIBLE
            else View.GONE
        if (screen.tab == 2) {
            screen.subtitle.text = "Chiffré sur cet appareil · originaux conservés"
            recent.visibility = View.GONE
        }
    }

    fun rowsChanged() {
        pageChanged()
        if (screen.tab < 2)
            screen.subtitle.text =
                "${screen.rows.size} ${if(screen.rows.any{it.media==null})"éléments" else if(screen.tab==0)"titres" else "vidéos"} dans cette vue"
        if (screen.tab == 0 && screen.mode == "all" && screen.query.isEmpty()) {
            recent.removeAllViews()
            val entries =
                screen.rows
                    .filter { it.media != null }
                    .sortedByDescending { it.media.addedAt }
                    .take(3)
            if (entries.isNotEmpty()) {
                recent.addView(label("Récemment ajoutés", 12f, GasyTheme.muted, true), lp(-1, 28))
                val scroll = HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false }
                val strip = row()
                for (entry in entries) {
                    val b =
                        GasyTheme.button(c, entry.title, false).apply {
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            textSize = 11f
                            setOnClickListener { screen.open(entry) }
                        }
                    strip.addView(b, lp(150, 38).apply { rightMargin = dp(7) })
                }
                scroll.addView(strip)
                recent.addView(scroll, lp(-1, 42))
            }
        }
    }

    fun isGrid() = screen.tab == 1 && grid

    fun rowCount() = if (isGrid()) (screen.rows.size + 1) / 2 else screen.rows.size

    fun rowView(position: Int, recycled: View?, parent: ViewGroup): View {
        if (isGrid()) {
            val outer =
                (recycled as? LinearLayout)?.takeIf { it.tag == "grid" }
                    ?: row().apply {
                        tag = "grid"
                        setPadding(0, dp(5), 0, dp(5))
                    }
            outer.removeAllViews()
            for (index in position * 2..position * 2 + 1) {
                val cell = if (index < screen.rows.size) videoCell(screen.rows[index]) else View(c)
                outer.addView(
                    cell,
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index % 2 == 0) rightMargin = dp(6) else leftMargin = dp(6)
                    },
                )
            }
            return outer
        }
        return ((recycled as? NativeRow) ?: NativeRow()).apply { bind(screen.rows[position]) }
    }

    private inner class NativeRow : LinearLayout(c) {
        private val image =
            ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GasyTheme.shape(c, GasyTheme.card, 10f)
            }
        private val title =
            label("", 14f, GasyTheme.text, true).apply { ellipsize = TextUtils.TruncateAt.END }
        private val detail =
            label("", 11f, GasyTheme.muted).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            }
        private val time = label("", 10f, GasyTheme.muted)
        private val more = GasyTheme.icon(c, "more", "Options").apply { isFocusable = false }
        private var item: BrowserScreen.Row? = null

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            background = GasyTheme.ripple(c, Color.TRANSPARENT, 14f)
            addView(image, lp(48, 48))
            val lines =
                col().apply {
                    setPadding(dp(12), 0, dp(5), 0)
                    addView(title)
                    addView(detail)
                }
            addView(lines, LayoutParams(0, -2, 1f))
            addView(time, lp(38, -2))
            addView(more, lp(40, 48))
            more.setOnClickListener { item?.let { rowMenu(it) } }
        }

        fun bind(value: BrowserScreen.Row) {
            item = value
            val media = value.media
            val video = media?.video == true
            minimumHeight = dp(if (video) 88 else 70)
            image.layoutParams = lp(if (video) 106 else 48, if (video) 64 else 48)
            title.text = value.title
            title.maxLines = if (video) 2 else 1
            if (media != null) art.load(image, media)
            else {
                image.tag = null
                image.setImageDrawable(Ui.Glyph(value.icon, GasyTheme.primary))
            }
            detail.text =
                if (media != null && !video)
                    listOf(media.artist, media.album).filter { it.isNotBlank() }.joinToString(" · ")
                else if (media != null)
                    "${if(media.width>0)"${media.width} × ${media.height} · " else ""}${GasyTheme.bytes(media.size)} · ${Ui.time(media.durationMs)}"
                else value.detail
            time.visibility = if (media != null && !video) View.VISIBLE else View.GONE
            time.text =
                if (media != null && media.durationMs > 0) Ui.time(media.durationMs) else "—"
            more.contentDescription = "Options de ${value.title}"
        }
    }

    private fun videoCell(item: BrowserScreen.Row): View {
        val box =
            col().apply {
                background = GasyTheme.ripple(c, GasyTheme.card, 16f, true)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { screen.open(item) }
                setOnLongClickListener {
                    rowMenu(item)
                    true
                }
            }
        val frame = FrameLayout(c)
        val image =
            ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GasyTheme.shape(c, GasyTheme.card, 12f)
            }
        frame.addView(image, FrameLayout.LayoutParams(-1, dp(100)))
        item.media?.let {
            art.load(image, it, 240)
            val badge =
                label(Ui.time(it.durationMs), 10f, Color.WHITE).apply {
                    setPadding(dp(6), dp(3), dp(6), dp(3))
                    background = GasyTheme.shape(c, 0xaa000000.toInt(), 5f)
                }
            frame.addView(
                badge,
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                },
            )
        } ?: image.setImageDrawable(Ui.Glyph(item.icon, GasyTheme.primary))
        box.addView(frame)
        box.addView(
            label(item.title, 12f, GasyTheme.text, true).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(7), 0, 0)
            },
            lp(-1, 40),
        )
        box.addView(
            label(item.media?.let { GasyTheme.bytes(it.size) } ?: item.detail, 10f, GasyTheme.muted)
                .apply { maxLines = 1 }
        )
        val options =
            GasyTheme.button(c, "Options", false).apply {
                textSize = 11f
                setOnClickListener { rowMenu(item) }
            }
        box.addView(options, lp(-1, 36))
        return box
    }

    private fun showPanel(title: String, content: View, secure: Boolean = false): AlertDialog {
        sheet?.dismiss()
        val container =
            col().apply {
                setPadding(dp(20), dp(16), dp(20), dp(22))
                background = GasyTheme.shape(c, GasyTheme.card, 26f)
                addView(label(title, 20f, GasyTheme.text, true), lp(-1, 40))
                addView(content)
            }
        val scroll = ScrollView(c).apply { addView(container) }
        val dialog = AlertDialog.Builder(c).setView(scroll).create()
        if (secure) {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            screen.privateDialogs.add(dialog)
        }
        dialog.setOnDismissListener {
            screen.privateDialogs.remove(dialog)
            if (sheet === dialog) sheet = null
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(-1, -2)
            attributes = attributes.apply { dimAmount = .4f }
        }
        sheet = dialog
        return dialog
    }

    private fun choices(
        title: String,
        items: List<Pair<String, () -> Unit>>,
        secure: Boolean = false,
    ) {
        val panel = col()
        for ((name, action) in items) panel.addView(
            GasyTheme.button(c, name, false).apply {
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    sheet?.dismiss()
                    action()
                }
            },
            lp(-1, 50).apply { bottomMargin = dp(5) },
        )
        showPanel(title, panel, secure)
    }

    fun menu() {
        if (screen.tab == 2) {
            choices(
                "Coffre privé",
                listOf(
                    "Importer des vidéos" to { screen.withVault { importPicker() } },
                    "Trier" to { sort() },
                    "Modifier le code" to { screen.changePin() },
                    "Verrouiller" to
                        {
                            screen.lockVault()
                            screen.reload()
                        },
                ),
                true,
            )
            return
        }
        choices(
            "Bibliothèque",
            listOf(
                "Scanner les médias" to { screen.actions.requestScan() },
                "Ouvrir des fichiers" to { screen.actions.pickMedia(false) },
                "Ajouter un dossier" to { screen.actions.pickFolder() },
                "Créer une playlist" to { screen.namePlaylist(null) },
                "Trier" to { sort() },
                "Réglages" to { screen.select(3) },
            ),
        )
    }

    private fun importPicker() {
        val generation = screen.vault.generation()
        screen.data.execute {
            val videos = screen.library.entries(true, false, null).sortedByDescending { it.addedAt }
            screen.handler.post {
                if (
                    screen.destroyed ||
                        !screen.active ||
                        !screen.vault.unlocked() ||
                        generation != screen.vault.generation()
                )
                    return@post
                val panel = col()
                val chosen = linkedSetOf<String>()
                val search =
                    EditText(c).apply {
                        hint = "Rechercher une vidéo"
                        isSingleLine = true
                        setTextColor(GasyTheme.text)
                        setHintTextColor(GasyTheme.muted)
                    }
                panel.addView(search, lp(-1, 48))
                val list = ListView(c).apply { divider = null }
                var minimum = 0L
                val filters = row()
                val confirm = button("Copier 0 vidéo") {}
                var filtered = videos
                val adapter =
                    object : BaseAdapter() {
                        override fun getCount() = filtered.size

                        override fun getItem(position: Int) = filtered[position]

                        override fun getItemId(position: Int) = position.toLong()

                        override fun getView(
                            position: Int,
                            recycled: View?,
                            parent: ViewGroup,
                        ): View {
                            val video = filtered[position]
                            return ((recycled as? CheckBox)
                                    ?: CheckBox(c).apply {
                                        textSize = 12f
                                        setTextColor(GasyTheme.text)
                                        setPadding(dp(8), dp(10), dp(8), dp(10))
                                    })
                                .apply {
                                    setOnCheckedChangeListener(null)
                                    text =
                                        "${video.title}\n${Ui.time(video.durationMs)} · ${GasyTheme.bytes(video.size)}"
                                    isChecked = chosen.contains(video.uri)
                                    setOnCheckedChangeListener { _, checked ->
                                        if (checked) chosen.add(video.uri)
                                        else chosen.remove(video.uri)
                                        confirm.text = "Copier ${chosen.size} vidéo(s)"
                                    }
                                }
                        }
                    }
                list.adapter = adapter
                fun populate() {
                    filtered = videos.filter {
                        it.durationMs >= minimum && it.title.contains(search.text.toString(), true)
                    }
                    adapter.notifyDataSetChanged()
                }
                for ((name, duration) in
                    listOf("Toutes" to 0L, "+ 1 min" to 60_000L, "+ 10 min" to 600_000L)) filters
                    .addView(
                        GasyTheme.button(c, name, false).apply {
                            textSize = 11f
                            setOnClickListener {
                                minimum = duration
                                populate()
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(40), 1f),
                    )
                panel.addView(filters)
                panel.addView(list, lp(-1, 260))
                search.addTextChangedListener(
                    object : android.text.TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) {}

                        override fun afterTextChanged(s: android.text.Editable?) {
                            populate()
                        }

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) {}
                    }
                )
                confirm.setOnClickListener {
                    if (chosen.isNotEmpty()) {
                        val uris = chosen.map { android.net.Uri.parse(it) }
                        sheet?.dismiss()
                        screen.imported(uris, true)
                    }
                }
                panel.addView(confirm, lp(-1, 48).apply { topMargin = dp(10) })
                panel.addView(
                    GasyTheme.button(c, "Choisir un autre fichier…", false).apply {
                        setOnClickListener {
                            sheet?.dismiss()
                            screen.actions.pickMedia(true)
                        }
                    },
                    lp(-1, 44).apply { topMargin = dp(6) },
                )
                panel.addView(
                    label(
                            "Les originaux sont conservés. Les copies sont chiffrées dans le coffre.",
                            11f,
                            GasyTheme.muted,
                        )
                        .apply { setPadding(0, dp(10), 0, 0) }
                )
                populate()
                showPanel("Ajouter au coffre", panel, true)
            }
        }
    }

    fun sort() {
        val panel = col()
        val labels = arrayOf("Nom", "Durée", "Taille", "Date d’ajout")
        val group = RadioGroup(c)
        labels.forEachIndexed { i, s ->
            group.addView(
                RadioButton(c).apply {
                    id = i + 100
                    text = s
                    isChecked = screen.sort == i
                    buttonTintList = android.content.res.ColorStateList.valueOf(GasyTheme.primary)
                    setTextColor(GasyTheme.text)
                },
                lp(-1, 46),
            )
        }
        panel.addView(group)
        val ascending =
            Switch(c).apply {
                text = "Ordre croissant"
                isChecked = screen.ascending
                setTextColor(GasyTheme.text)
            }
        panel.addView(ascending, lp(-1, 48))
        panel.addView(
            button("Appliquer") {
                screen.sort = group.checkedRadioButtonId - 100
                screen.ascending = ascending.isChecked
                prefs
                    .edit()
                    .putInt("sort:${screen.tab}", screen.sort)
                    .putBoolean("ascending:${screen.tab}", screen.ascending)
                    .apply()
                sheet?.dismiss()
                screen.reload()
            },
            lp(-1, 48),
        )
        showPanel("Trier par", panel, screen.tab == 2)
    }

    fun rowMenu(item: BrowserScreen.Row) {
        val media = item.media
        if (media == null) {
            val options = mutableListOf("Ouvrir" to { screen.open(item) })
            item.playlist?.let { playlist ->
                options.add("Renommer" to { screen.namePlaylist(playlist) })
                options.add(
                    "Supprimer la playlist" to
                        {
                            AlertDialog.Builder(c)
                                .setMessage(
                                    "Supprimer cette playlist ? Les fichiers restent conservés."
                                )
                                .setNegativeButton("Annuler", null)
                                .setPositiveButton("Supprimer") { _, _ ->
                                    screen.data.execute {
                                        screen.library.deletePlaylist(playlist.id)
                                        screen.handler.post { screen.reload() }
                                    }
                                }
                                .show()
                            Unit
                        }
                )
            }
            choices(item.title, options)
            return
        }
        if (media.isPrivate) {
            choices(
                "Vidéo privée",
                listOf(
                    "Lire" to { screen.open(item) },
                    "Informations" to { screen.info(media) },
                    "Supprimer la copie chiffrée" to
                        {
                            screen.showPrivate(
                                AlertDialog.Builder(c)
                                    .setMessage(
                                        "Supprimer la copie du coffre ? Le fichier original est conservé."
                                    )
                                    .setNegativeButton("Annuler", null)
                                    .setPositiveButton("Supprimer") { _, _ ->
                                        screen.job(
                                            "Suppression",
                                            {
                                                screen.vault.delete(media)
                                                true
                                            },
                                            { screen.reload() },
                                            false,
                                        )
                                    }
                            )
                        },
                ),
                true,
            )
            return
        }
        val items =
            mutableListOf<Pair<String, () -> Unit>>(
                "Lire" to { screen.open(item) },
                "Ajouter / retirer des favoris" to
                    {
                        screen.data.execute {
                            screen.library.toggleFavorite(media.uri)
                            screen.handler.post { screen.reload() }
                        }
                    },
                "Ajouter à une playlist" to { screen.choosePlaylist(media) },
                "Ajouter à la file" to { screen.actions.enqueue(media) },
                "Informations" to { screen.info(media) },
            )
        screen.playlistId?.let { id ->
            items.add(
                "Retirer de cette playlist" to
                    {
                        screen.data.execute {
                            screen.library.removeFromPlaylist(id, media.uri)
                            screen.handler.post { screen.reload() }
                        }
                    }
            )
        }
        choices(media.title, items)
    }

    private fun appearance() {
        choices(
            "Apparence",
            listOf(
                "Clair · Lamba" to { theme("light") },
                "Sombre · Nuit des Hautes Terres" to { theme("dark") },
                "Suivre le téléphone" to { theme("system") },
            ),
        )
    }

    private fun theme(value: String) {
        prefs.edit().putString("appearance", value).apply()
        c.recreate()
    }

    fun settings() {
        pageChanged()
        recent.visibility = View.GONE
        screen.list.visibility = View.GONE
        screen.emptyBox.visibility = View.GONE
        for (i in screen.body.childCount - 1 downTo 0) {
            val child = screen.body.getChildAt(i)
            if (child !== screen.list && child !== screen.emptyBox) screen.body.removeViewAt(i)
        }
        val panel = col().apply { setPadding(0, dp(8), 0, dp(24)) }
        val scroll = ScrollView(c).apply { addView(panel) }
        screen.body.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        fun section(s: String) {
            panel.addView(
                label(s.uppercase(java.util.Locale.FRENCH), 11f, GasyTheme.muted, true).apply {
                    setPadding(0, dp(16), 0, dp(7))
                },
                lp(-1, 42),
            )
        }
        fun action(title: String, detail: String, run: () -> Unit) {
            val box =
                col().apply {
                    background = GasyTheme.ripple(c, GasyTheme.card, 16f)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    addView(label(title, 14f, GasyTheme.text, true))
                    addView(
                        label(detail, 11f, GasyTheme.muted).apply { setPadding(0, dp(5), 0, 0) }
                    )
                    setOnClickListener { run() }
                }
            panel.addView(box, lp(-1, -2).apply { bottomMargin = dp(7) })
        }
        fun toggle(title: String, key: String, default: Boolean = true) {
            panel.addView(
                Switch(c).apply {
                    text = title
                    setTextColor(GasyTheme.text)
                    textSize = 13f
                    isChecked = prefs.getBoolean(key, default)
                    setPadding(dp(12), 0, dp(12), 0)
                    background = GasyTheme.shape(c, GasyTheme.card, 14f)
                    setOnCheckedChangeListener { _, value ->
                        prefs.edit().putBoolean(key, value).apply()
                    }
                },
                lp(-1, 54).apply { bottomMargin = dp(6) },
            )
        }
        section("Apparence")
        action(
            "Thème",
            when (prefs.getString("appearance", "system")) {
                "light" -> "Clair"
                "dark" -> "Sombre"
                else -> "Système"
            },
        ) {
            appearance()
        }
        toggle("Animations discrètes", "motion")
        section("Bibliothèque")
        action("Scanner le téléphone", screen.access()) { screen.actions.requestScan() }
        toggle("Scan automatique", "scan_auto")
        action("Ajouter un dossier", "Inclure les dossiers accessibles et cartes SD") {
            screen.actions.pickFolder()
        }
        action(
            "Durée audio minimale",
            if (prefs.getInt("audio_min_seconds", 0) == 0) "Tout afficher · aucune exclusion"
            else "${prefs.getInt("audio_min_seconds",0)} secondes",
        ) {
            choices(
                "Durée minimale",
                listOf(0, 30, 60, 120, 300).map { value ->
                    (if (value == 0) "Tous les sons" else "$value secondes") to
                        {
                            prefs.edit().putInt("audio_min_seconds", value).apply()
                            settings()
                        }
                },
            )
        }
        section("Lecture")
        toggle("Lecture en arrière-plan", "background_audio")
        toggle("Reprendre à la dernière position", "resume")
        toggle("PiP vidéo automatique (Android 8+)", "auto_pip", false)
        action("Vitesse vidéo par défaut", "${prefs.getFloat("video_default_speed",1f)}×") {
            choices(
                "Vitesse vidéo",
                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).map { value ->
                    "$value×" to
                        {
                            prefs.edit().putFloat("video_default_speed", value).apply()
                            settings()
                        }
                },
            )
        }
        section("Coffre privé")
        action(
            if (screen.vault.configured()) "Modifier le code PIN" else "Créer le code PIN",
            "4 chiffres · chiffrement local",
        ) {
            screen.changePin()
        }
        panel.addView(
            label(
                    "Verrouillage automatique à la sortie de l’application. Les imports sont des copies chiffrées : les originaux restent dans leurs dossiers.",
                    12f,
                    GasyTheme.muted,
                )
                .apply { setPadding(dp(10), dp(4), dp(10), dp(10)) }
        )
        section("À propos")
        action("Profil local", prefs.getString("profile_name", "Mon espace") ?: "Mon espace") {
            val field =
                EditText(c).apply {
                    isSingleLine = true
                    filters = arrayOf(android.text.InputFilter.LengthFilter(40))
                    setText(prefs.getString("profile_name", "Mon espace"))
                }
            AlertDialog.Builder(c)
                .setTitle("Nom du profil")
                .setView(field)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer") { _, _ ->
                    val value = field.text.toString().trim()
                    if (value.isNotEmpty()) prefs.edit().putString("profile_name", value).apply()
                    settings()
                }
                .show()
        }
        action("RAKOTO Player · bêta 3", "Interface Kotlin native · lecture hors ligne LibVLC") {
            screen.actions.settings()
        }
        action("Autorisations Android", "Gérer l’accès aux médias et aux notifications") {
            screen.actions.systemSettings()
        }
    }

    fun lockedVault() {
        pageChanged()
        screen.search.visibility = View.GONE
        (screen.chips.parent as View).visibility = View.GONE
        (screen.pathLabel.parent as View).visibility = View.GONE
        screen.emptyBox.visibility = View.GONE
        if (pinPanel == null) pin(false, Runnable { screen.reload() })
        else
            (pinPanel?.tag as? View)?.let {
                if (it.parent == null) screen.body.addView(it, FrameLayout.LayoutParams(-1, -1))
            }
    }

    fun pin(replace: Boolean, next: Runnable) {
        if (pinPanel != null) {
            pinNext = next
            return
        }
        replacePin = replace
        pinNext = next
        code.setLength(0)
        confirmation?.fill('\u0000')
        confirmation = null
        val epoch = ++pinEpoch
        screen.actions.secure(true)
        val panel =
            col().apply {
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(16), dp(24), dp(18))
            }
        pinPanel = panel
        val create = replace || !screen.vault.configured()
        val icon =
            GasyTheme.icon(c, "lock", "Coffre privé").apply {
                isClickable = false
                background = GasyTheme.shape(c, GasyTheme.forest, 24f)
                setImageDrawable(Ui.Glyph("lock", Color.WHITE))
            }
        panel.addView(icon, lp(64, 64))
        val title =
            label(
                    if (create) "Créez votre code PIN" else "Entrez votre code PIN",
                    22f,
                    GasyTheme.text,
                    true,
                )
                .apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(18), 0, dp(8))
                }
        panel.addView(title, lp(-1, -2))
        val status =
            label("4 chiffres · conservé uniquement sur cet appareil", 11f, GasyTheme.muted).apply {
                gravity = Gravity.CENTER
            }
        panel.addView(status, lp(-1, 36))
        val dots =
            label("○   ○   ○   ○", 26f, GasyTheme.primary).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        panel.addView(dots, lp(-1, 50))
        val keys = col()
        panel.addView(keys, lp(-1, -2))
        fun update() {
            dots.text = (0..3).joinToString("   ") { if (it < code.length) "●" else "○" }
        }
        fun submit() {
            if (create && confirmation == null) {
                confirmation = code.toString().toCharArray()
                code.setLength(0)
                update()
                title.text = "Confirmez votre code"
                return
            }
            val attempt = code.toString().toCharArray()
            code.setLength(0)
            update()
            if (create && !Arrays.equals(attempt, confirmation)) {
                attempt.fill('\u0000')
                confirmation?.fill('\u0000')
                confirmation = null
                title.text = "Créez votre code PIN"
                status.text = "Les codes ne correspondent pas. Recommencez."
                return
            }
            confirmation?.fill('\u0000')
            confirmation = null
            pinBusy = true
            status.text = if (create) "Protection du coffre…" else "Vérification du code…"
            screen.jobs.execute {
                try {
                    if (epoch != pinEpoch || screen.destroyed) return@execute
                    if (replace) screen.vault.changePin(attempt)
                    else if (create) screen.vault.create(attempt) else screen.vault.unlock(attempt)
                    if (epoch != pinEpoch || screen.destroyed) {
                        screen.vault.lock()
                        return@execute
                    }
                    screen.handler.post {
                        if (epoch != pinEpoch || screen.destroyed || !screen.active) {
                            screen.vault.lock()
                            return@post
                        }
                        pinBusy = false
                        val done = pinNext
                        clearPin(false)
                        done?.run()
                    }
                } catch (e: Exception) {
                    screen.handler.post {
                        if (epoch == pinEpoch && !screen.destroyed) {
                            pinBusy = false
                            status.text = e.message ?: "Code non accepté"
                            title.text =
                                if (create) "Créez votre code PIN" else "Entrez votre code PIN"
                        }
                    }
                } finally {
                    attempt.fill('\u0000')
                }
            }
        }
        for (r in 0..3) {
            val line = row()
            for (column in 0..2) {
                val value = if (r < 3) "${r*3+column+1}" else arrayOf("Annuler", "0", "⌫")[column]
                val key =
                    GasyTheme.button(c, value, false).apply {
                        textSize = if (value == "Annuler") 11f else 24f
                        setOnClickListener {
                            if (!pinBusy) {
                                when (value) {
                                    "Annuler" -> clearPin(true)
                                    "⌫" -> {
                                        if (code.isNotEmpty()) code.deleteCharAt(code.length - 1)
                                    }
                                    else -> if (code.length < 4) code.append(value)
                                }
                                update()
                                if (code.length == 4) submit()
                            }
                        }
                    }
                line.addView(
                    key,
                    LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    },
                )
            }
            keys.addView(line)
        }
        if (screen.tab == 2 && !replace) {
            val scroll = ScrollView(c)
            scroll.addView(panel)
            screen.body.addView(scroll, FrameLayout.LayoutParams(-1, -1))
            pinPanel = panel
            panel.tag = scroll
        } else {
            val dialog = showPanel("Coffre privé", panel, true)
            screen.pinDialog = dialog
            dialog.setOnDismissListener {
                screen.privateDialogs.remove(dialog)
                screen.pinDialog = null
                if (pinPanel === panel) clearPin(true)
            }
        }
    }

    private fun clearPin(cancel: Boolean) {
        ++pinEpoch
        code.setLength(0)
        confirmation?.fill('\u0000')
        confirmation = null
        val panel = pinPanel
        pinPanel = null
        pinNext = null
        (panel?.tag as? View)?.let { (it.parent as? ViewGroup)?.removeView(it) }
        (panel?.parent as? ViewGroup)?.removeView(panel)
        screen.pinDialog?.let {
            screen.pinDialog = null
            it.dismiss()
        }
        if (cancel && pinBusy) screen.vault.lock()
        pinBusy = false
        screen.actions.secure(screen.tab == 2)
        if (cancel && screen.tab == 2 && !screen.destroyed && screen.active) {
            screen.emptyBox.visibility = View.VISIBLE
            screen.emptyAction.visibility = View.VISIBLE
        }
    }

    fun vaultLocked() {
        clearPin(true)
        sheet?.let { if (screen.privateDialogs.contains(it)) it.dismiss() }
    }

    fun back(): Boolean {
        if (sheet?.isShowing == true) {
            sheet?.dismiss()
            return true
        }
        return false
    }

    fun scanStarted() {
        screen.scanStatus.text = "Analyse des médias accessibles…"
        screen.scanStatus.visibility = View.VISIBLE
    }

    fun scanFinished(result: MediaScanner.Result) {
        screen.toast("${result.audio} fichiers audio et ${result.video} vidéos analysés")
    }

    fun destroy() {
        clearPin(true)
        sheet?.dismiss()
        art.close()
    }
}
