package com.rakotoplayer.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Audio, Video, Vault, Profile. Playback stays in the existing native service. */
final class BrowserScreen extends LinearLayout {
  interface Actions {
    void play(List<MediaEntry> entries, int index);

    void enqueue(MediaEntry entry);

    void playShuffled(List<MediaEntry> entries);

    void completeVaultMove(List<MediaEntry> entries);

    void exportPrivate(MediaEntry entry);

    void linkVaultOriginal(MediaEntry entry);

    void vaultReady();

    void pickMedia(boolean vault);

    void pickFolder();

    void requestScan();

    void settings();

    void openPrivate(MediaEntry entry, ParcelFileDescriptor descriptor);

    void locked();

    void secure(boolean secure);

    void systemSettings();
  }

  interface Work<T> {
    T run() throws Exception;
  }

  interface Done<T> {
    void accept(T value);
  }

  GasySkin skin;
  String groupValue = null;
  final Activity activity;
  final Library library;
  final Actions actions;
  final Vault vault;
  final Handler handler = new Handler(Looper.getMainLooper());
  final ExecutorService data = Executors.newSingleThreadExecutor(),
      jobs = Executors.newSingleThreadExecutor();
  final LinearLayout navigation, chips;
  final FrameLayout body;
  final ListView list;
  final LinearLayout emptyBox;
  final TextView heading, subtitle, scanStatus, pathLabel, emptyLabel;
  final EditText search;
  final Button emptyAction;
  final ImageButton add;
  final RowAdapter adapter = new RowAdapter();
  List<Row> rows = new ArrayList<>();
  int tab, sort, loadVersion;
  boolean ascending = true, busy;
  volatile boolean destroyed, scanning;
  final Set<AlertDialog> privateDialogs = new HashSet<>();
  final Set<ParcelFileDescriptor> pendingDescriptors = Collections.synchronizedSet(new HashSet<>());
  String mode = "all", folder = "", query = "", playlistId, playlistName = "";
  final String[] modes = {"all", "folders"};
  ProgressDialog operation;
  AlertDialog pinDialog;
  long lastScan;
  String lastAccess = "";
  final Runnable searchChanged = this::reload;
  boolean active, scanPending;
  final Runnable mediaChanged = this::scan;
  final android.database.ContentObserver observer =
      new android.database.ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
          scanPending = true;
          if (active && library.preferences.getBoolean("scan_auto", true)) {
            handler.removeCallbacks(mediaChanged);
            handler.postDelayed(mediaChanged, 900);
          }
        }
      };

  static final class Row {
    String title, detail, icon = "music", folder, group;
    MediaEntry media;
    Library.Playlist playlist;
    long size, duration, added;

    Row(String title, String detail) {
      this.title = title;
      this.detail = detail;
    }
  }

  BrowserScreen(Activity activity, Library library, Actions actions) {
    super(activity);
    this.activity = activity;
    this.library = library;
    this.actions = actions;
    vault = new Vault(activity);
    setOrientation(VERTICAL);
    setPadding(dp(18), 0, dp(18), 0);
    LinearLayout header = row();
    header.setPadding(0, dp(10), 0, dp(8));
    ImageView logo = new ImageView(activity);
    logo.setImageResource(R.drawable.rakoto_mark);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    header.addView(logo, lp(46, 43));
    LinearLayout titles = column();
    titles.setPadding(dp(10), 0, 0, 0);
    heading = text("Audio", 23, Ui.NAVY);
    heading.setTypeface(null, Typeface.BOLD);
    subtitle = text("Tous vos sons, simplement.", 11, Ui.MUTED);
    titles.addView(heading);
    titles.addView(subtitle);
    header.addView(titles, new LayoutParams(0, -2, 1));
    add = Ui.icon(activity, "plus", "Importer des fichiers", false);
    header.addView(add, lp(46, 46));
    add.setOnClickListener(
        v -> {
          if (tab == 2) withVault(() -> actions.pickMedia(true));
          else actions.pickMedia(false);
        });
    ImageButton menu = Ui.icon(activity, "more", "Options de la bibliothèque", false);
    header.addView(menu, lp(42, 46));
    menu.setOnClickListener(v -> menu(menu));
    addView(header, lp(-1, 70));
    search = new EditText(activity);
    search.setSingleLine();
    search.setHint("Rechercher");
    search.setTextSize(14);
    search.setTextColor(Ui.INK);
    search.setPadding(dp(14), 0, dp(14), 0);
    search.setBackground(Ui.background(Color.WHITE, 15, activity));
    addView(search, lp(-1, 46));
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            query = s.toString();
            handler.removeCallbacks(searchChanged);
            handler.postDelayed(searchChanged, 180);
          }

          public void afterTextChanged(Editable value) {}
        });
    HorizontalScrollView filters = new HorizontalScrollView(activity);
    filters.setHorizontalScrollBarEnabled(false);
    chips = row();
    chips.setPadding(0, dp(10), 0, dp(7));
    filters.addView(chips);
    addView(filters, lp(-1, 58));
    LinearLayout tools = row();
    pathLabel = text("Tous les titres", 12, Ui.NAVY);
    pathLabel.setSingleLine();
    pathLabel.setEllipsize(TextUtils.TruncateAt.END);
    pathLabel.setGravity(Gravity.CENTER_VERTICAL);
    tools.addView(pathLabel, new LayoutParams(0, dp(40), 1));
    pathLabel.setOnClickListener(v -> back());
    Button order = Ui.button(activity, "Trier", false);
    order.setTextSize(11);
    tools.addView(order, lp(65, 38));
    order.setOnClickListener(v -> sortDialog());
    addView(tools);
    scanStatus = text("", 10, Ui.MUTED);
    scanStatus.setPadding(0, dp(2), 0, dp(5));
    scanStatus.setVisibility(GONE);
    addView(scanStatus, lp(-1, -2));
    body = new FrameLayout(activity);
    addView(body, new LayoutParams(-1, 0, 1));
    list = new ListView(activity);
    list.setDivider(null);
    list.setCacheColorHint(Color.TRANSPARENT);
    list.setAdapter(adapter);
    body.addView(list, new FrameLayout.LayoutParams(-1, -1));
    emptyBox = column();
    emptyBox.setGravity(Gravity.CENTER);
    emptyBox.setPadding(dp(16), dp(14), dp(16), dp(14));
    emptyLabel = text("Votre bibliothèque se prépare…", 15, Ui.MUTED);
    emptyLabel.setGravity(Gravity.CENTER);
    emptyBox.addView(emptyLabel);
    emptyAction = Ui.button(activity, "Scanner mes médias", true);
    LayoutParams emptyParams = lp(-2, 48);
    emptyParams.topMargin = dp(18);
    emptyBox.addView(emptyAction, emptyParams);
    body.addView(emptyBox, new FrameLayout.LayoutParams(-1, -1));
    list.setEmptyView(emptyBox);
    emptyAction.setOnClickListener(
        v -> {
          if (tab == 2) withVault(() -> actions.pickMedia(true));
          else if ("playlists".equals(mode)) namePlaylist(null);
          else actions.requestScan();
        });
    navigation = row();
    navigation.setPadding(dp(5), dp(5), dp(5), dp(5));
    navigation.setBackground(Ui.background(0xfaffffff, 25, activity));
    navigation.setElevation(dp(9));
    String[] names = {"Audio", "Vidéo", "Coffre", "Profil"},
        icons = {"music", "video", "lock", "person"};
    for (int i = 0; i < 4; i++) {
      final int selected = i;
      LinearLayout item = column();
      item.setGravity(Gravity.CENTER);
      item.setContentDescription(names[i]);
      ImageView icon = new ImageView(activity);
      icon.setImageDrawable(new Ui.Glyph(icons[i], Ui.BLUE));
      item.addView(icon, lp(22, 22));
      TextView label = text(names[i], 10, Ui.NAVY);
      label.setGravity(Gravity.CENTER);
      item.addView(label, lp(-1, 20));
      navigation.addView(item, new LayoutParams(0, dp(50), 1));
      item.setOnClickListener(v -> select(selected));
    }
    select(0);
    try {
      activity
          .getContentResolver()
          .registerContentObserver(
              android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
      activity
          .getContentResolver()
          .registerContentObserver(
              android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
    } catch (SecurityException ignored) {
    }
  }

  View navigation() {
    return navigation;
  }

  int dp(float value) {
    return Ui.dp(activity, value);
  }

  LayoutParams lp(int width, int height) {
    return new LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height));
  }

  LinearLayout column() {
    LinearLayout view = new LinearLayout(activity);
    view.setOrientation(VERTICAL);
    return view;
  }

  LinearLayout row() {
    LinearLayout view = new LinearLayout(activity);
    view.setOrientation(HORIZONTAL);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  TextView text(String value, float size, int color) {
    return Ui.text(activity, value, size, color);
  }

  void toast(String message) {
    if (!destroyed && active) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
  }

  static String bytes(long size) {
    if (size >= 1000000000L) return String.format(Locale.FRANCE, "%.1f Go", size / 1000000000.0);
    if (size >= 1000000) return String.format(Locale.FRANCE, "%.1f Mo", size / 1000000.0);
    return String.format(Locale.FRANCE, "%.0f Ko", size / 1000.0);
  }

  String duration(long value) {
    return value > 0 ? Ui.time(value) : "Durée inconnue";
  }

  void installGasy() {
    skin = new GasySkin(this);
    skin.attach();
    filters();
    reload();
  }

  void select(int value) {
    groupValue = null;
    if (tab == 2 && value != 2) lockVault();
    tab = value;
    mode = tab < 2 ? modes[tab] : "all";
    folder = "";
    playlistId = null;
    playlistName = "";
    search.setText("");
    for (int i = 0; i < navigation.getChildCount(); i++)
      navigation
          .getChildAt(i)
          .setBackground(Ui.background(i == tab ? 0xffe0eeff : Color.TRANSPARENT, 20, activity));
    heading.setText(new String[] {"Audio", "Vidéo", "Coffre", "Profil"}[tab]);
    subtitle.setText(
        new String[] {
              "Tous vos sons, simplement.",
              "Vos vidéos, dossier par dossier.",
              "Votre espace privé.",
              "Votre RAKOTO, à votre façon."
            }
            [tab]);
    add.setVisibility(tab == 3 ? GONE : VISIBLE);
    search.setVisibility(tab == 3 ? GONE : VISIBLE);
    ((View) chips.getParent()).setVisibility(tab == 3 ? GONE : VISIBLE);
    ((View) pathLabel.getParent()).setVisibility(tab == 3 ? GONE : VISIBLE);
    scanStatus.setVisibility(tab < 2 && scanStatus.length() > 0 ? VISIBLE : GONE);
    actions.secure(tab == 2);
    restoreSort();
    filters();
    reload();
    if (skin != null) skin.pageChanged();
    if (tab == 2 && !vault.unlocked())
      handler.post(
          () -> {
            if (tab == 2 && !destroyed) withVault(this::reload);
          });
  }

  void restoreSort() {
    sort = library.preferences.getInt("sort:" + tab, tab == 1 ? 3 : 0);
    ascending = library.preferences.getBoolean("ascending:" + tab, tab != 1);
  }

  void filters() {
    chips.removeAllViews();
    String[] keys =
        tab == 0
            ? new String[] {"all", "albums", "artists", "folders", "playlists", "favorites"}
            : tab == 1
                ? new String[] {"all", "recent", "unread", "folders", "favorites", "playlists"}
                : new String[] {"all", "videos", "images", "pending", "lock"};
    for (String key : keys) {
      String label =
          "all".equals(key)
              ? "Tous"
              : "folders".equals(key)
                  ? "Dossiers"
                  : "playlists".equals(key)
                      ? "Playlists"
                      : "favorites".equals(key) ? "Favoris" : "Verrouiller";
      Button button = Ui.button(activity, label, false);
      button.setTag(key);
      button.setTextSize(12);
      if (key.equals(mode)) button.setBackground(Ui.background(0xffdcecff, 14, activity));
      LayoutParams p = lp(-2, 40);
      p.rightMargin = dp(7);
      chips.addView(button, p);
      button.setOnClickListener(
          v -> {
            if ("lock".equals(key)) {
              lockVault();
              reload();
              return;
            }
            groupValue = null;
            mode = key;
            playlistId = null;
            folder = "";
            filters();
            reload();
          });
    }
  }

  void menu(View anchor) {
    if (skin != null) {
      skin.menu();
      return;
    }
    PopupMenu menu = new PopupMenu(activity, anchor);
    String[] labels =
        tab == 2
            ? new String[] {
              "Importer des vidéos", "Trier", "Verrouiller le coffre", "Changer le code"
            }
            : new String[] {
              "Scanner les médias",
              "Ajouter un dossier",
              "Importer des fichiers",
              "Créer une playlist",
              "Réglages de lecture"
            };
    for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]);
    menu.setOnMenuItemClickListener(
        item -> {
          int id = item.getItemId();
          if (tab == 2) {
            if (id == 0) withVault(() -> actions.pickMedia(true));
            else if (id == 1) sortDialog();
            else if (id == 2) {
              lockVault();
              reload();
            } else changePin();
          } else {
            if (id == 0) actions.requestScan();
            else if (id == 1) actions.pickFolder();
            else if (id == 2) actions.pickMedia(false);
            else if (id == 3) namePlaylist(null);
            else actions.settings();
          }
          return true;
        });
    menu.show();
  }

  void reload() {
    if (destroyed) return;
    final int version = ++loadVersion, selected = tab, order = sort;
    final boolean asc = ascending;
    final String filter = mode,
        path = folder,
        term = query.toLowerCase(Locale.ROOT),
        playlist = playlistId;
    final String selectedGroup = groupValue;
    final long generation = vault.generation();
    if (tab == 3) {
      profile();
      return;
    }
    for (int i = body.getChildCount() - 1; i >= 0; i--) {
      View child = body.getChildAt(i);
      if (child != list && child != emptyBox) body.removeViewAt(i);
    }
    search.setEnabled(tab != 2 || vault.unlocked());
    emptyAction.setVisibility(VISIBLE);
    if (tab == 2 && !vault.unlocked()) {
      rows = new ArrayList<>();
      adapter.notifyDataSetChanged();
      list.setVisibility(GONE);
      emptyBox.setVisibility(VISIBLE);
      emptyLabel.setText(
          vault.configured()
              ? "Coffre verrouillé\n\nEntrez votre code pour retrouver vos vidéos."
              : "Votre coffre privé\n\nCréez un code à 4 chiffres pour commencer.");
      emptyAction.setText(vault.configured() ? "Déverrouiller" : "Créer mon code");
      pathLabel.setText("Copies chiffrées sur cet appareil");
      if (skin != null) skin.lockedVault();
      return;
    }
    if (tab == 2 && vault.unlocked()) actions.vaultReady();
    emptyAction.setText(
        "playlists".equals(mode) && playlistId == null
            ? "Créer une playlist"
            : tab == 2 ? "Importer vidéos et images" : "Scanner mes médias");
    pathLabel.setText(
        playlistId != null
            ? "‹ " + playlistName
            : "folders".equals(mode)
                ? (folder.isEmpty() ? "Dossiers" : "‹ " + folder)
                : "playlists".equals(mode)
                    ? "Playlists"
                    : "favorites".equals(mode)
                        ? "Favoris"
                        : tab == 2 ? "Vidéos et images privées" : "Tous les fichiers");
    if (skin != null) skin.pageChanged();
    data.execute(
        () -> {
          try {
            List<Row> result = new ArrayList<>();
            if ("playlists".equals(filter) && playlist == null) {
              for (Library.Playlist p : library.playlists(selected == 1)) {
                if (!term.isEmpty() && !p.name.toLowerCase(Locale.ROOT).contains(term)) continue;
                Row row = new Row(p.name, p.count + " fichier" + (p.count > 1 ? "s" : ""));
                row.playlist = p;
                row.icon = "playlist";
                result.add(row);
              }
            } else {
              List<MediaEntry> media =
                  selected == 2
                      ? vault.entries()
                      : library.entries(selected == 1, "favorites".equals(filter), playlist);
              List<MediaEntry> matches = new ArrayList<>();
              int minimum = selected == 0 ? library.preferences.getInt("audio_min_seconds", 0) : 0;
              for (MediaEntry e : media)
                if ((minimum == 0 || e.durationMs >= minimum * 1000L)
                    && (!"recent".equals(filter)
                        || e.addedAt >= System.currentTimeMillis() - 14L * 86400000L)
                    && (!"unread".equals(filter) || !library.played(e.uri))
                    && (selectedGroup == null
                        || ("albums".equals(filter) ? e.album : e.artist).equals(selectedGroup))
                    && (term.isEmpty()
                        || (e.title + " " + e.artist + " " + e.album + " " + e.name + " "
                                + e.folder)
                            .toLowerCase(Locale.ROOT)
                            .contains(term))) matches.add(e);
              Library.sort(matches, order, asc);
              if (("albums".equals(filter) || "artists".equals(filter)) && selectedGroup == null) {
                Map<String, Row> groups = new LinkedHashMap<>();
                Map<String, Integer> counts = new HashMap<>();
                for (MediaEntry e : matches) {
                  String key = "albums".equals(filter) ? e.album : e.artist;
                  Row group = groups.get(key);
                  if (group == null) {
                    String name =
                        key.isEmpty()
                            ? ("albums".equals(filter) ? "Sans album" : "Artiste inconnu")
                            : "Fichier local".equals(key) ? "Artiste inconnu" : key;
                    group = new Row(name, "");
                    group.group = key;
                    group.icon = "albums".equals(filter) ? "music" : "person";
                    groups.put(key, group);
                  }
                  counts.put(key, (counts.containsKey(key) ? counts.get(key) : 0) + 1);
                }
                for (Map.Entry<String, Row> group : groups.entrySet()) {
                  group.getValue().detail = counts.get(group.getKey()) + " chanson(s)";
                  result.add(group.getValue());
                }
              } else if ("folders".equals(filter) && term.isEmpty()) {
                Map<String, Row> folders = new LinkedHashMap<>();
                Map<String, Integer> counts = new HashMap<>();
                String prefix = path.isEmpty() ? "" : path + "/";
                for (MediaEntry e : matches) {
                  String current = e.folder == null ? "Fichiers ajoutés" : e.folder;
                  if (current.equals(path)) {
                    result.add(mediaRow(e));
                    continue;
                  }
                  if (!current.startsWith(prefix)) continue;
                  String rest = current.substring(prefix.length());
                  int slash = rest.indexOf('/');
                  String name = slash < 0 ? rest : rest.substring(0, slash);
                  String child = prefix + name;
                  if (name.isEmpty()) continue;
                  Row row = folders.get(child);
                  if (row == null) {
                    row = new Row(name, "");
                    row.icon = "folder";
                    row.folder = child;
                    folders.put(child, row);
                  }
                  row.size += e.size;
                  row.duration += e.durationMs;
                  row.added = Math.max(row.added, e.addedAt);
                  counts.put(child, (counts.containsKey(child) ? counts.get(child) : 0) + 1);
                }
                List<Row> sortedFolders = new ArrayList<>(folders.values());
                Collections.sort(
                    sortedFolders,
                    (a, b) -> {
                      int comparison =
                          order == 1
                              ? Long.compare(a.duration, b.duration)
                              : order == 2
                                  ? Long.compare(a.size, b.size)
                                  : order == 3
                                      ? Long.compare(a.added, b.added)
                                      : a.title.compareToIgnoreCase(b.title);
                      return asc ? comparison : -comparison;
                    });
                for (Row row : sortedFolders)
                  row.detail = counts.get(row.folder) + " vidéo(s) · " + bytes(row.size);
                result.addAll(0, sortedFolders);
              } else for (MediaEntry e : matches) result.add(mediaRow(e));
            }
            handler.post(
                () -> {
                  if (destroyed
                      || version != loadVersion
                      || selected != tab
                      || selected == 2 && (!vault.unlocked() || vault.generation() != generation))
                    return;
                  rows = result;
                  adapter.notifyDataSetChanged();
                  list.setVisibility(result.isEmpty() ? GONE : VISIBLE);
                  emptyBox.setVisibility(result.isEmpty() ? VISIBLE : GONE);
                  emptyLabel.setText(
                      !term.isEmpty()
                          ? "Aucun résultat."
                          : "playlists".equals(filter)
                              ? "Vos playlists commencent ici."
                              : selected == 2
                                  ? "Ajoutez vos premières vidéos et images.\n"
                                      + "Les originaux sont retirés après chiffrement vérifié et"
                                      + " autorisation Android."
                                  : "Aucun média dans cette vue.\n"
                                      + "Autorisez le scan ou ajoutez un dossier.");
                  if (!term.isEmpty()) emptyAction.setVisibility(GONE);
                  if (skin != null) skin.rowsChanged();
                });
          } catch (Exception failure) {
            handler.post(
                () -> {
                  if (!destroyed && version == loadVersion) {
                    rows = new ArrayList<>();
                    adapter.notifyDataSetChanged();
                    emptyLabel.setText(
                        selected == 2
                            ? "Déverrouillez le coffre pour continuer."
                            : "Cette bibliothèque n’est pas accessible pour le moment.");
                  }
                });
          }
        });
    list.setOnItemClickListener(
        (parent, view, position, id) -> {
          if (skin == null || !skin.isGrid()) open(rows.get(position));
        });
    list.setOnItemLongClickListener(
        (parent, view, position, id) -> {
          if (skin == null || !skin.isGrid()) rowActions(rows.get(position), view);
          return true;
        });
  }

  Row mediaRow(MediaEntry e) {
    Row row =
        new Row(e.title, (e.image ? "Image" : duration(e.durationMs)) + " · " + bytes(e.size));
    row.media = e;
    row.icon = e.image ? "photo" : e.video ? "video" : "music";
    if (!e.video && !e.image) row.detail = e.artist + " · " + row.detail;
    return row;
  }

  void open(Row row) {
    if (row.group != null) {
      groupValue = row.group;
      reload();
      return;
    }
    if (row.folder != null) {
      folder = row.folder;
      reload();
    } else if (row.playlist != null) {
      playlistId = row.playlist.id;
      playlistName = row.playlist.name;
      reload();
    } else if (row.media != null) {
      if (row.media.isPrivate()) {
        long generation = vault.generation();
        job(
            "Préparation de la lecture privée",
            () -> vault.open(row.media),
            descriptor -> {
              if (vault.unlocked() && generation == vault.generation())
                actions.openPrivate(row.media, descriptor);
              else
                try {
                  descriptor.close();
                } catch (IOException ignored) {
                }
            },
            false);
      } else {
        List<MediaEntry> queue = new ArrayList<>();
        int index = 0;
        for (Row item : rows)
          if (item.media != null) {
            if (item == row) index = queue.size();
            queue.add(item.media);
          }
        actions.play(queue, index);
      }
    }
  }

  void rowActions(Row row, View anchor) {
    if (skin != null) {
      skin.rowMenu(row);
      return;
    }
    if (row.playlist != null) {
      new AlertDialog.Builder(activity)
          .setTitle(row.title)
          .setItems(
              new String[] {"Ouvrir", "Renommer", "Supprimer la playlist"},
              (dialog, which) -> {
                if (which == 0) open(row);
                else if (which == 1) namePlaylist(row.playlist);
                else
                  new AlertDialog.Builder(activity)
                      .setMessage("Supprimer cette playlist ? Les fichiers sont conservés.")
                      .setNegativeButton("Annuler", null)
                      .setPositiveButton(
                          "Supprimer",
                          (d, w) ->
                              data.execute(
                                  () -> {
                                    library.deletePlaylist(row.playlist.id);
                                    handler.post(this::reload);
                                  }))
                      .show();
              })
          .show();
      return;
    }
    if (row.folder != null) {
      new AlertDialog.Builder(activity)
          .setTitle(row.title)
          .setItems(
              new String[] {"Ouvrir le dossier", "Lire toutes ses vidéos"},
              (d, i) -> {
                if (i == 0) open(row);
                else
                  data.execute(
                      () -> {
                        List<MediaEntry> queue = new ArrayList<>();
                        for (MediaEntry e : library.entries(true, false, null))
                          if (e.folder.equals(row.folder) || e.folder.startsWith(row.folder + "/"))
                            queue.add(e);
                        Library.sort(queue, sort, ascending);
                        handler.post(
                            () -> {
                              if (!destroyed && !queue.isEmpty()) actions.play(queue, 0);
                            });
                      });
              })
          .show();
      return;
    }
    if (row.media == null) return;
    MediaEntry entry = row.media;
    if (entry.isPrivate()) {
      showPrivate(
          new AlertDialog.Builder(activity)
              .setTitle(entry.name)
              .setItems(
                  new String[] {"Lire", "Informations", "Supprimer la copie du coffre"},
                  (d, i) -> {
                    if (i == 0) open(row);
                    else if (i == 1) info(entry);
                    else
                      showPrivate(
                          new AlertDialog.Builder(activity)
                              .setTitle("Supprimer cette copie ?")
                              .setMessage(
                                  "La copie chiffrée sera supprimée du coffre. Le fichier d’origine"
                                      + " est conservé.")
                              .setNegativeButton("Annuler", null)
                              .setPositiveButton(
                                  "Supprimer",
                                  (dialog, which) ->
                                      job(
                                          "Suppression",
                                          () -> {
                                            vault.delete(entry);
                                            return true;
                                          },
                                          ignored -> reload(),
                                          false)));
                  }));
      return;
    }
    PopupMenu menu = new PopupMenu(activity, anchor);
    menu.getMenu().add(0, 0, 0, "Ajouter / retirer des favoris");
    menu.getMenu().add(0, 1, 1, "Ajouter à une playlist");
    menu.getMenu().add(0, 2, 2, "Informations");
    if (playlistId != null) menu.getMenu().add(0, 3, 3, "Retirer de cette playlist");
    menu.setOnMenuItemClickListener(
        item -> {
          if (item.getItemId() == 0)
            data.execute(
                () -> {
                  library.toggleFavorite(entry.uri);
                  handler.post(this::reload);
                });
          else if (item.getItemId() == 1) choosePlaylist(entry);
          else if (item.getItemId() == 2) info(entry);
          else {
            String id = playlistId;
            data.execute(
                () -> {
                  library.removeFromPlaylist(id, entry.uri);
                  handler.post(this::reload);
                });
          }
          return true;
        });
    menu.show();
  }

  void info(MediaEntry entry) {
    AlertDialog.Builder builder =
        new AlertDialog.Builder(activity)
            .setTitle(entry.name)
            .setMessage(
                bytes(entry.size)
                    + "\n"
                    + (entry.image ? "Image" : duration(entry.durationMs))
                    + "\nAjout : "
                    + DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRANCE)
                        .format(new Date(entry.addedAt))
                    + (entry.isPrivate()
                        ? "\nFormat privé : "
                            + (entry.image ? ".rpi" : ".rpv")
                            + " · AES-256-GCM\n"
                            + (!entry.originalUri.isEmpty()
                                ? "Original à retirer : déplacement non terminé"
                                : entry.moved
                                    ? "Original retiré après vérification"
                                    : "Ancienne copie : original non associé")
                        : "\n" + entry.folder))
            .setPositiveButton("Fermer", null);
    if (entry.isPrivate()) showPrivate(builder);
    else builder.show();
  }

  void showPrivate(AlertDialog.Builder builder) {
    AlertDialog dialog = builder.create();
    privateDialogs.add(dialog);
    dialog.setOnDismissListener(d -> privateDialogs.remove(dialog));
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    dialog.show();
  }

  void namePlaylist(Library.Playlist existing) {
    EditText name = new EditText(activity);
    name.setSingleLine();
    name.setHint("Nom de la playlist");
    name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(100)});
    if (existing != null) name.setText(existing.name);
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(existing == null ? "Nouvelle playlist" : "Renommer")
            .setView(name)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      String title = name.getText().toString().trim();
                      if (title.isEmpty()) {
                        name.setError("Indiquez un nom");
                        return;
                      }
                      boolean video = tab == 1;
                      data.execute(
                          () -> {
                            if (existing == null) library.createPlaylist(title, video);
                            else library.renamePlaylist(existing.id, title);
                            handler.post(this::reload);
                          });
                      dialog.dismiss();
                    }));
    dialog.show();
  }

  void choosePlaylist(MediaEntry entry) {
    data.execute(
        () -> {
          List<Library.Playlist> playlists = library.playlists(entry.video);
          handler.post(
              () -> {
                if (destroyed) return;
                String[] names = new String[playlists.size() + 1];
                for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
                names[names.length - 1] = "+ Nouvelle playlist";
                new AlertDialog.Builder(activity)
                    .setTitle("Ajouter à une playlist")
                    .setItems(
                        names,
                        (dialog, which) -> {
                          if (which < playlists.size()) {
                            data.execute(
                                () -> library.addToPlaylist(playlists.get(which).id, entry.uri));
                            toast("Ajouté à la playlist");
                          } else {
                            EditText name = new EditText(activity);
                            name.setSingleLine();
                            AlertDialog create =
                                new AlertDialog.Builder(activity)
                                    .setTitle("Nouvelle playlist")
                                    .setView(name)
                                    .setNegativeButton("Annuler", null)
                                    .setPositiveButton("Créer et ajouter", null)
                                    .create();
                            create.setOnShowListener(
                                d ->
                                    create
                                        .getButton(-1)
                                        .setOnClickListener(
                                            v -> {
                                              String title = name.getText().toString().trim();
                                              if (title.isEmpty()) {
                                                name.setError("Indiquez un nom");
                                                return;
                                              }
                                              data.execute(
                                                  () -> {
                                                    String id =
                                                        library.createPlaylist(title, entry.video);
                                                    library.addToPlaylist(id, entry.uri);
                                                    handler.post(this::reload);
                                                  });
                                              create.dismiss();
                                            }));
                            create.show();
                          }
                        })
                    .show();
              });
        });
  }

  void sortDialog() {
    if (skin != null) {
      skin.sort();
      return;
    }
    String[] labels = {"Nom", "Durée", "Taille", "Date d’ajout"};
    final int[] selected = {sort};
    final boolean[] asc = {ascending};
    LinearLayout panel = column();
    panel.setPadding(dp(18), dp(8), dp(18), dp(8));
    Spinner criterion = new Spinner(activity);
    criterion.setAdapter(
        new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, labels));
    criterion.setSelection(sort);
    panel.addView(criterion, lp(-1, 48));
    Switch direction = new Switch(activity);
    direction.setText("Ordre croissant");
    direction.setTextSize(14);
    direction.setChecked(ascending);
    panel.addView(direction, lp(-1, 48));
    TextView explanation =
        text(
            "Nom A → Z · plus court → plus long · plus petit → plus grand · ancien → récent.\n"
                + "Désactivez pour inverser.",
            12,
            Ui.MUTED);
    panel.addView(explanation);
    new AlertDialog.Builder(activity)
        .setTitle("Trier les fichiers")
        .setView(panel)
        .setNegativeButton("Annuler", null)
        .setPositiveButton(
            "Appliquer",
            (d, w) -> {
              sort = criterion.getSelectedItemPosition();
              ascending = direction.isChecked();
              library
                  .preferences
                  .edit()
                  .putInt("sort:" + tab, sort)
                  .putBoolean("ascending:" + tab, ascending)
                  .apply();
              reload();
            })
        .show();
  }

  boolean back() {
    if (skin != null && skin.back()) return true;
    if (groupValue != null) {
      groupValue = null;
      reload();
      return true;
    }
    if (tab >= 2) return false;
    if (playlistId != null) {
      playlistId = null;
      reload();
      return true;
    }
    if (!folder.isEmpty()) {
      int slash = folder.lastIndexOf('/');
      folder = slash < 0 ? "" : folder.substring(0, slash);
      reload();
      return true;
    }
    if (!query.isEmpty()) {
      search.setText("");
      return true;
    }
    return false;
  }

  final class RowAdapter extends BaseAdapter {
    public int getCount() {
      return skin == null ? rows.size() : skin.rowCount();
    }

    public Row getItem(int position) {
      return rows.get(position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View recycled, ViewGroup parent) {
      if (skin != null) return skin.rowView(position, recycled, parent);
      LinearLayout root;
      ImageView icon;
      TextView title, detail;
      ImageButton more;
      if (recycled == null) {
        root = row();
        root.setPadding(dp(2), dp(8), 0, dp(8));
        icon = new ImageView(activity);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setBackground(Ui.background(0xffeaf3fc, 14, activity));
        root.addView(icon, lp(48, 48));
        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, dp(4), 0);
        title = text("", 14, Ui.INK);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        detail = text("", 10, Ui.MUTED);
        detail.setSingleLine();
        detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setPadding(0, dp(5), 0, 0);
        labels.addView(title);
        labels.addView(detail);
        root.addView(labels, new LayoutParams(0, -2, 1));
        more = Ui.icon(activity, "more", "Options du fichier", false);
        more.setFocusable(false);
        root.addView(more, lp(43, 48));
        root.setTag(new View[] {icon, title, detail, more});
      } else {
        root = (LinearLayout) recycled;
        View[] children = (View[]) root.getTag();
        icon = (ImageView) children[0];
        title = (TextView) children[1];
        detail = (TextView) children[2];
        more = (ImageButton) children[3];
      }
      Row item = rows.get(position);
      icon.setImageDrawable(new Ui.Glyph(item.icon, Ui.BLUE));
      title.setText(item.title);
      detail.setText(item.detail);
      more.setOnClickListener(v -> rowActions(item, more));
      return root;
    }
  }

  void scan() {
    if (scanning || destroyed) return;
    if (skin != null) skin.scanStarted();
    scanning = true;
    scanStatus.setVisibility(VISIBLE);
    scanStatus.setText("Scan des médias…");
    jobs.execute(
        () -> {
          try {
            MediaScanner.Result result =
                new MediaScanner(activity, library)
                    .scan(
                        message ->
                            handler.post(
                                () -> {
                                  if (!destroyed) scanStatus.setText(message);
                                }));
            handler.post(
                () -> {
                  if (destroyed) return;
                  lastScan = SystemClock.elapsedRealtime();
                  if (skin != null) skin.scanFinished(result);
                  scanStatus.setText(
                      result.audio
                          + " audio · "
                          + result.video
                          + " vidéos"
                          + (result.unavailable.isEmpty() ? "" : " · Certains accès sont limités"));
                  reload();
                });
          } catch (Exception failure) {
            handler.post(
                () -> {
                  if (!destroyed)
                    scanStatus.setText("Scan incomplet. Vérifiez les accès dans Profil.");
                });
          } finally {
            scanning = false;
          }
        });
  }

  void imported(List<Uri> uris, boolean privateImport) {
    if (privateImport) {
      withVault(
          () -> {
            new AlertDialog.Builder(activity)
                .setTitle("Déplacer dans le coffre")
                .setMessage(
                    "Les vidéos et images seront chiffrées et vérifiées dans RAKOTO, puis leurs"
                        + " originaux seront retirés de la galerie. Android pourra demander votre"
                        + " accord. Vous pourrez les restaurer depuis le coffre. Restaurez vos"
                        + " fichiers avant de désinstaller RAKOTO : la désinstallation efface le"
                        + " coffre.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton(
                    "Chiffrer et déplacer",
                    (dialog, which) ->
                        job(
                            "Import dans le coffre",
                            () ->
                                vault.importMedia(
                                    uris, (stage, done, total) -> progress(stage, done, total)),
                            entries -> {
                              reload();
                              actions.completeVaultMove(entries);
                            },
                            true))
                .show();
          });
      return;
    }
    job(
        "Ajout des fichiers",
        () -> {
          List<MediaEntry> entries = new ArrayList<>();
          for (Uri uri : uris) {
            MediaEntry e = MediaEntry.read(activity, uri);
            MediaScanner.duration(activity, e);
            entries.add(e);
          }
          library.addAll(entries);
          return entries;
        },
        entries -> {
          reload();
          if (!entries.isEmpty()) actions.play(entries, 0);
        },
        false);
  }

  void progress(String stage, long done, long total) {
    handler.post(
        () -> {
          if (!destroyed && operation != null) {
            operation.setMessage(
                stage.split(" · ")[0]
                    + " · "
                    + bytes(done)
                    + (total > 0 ? " / " + bytes(total) : ""));
            operation.setIndeterminate(total <= 0);
            if (total > 0) {
              operation.setMax(1000);
              operation.setProgress((int) Math.min(1000, done * 1000 / total));
            }
          }
        });
  }

  <T> void job(String title, Work<T> work, Done<T> done, boolean progress) {
    if (busy) {
      toast("Une opération est déjà en cours.");
      return;
    }
    busy = true;
    operation = new ProgressDialog(activity);
    operation.setTitle(title);
    operation.setMessage("Préparation…");
    operation.setIndeterminate(true);
    operation.setProgressStyle(
        progress ? ProgressDialog.STYLE_HORIZONTAL : ProgressDialog.STYLE_SPINNER);
    operation.setCancelable(false);
    if (tab == 2) operation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    operation.show();
    jobs.execute(
        () -> {
          try {
            T value = work.run();
            ParcelFileDescriptor fd =
                value instanceof ParcelFileDescriptor ? (ParcelFileDescriptor) value : null;
            if (fd != null) pendingDescriptors.add(fd);
            if (destroyed) {
              if (fd != null) {
                pendingDescriptors.remove(fd);
                try {
                  fd.close();
                } catch (IOException ignored) {
                }
              }
              return;
            }
            handler.post(
                () -> {
                  if (fd != null) pendingDescriptors.remove(fd);
                  busy = false;
                  if (operation != null) {
                    operation.dismiss();
                    operation = null;
                  }
                  if (!destroyed) done.accept(value);
                  else if (fd != null)
                    try {
                      fd.close();
                    } catch (IOException ignored) {
                    }
                });
          } catch (Exception failure) {
            handler.post(
                () -> {
                  busy = false;
                  if (operation != null) {
                    operation.dismiss();
                    operation = null;
                  }
                  if (!destroyed) {
                    toast(
                        failure.getMessage() == null
                            ? "Opération impossible"
                            : failure.getMessage());
                    reload();
                  }
                });
          }
        });
  }

  EditText pinField(String hint) {
    EditText field = new EditText(activity);
    field.setSingleLine();
    field.setHint(hint);
    field.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    field.setTransformationMethod(PasswordTransformationMethod.getInstance());
    field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});
    return field;
  }

  void withVault(Runnable next) {
    if (vault.unlocked()) {
      next.run();
      return;
    }
    pin(false, next);
  }

  void pin(boolean replace, Runnable next) {
    if (skin != null) {
      skin.pin(replace, next);
      return;
    }
    if (pinDialog != null && pinDialog.isShowing()) return;
    boolean create = replace || !vault.configured();
    actions.secure(true);
    LinearLayout panel = column();
    panel.setPadding(dp(24), dp(8), dp(24), dp(8));
    EditText first = pinField(create ? "Nouveau code à 4 chiffres" : "Code à 4 chiffres"),
        second = pinField("Confirmer le code");
    panel.addView(first, lp(-1, 56));
    if (create) panel.addView(second, lp(-1, 56));
    TextView status =
        text(
            create
                ? "Choisissez quatre chiffres et confirmez-les."
                : "Votre code reste sur cet appareil.",
            12,
            Ui.MUTED);
    panel.addView(status);
    final boolean[] running = {false};
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle(create ? "Créer votre code" : "Déverrouiller le coffre")
            .setView(panel)
            .setNegativeButton("Annuler", null)
            .setPositiveButton(create ? "Enregistrer" : "Ouvrir", null)
            .create();
    pinDialog = dialog;
    dialog.setOnDismissListener(
        d -> {
          if (running[0]) vault.lock();
          pinDialog = null;
          actions.secure(tab == 2);
        });
    dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    dialog.setOnShowListener(
        d -> {
          first.requestFocus();
          dialog
              .getWindow()
              .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
          dialog
              .getButton(-1)
              .setOnClickListener(
                  v -> {
                    String typed = first.getText().toString();
                    if (!typed.matches("[0-9]{4}")) {
                      first.setError("4 chiffres sont nécessaires");
                      return;
                    }
                    if (create && !typed.contentEquals(second.getText())) {
                      second.setError("Les codes ne correspondent pas");
                      return;
                    }
                    char[] code = typed.toCharArray();
                    first.getText().clear();
                    second.getText().clear();
                    running[0] = true;
                    dialog.getButton(-1).setEnabled(false);
                    dialog.setCancelable(false);
                    dialog.getButton(-2).setEnabled(false);
                    status.setText(create ? "Protection du coffre…" : "Vérification du code…");
                    jobs.execute(
                        () -> {
                          try {
                            if (replace) vault.changePin(code);
                            else if (create) vault.create(code);
                            else vault.unlock(code);
                            handler.post(
                                () -> {
                                  running[0] = false;
                                  if (destroyed || !dialog.isShowing()) return;
                                  dialog.dismiss();
                                  next.run();
                                });
                          } catch (Exception failure) {
                            handler.post(
                                () -> {
                                  running[0] = false;
                                  if (!destroyed && dialog.isShowing()) {
                                    dialog.getButton(-1).setEnabled(true);
                                    dialog.setCancelable(true);
                                    dialog.getButton(-2).setEnabled(true);
                                    status.setText(
                                        failure.getMessage() == null
                                            ? "Code non accepté"
                                            : failure.getMessage());
                                  }
                                });
                          } finally {
                            Arrays.fill(code, '\0');
                          }
                        });
                  });
        });
    dialog.show();
  }

  void changePin() {
    if (!vault.configured()) {
      pin(
          false,
          () -> {
            if (tab != 2) vault.lock();
            reload();
          });
      return;
    }
    withVault(
        () ->
            pin(
                true,
                () -> {
                  toast("Code modifié");
                  if (tab != 2) vault.lock();
                  reload();
                }));
  }

  void lockVault() {
    vault.lock();
    if (skin != null) skin.vaultLocked();
    loadVersion++;
    for (AlertDialog dialog : new ArrayList<>(privateDialogs)) dialog.dismiss();
    if (tab == 2) {
      rows = new ArrayList<>();
      adapter.notifyDataSetChanged();
    }
    actions.locked();
  }

  void pause(boolean externalPicker) {
    active = false;
    handler.removeCallbacks(mediaChanged);
    if (!externalPicker) {
      lockVault();
      if (pinDialog != null) pinDialog.dismiss();
      if (tab == 2) reload();
    } else handler.postDelayed(expirePicker, 120000);
  }

  final Runnable expirePicker =
      () -> {
        lockVault();
        if (tab == 2) reload();
      };

  void resume() {
    active = true;
    handler.removeCallbacks(expirePicker);
    String access = access();
    if (library.preferences.getBoolean("scan_auto", true)
        && (scanPending
            || !access.equals(lastAccess)
            || SystemClock.elapsedRealtime() - lastScan > 30000)) {
      lastAccess = access;
      scanPending = false;
      scan();
    }
    reload();
  }

  void destroy() {
    destroyed = true;
    if (skin != null) skin.destroy();
    lockVault();
    handler.removeCallbacksAndMessages(null);
    activity.getContentResolver().unregisterContentObserver(observer);
    data.shutdownNow();
    jobs.shutdownNow();
    synchronized (pendingDescriptors) {
      for (ParcelFileDescriptor fd : pendingDescriptors)
        try {
          fd.close();
        } catch (IOException ignored) {
        }
      pendingDescriptors.clear();
    }
    if (operation != null) operation.dismiss();
    if (pinDialog != null) pinDialog.dismiss();
  }

  String access() {
    if (Build.VERSION.SDK_INT >= 33) {
      boolean
          audio =
              activity.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
                  == PackageManager.PERMISSION_GRANTED,
          video =
              activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)
                  == PackageManager.PERMISSION_GRANTED,
          partial =
              Build.VERSION.SDK_INT >= 34
                  && activity.checkSelfPermission(
                          Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                      == PackageManager.PERMISSION_GRANTED;
      return "Audio : "
          + (audio ? "autorisé" : "non autorisé")
          + "\nVidéos : "
          + (video ? "accès complet" : partial ? "sélection autorisée" : "non autorisé");
    }
    return activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ? "Accès aux médias autorisé"
        : "Accès aux médias non autorisé";
  }

  void profile() {
    if (skin != null) {
      skin.settings();
      return;
    }
    list.setVisibility(GONE);
    emptyBox.setVisibility(GONE);
    for (int i = body.getChildCount() - 1; i >= 0; i--) {
      View child = body.getChildAt(i);
      if (child != list && child != emptyBox) body.removeViewAt(i);
    }
    ScrollView scroll = new ScrollView(activity);
    LinearLayout panel = column();
    panel.setPadding(0, dp(10), 0, dp(25));
    scroll.addView(panel);
    body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    TextView name = text(library.preferences.getString("profile_name", "Mon espace"), 27, Ui.NAVY);
    name.setTypeface(null, Typeface.BOLD);
    panel.addView(name);
    TextView local = text("Un profil local. Aucun compte en ligne.", 12, Ui.MUTED);
    local.setPadding(0, dp(7), 0, dp(16));
    panel.addView(local);
    profileButton(
        panel,
        "Modifier mon nom",
        () -> {
          EditText input = new EditText(activity);
          input.setSingleLine();
          input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(40)});
          input.setText(library.preferences.getString("profile_name", "Mon espace"));
          new AlertDialog.Builder(activity)
              .setTitle("Nom du profil")
              .setView(input)
              .setNegativeButton("Annuler", null)
              .setPositiveButton(
                  "Enregistrer",
                  (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty())
                      library.preferences.edit().putString("profile_name", value).apply();
                    reload();
                  })
              .show();
        });
    TextView permissions = text("ACCÈS AUX FICHIERS\n\n" + access(), 12, Ui.MUTED);
    permissions.setPadding(0, dp(22), 0, dp(12));
    panel.addView(permissions);
    profileButton(panel, "Autoriser / modifier le scan", actions::requestScan);
    profileButton(panel, "Permissions dans Android", actions::systemSettings);
    profileButton(panel, "Scanner maintenant", this::scan);
    profileButton(panel, "Ajouter un dossier à scanner", actions::pickFolder);
    TextView scope =
        text(
            "Tous les fichiers audio indexés sont recherchés, y compris les enregistrements et"
                + " sonneries. Ajoutez un dossier pour les fichiers non indexés et les cartes SD"
                + " autorisées. Les dossiers privés d’autres applications restent protégés par"
                + " Android.\n\n"
                + "Dossiers ajoutés : "
                + library.folders().size(),
            11,
            Ui.MUTED);
    scope.setPadding(0, dp(12), 0, dp(20));
    panel.addView(scope);
    profileButton(panel, "Réglages du lecteur", actions::settings);
    profileButton(panel, "Changer le code du coffre", this::changePin);
    TextView vaultNote =
        text(
            "Le coffre conserve des copies chiffrées. Les originaux restent à leur emplacement. Le"
                + " coffre se verrouille lorsque vous quittez l’application. Conservez vos"
                + " originaux : la désinstallation efface le coffre.",
            11,
            Ui.MUTED);
    vaultNote.setPadding(0, dp(14), 0, dp(14));
    panel.addView(vaultNote);
    TextView version = text("RAKOTO Player · Android\n1.0.0 bêta 2 · LibVLC 3.7.7", 11, Ui.NAVY);
    panel.addView(version);
  }

  void profileButton(LinearLayout panel, String label, Runnable action) {
    Button button = Ui.button(activity, label, false);
    LayoutParams p = lp(-1, 48);
    p.bottomMargin = dp(8);
    panel.addView(button, p);
    button.setOnClickListener(v -> action.run());
  }
}
