package com.rakotoplayer.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.util.Rational;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.VLCVideoLayout;

public class MainActivity extends Activity {
  private static final int OPEN_MEDIA = 101, OPEN_SUBTITLE = 102;
  private PlaybackService service;
  private Library library;
  private boolean bound, visible, playerVisible, fullscreen, menuOpen, seeking, attached, pip;
  private int selectedVideo = -1, filter = 0;
  private String query = "", artUri = "", lastError = "";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService artworkWorker = Executors.newSingleThreadExecutor();
  private final PlaybackService.Listener listener = this::refresh;
  private final Runnable hideControls =
      () -> {
        if (fullscreen && !menuOpen && !seeking && !pip)
          this.controls.setVisibility(View.INVISIBLE);
      };
  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (visible) {
            refreshTimeline();
            handler.postDelayed(this, 500);
          }
        }
      };
  private LinearLayout root, libraryPage, drawer, controls, playerToolbar, audioContent;
  private FrameLayout libraryLandscape;
  private FrameLayout content, playerPage, audioPlaceholder;
  private VLCVideoLayout video;
  private ImageView album, drawerArt;
  private TextView trackTitle,
      artist,
      drawerTitle,
      drawerArtist,
      time,
      duration,
      trackSelection,
      empty;
  private ImageButton play, miniPlay;
  private SeekBar timeline, drawerProgress;
  private ListView list;
  private List<MediaEntry> displayed = new ArrayList<>();
  private MediaAdapter adapter;
  private Intent pendingIntent;
  private int insetLeft, insetTop, insetRight, insetBottom;
  private final ServiceConnection connection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
          service = ((PlaybackService.LocalBinder) binder).service();
          bound = true;
          if (visible) service.listen(listener);
          if (playerVisible) attachVideo();
          handleIntent(pendingIntent);
          pendingIntent = null;
          refresh();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
          service = null;
          bound = false;
          attached = false;
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    library = new Library(this);
    setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
    buildUi();
    adaptLayout();
    pendingIntent = getIntent();
    bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE);
    if (Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
  }

  private int dp(float value) {
    return Ui.dp(this, value);
  }

  private LinearLayout row() {
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.HORIZONTAL);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  private LinearLayout column() {
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.VERTICAL);
    return view;
  }

  private LinearLayout.LayoutParams lp(int width, int height) {
    return new LinearLayout.LayoutParams(
        width < 0 ? width : dp(width), height < 0 ? height : dp(height));
  }

  private void space(LinearLayout view, int width) {
    view.addView(new View(this), lp(width, 1));
  }

  private TextView text(String value, float size, int color) {
    return Ui.text(this, value, size, color);
  }

  private void click(View view, Runnable action) {
    view.setOnClickListener(
        v -> {
          if (library.preferences.getBoolean("motion", true)
              && Settings.Global.getFloat(
                      getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                  > 0) {
            v.animate().cancel();
            v.animate()
                .scaleX(.95f)
                .scaleY(.95f)
                .setDuration(80)
                .withEndAction(
                    () ->
                        v.animate()
                            .scaleX(1)
                            .scaleY(1)
                            .setDuration(180)
                            .setInterpolator(new OvershootInterpolator(1f))
                            .start())
                .start();
          }
          action.run();
        });
  }

  private void buildUi() {
    root = column();
    root.setBackgroundColor(Ui.PAPER);
    setContentView(root);
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    root.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            insetLeft = bars.left;
            insetTop = bars.top;
            insetRight = bars.right;
            insetBottom = bars.bottom;
          } else {
            insetLeft = insets.getSystemWindowInsetLeft();
            insetTop = insets.getSystemWindowInsetTop();
            insetRight = insets.getSystemWindowInsetRight();
            insetBottom = insets.getSystemWindowInsetBottom();
          }
          applyInsets();
          return insets;
        });
    content = new FrameLayout(this);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    buildLibrary();
    buildPlayer();
    buildDrawer();
    root.requestApplyInsets();
    reloadLibrary();
  }

  private void buildLibrary() {
    libraryPage = column();
    libraryPage.setPadding(dp(18), 0, dp(18), 0);
    content.addView(libraryPage, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout header = row();
    header.setPadding(0, dp(10), 0, dp(10));
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.rakoto_logo);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    header.addView(logo, lp(63, 43));
    space(header, 9);
    TextView brand = text("RAKOTO\nPLAYER", 17, Ui.NAVY);
    brand.setTypeface(null, Typeface.BOLD);
    brand.setLineSpacing(0, .9f);
    header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
    ImageButton settings = Ui.icon(this, "more", "Paramètres et informations", false);
    header.addView(settings, lp(48, 48));
    click(settings, this::settings);
    libraryPage.addView(header, lp(-1, 72));
    FrameLayout landscape = new FrameLayout(this);
    libraryLandscape = landscape;
    landscape.setClipToOutline(true);
    landscape.setBackground(Ui.background(Ui.SOFT, 20, this));
    landscape.addView(new Ui.Landscape(this), new FrameLayout.LayoutParams(-1, -1));
    LinearLayout intro = column();
    intro.setPadding(dp(18), dp(17), dp(18), 0);
    TextView heading = text("Votre musique.\nVotre liberté.", 24, Ui.NAVY);
    heading.setTypeface(null, Typeface.BOLD);
    intro.addView(heading);
    TextView subtitle = text("Vos fichiers, sur votre téléphone.", 12, Ui.NAVY);
    subtitle.setPadding(0, dp(8), 0, 0);
    intro.addView(subtitle);
    landscape.addView(intro, new FrameLayout.LayoutParams(-1, -1));
    libraryPage.addView(landscape, lp(-1, 132));
    LinearLayout actions = row();
    actions.setPadding(0, dp(14), 0, dp(10));
    TextView title = text("Votre bibliothèque", 19, Ui.INK);
    title.setTypeface(null, Typeface.BOLD);
    actions.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));
    title.setGravity(Gravity.CENTER_VERTICAL);
    Button add = Ui.button(this, "Importer", true);
    actions.addView(add, lp(-2, 46));
    click(add, this::pickMedia);
    libraryPage.addView(actions);
    EditText search = new EditText(this);
    search.setSingleLine();
    search.setTextSize(14);
    search.setHint("Rechercher un titre ou un artiste");
    search.setPadding(dp(13), 0, dp(13), 0);
    search.setBackground(Ui.background(Color.WHITE, 14, this));
    search.setTextColor(Ui.INK);
    libraryPage.addView(search, lp(-1, 48));
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            query = s.toString();
            reloadLibrary();
          }

          public void afterTextChanged(Editable e) {}
        });
    LinearLayout tabs = row();
    tabs.setPadding(0, dp(9), 0, dp(7));
    String[] names = {"Tout", "Musique", "Vidéos", "Favoris"};
    for (int i = 0; i < names.length; i++) {
      final int selected = i;
      Button tab = Ui.button(this, names[i], false);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(43), 1);
      params.setMargins(0, 0, dp(i < 3 ? 5 : 0), 0);
      tabs.addView(tab, params);
      click(
          tab,
          () -> {
            filter = selected;
            for (int n = 0; n < tabs.getChildCount(); n++) {
              View child = tabs.getChildAt(n);
              child.setBackground(Ui.background(n == selected ? 0xffd7e9ff : Ui.SOFT, 14, this));
            }
            reloadLibrary();
          });
    }
    libraryPage.addView(tabs);
    FrameLayout listHolder = new FrameLayout(this);
    libraryPage.addView(listHolder, new LinearLayout.LayoutParams(-1, 0, 1));
    list = new ListView(this);
    list.setDivider(null);
    list.setCacheColorHint(Color.TRANSPARENT);
    adapter = new MediaAdapter();
    list.setAdapter(adapter);
    listHolder.addView(list, new FrameLayout.LayoutParams(-1, -1));
    empty =
        text(
            "Votre univers commence ici.\n\n"
                + "Importez vos musiques et vidéos.\n"
                + "Les fichiers restent à leur emplacement.",
            15,
            Ui.MUTED);
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(dp(20), dp(18), dp(20), dp(18));
    listHolder.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    list.setEmptyView(empty);
    list.setOnItemClickListener(
        (parent, view, position, id) -> playEntries(new ArrayList<>(displayed), position));
    list.setOnItemLongClickListener(
        (parent, view, position, id) -> {
          mediaActions(displayed.get(position));
          return true;
        });
  }

  private void buildPlayer() {
    playerPage = new FrameLayout(this);
    playerPage.setBackgroundColor(0xff040b15);
    playerPage.setVisibility(View.GONE);
    content.addView(playerPage, new FrameLayout.LayoutParams(-1, -1));
    video = new VLCVideoLayout(this);
    playerPage.addView(video, new FrameLayout.LayoutParams(-1, -1));
    audioPlaceholder = new FrameLayout(this);
    audioPlaceholder.setBackgroundColor(Ui.PAPER);
    playerPage.addView(audioPlaceholder, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout audio = column();
    audioContent = audio;
    audio.setGravity(Gravity.CENTER);
    audio.setPadding(dp(28), dp(65), dp(28), dp(220));
    audioPlaceholder.addView(audio, new FrameLayout.LayoutParams(-1, -1));
    album = new ImageView(this);
    album.setImageResource(R.drawable.rakoto_logo);
    album.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    album.setBackground(Ui.background(Ui.SOFT, 24, this));
    album.setClipToOutline(true);
    audio.addView(album, lp(220, 220));
    trackTitle = text("RAKOTO Player", 23, Ui.NAVY);
    trackTitle.setGravity(Gravity.CENTER);
    trackTitle.setTypeface(null, Typeface.BOLD);
    trackTitle.setMaxLines(2);
    trackTitle.setPadding(0, dp(22), 0, 0);
    audio.addView(trackTitle, lp(-1, -2));
    artist = text("Vos médias locaux", 14, Ui.MUTED);
    artist.setGravity(Gravity.CENTER);
    artist.setPadding(0, dp(7), 0, 0);
    audio.addView(artist);
    playerToolbar = row();
    playerToolbar.setPadding(dp(12), dp(8), dp(12), 0);
    FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP);
    playerPage.addView(playerToolbar, toolbarParams);
    ImageButton back = Ui.icon(this, "back", "Revenir à la bibliothèque", false);
    playerToolbar.addView(back, lp(48, 48));
    click(back, () -> showLibrary());
    space(playerToolbar, 8);
    TextView label = text("RAKOTO / LECTURE", 11, 0xff6d9dc3);
    playerToolbar.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton full = Ui.icon(this, "expand", "Activer ou quitter le plein écran", false);
    playerToolbar.addView(full, lp(48, 48));
    click(
        full,
        () -> {
          if (service != null && service.hasVideo) fullscreen(!fullscreen);
          else toast("Le plein écran immersif est disponible pour une vidéo.");
        });
    controls = column();
    controls.setBackground(Ui.background(0xf0eff6ff, 23, this));
    controls.setPadding(dp(13), dp(9), dp(13), dp(10));
    FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    controlsParams.setMargins(dp(12), 0, dp(12), dp(12));
    playerPage.addView(controls, controlsParams);
    LinearLayout seek = row();
    time = text("0:00", 11, Ui.MUTED);
    duration = text("0:00", 11, Ui.MUTED);
    timeline = new SeekBar(this);
    timeline.setMax(10000);
    seek.addView(time, lp(44, 40));
    seek.addView(timeline, new LinearLayout.LayoutParams(0, dp(40), 1));
    seek.addView(duration, lp(48, 40));
    controls.addView(seek);
    timeline.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar bar) {
            seeking = true;
            showControls();
          }

          public void onProgressChanged(SeekBar bar, int position, boolean user) {
            if (user && service != null)
              time.setText(Ui.time(service.player.getLength() * position / 10000));
          }

          public void onStopTrackingTouch(SeekBar bar) {
            if (service != null)
              service.seek(service.player.getLength() * bar.getProgress() / 10000);
            seeking = false;
            showControls();
          }
        });
    LinearLayout transport = row();
    transport.setGravity(Gravity.CENTER);
    ImageButton prev = Ui.icon(this, "previous", "Précédent", false);
    play = Ui.icon(this, "play", "Lire ou mettre en pause", true);
    ImageButton next = Ui.icon(this, "next", "Suivant", false);
    transport.addView(prev, lp(52, 52));
    space(transport, 19);
    transport.addView(play, lp(60, 60));
    space(transport, 19);
    transport.addView(next, lp(52, 52));
    controls.addView(transport, lp(-1, 66));
    click(
        prev,
        () -> {
          if (service != null) service.next(-1);
          showControls();
        });
    click(
        play,
        () -> {
          if (service != null) service.toggle();
          showControls();
        });
    click(
        next,
        () -> {
          if (service != null) service.next(1);
          showControls();
        });
    trackSelection = text("Pistes détectées dans le fichier", 10, Ui.MUTED);
    trackSelection.setGravity(Gravity.CENTER);
    trackSelection.setPadding(0, dp(4), 0, dp(5));
    trackSelection.setMaxLines(2);
    controls.addView(trackSelection, lp(-1, -2));
    LinearLayout options = row();
    String[] labels = {"Audio", "Sous-titres", "Options"};
    for (int i = 0; i < 3; i++) {
      final int action = i;
      Button button = Ui.button(this, labels[i], false);
      button.setTextSize(11);
      options.addView(button, new LinearLayout.LayoutParams(0, dp(43), 1));
      click(
          button,
          () -> {
            showControls();
            if (action == 0) tracks(false);
            else if (action == 1) subtitles();
            else options(button);
          });
    }
    controls.addView(options);
    GestureDetector gestures =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(android.view.MotionEvent event) {
                return true;
              }

              @Override
              public boolean onSingleTapConfirmed(android.view.MotionEvent event) {
                if (fullscreen && controls.getVisibility() == View.VISIBLE && !menuOpen)
                  controls.setVisibility(View.INVISIBLE);
                else showControls();
                return true;
              }

              @Override
              public boolean onDoubleTap(android.view.MotionEvent event) {
                if (service != null && service.hasVideo) fullscreen(!fullscreen);
                return true;
              }
            });
    video.setOnTouchListener((view, event) -> gestures.onTouchEvent(event));
    playerPage.setOnTouchListener((view, event) -> gestures.onTouchEvent(event));
  }

  private void buildDrawer() {
    drawer = row();
    drawer.setPadding(dp(10), dp(7), dp(8), dp(7));
    drawer.setBackground(Ui.background(0xffe5f1fc, 21, this));
    LinearLayout.LayoutParams params = lp(-1, 76);
    params.setMargins(dp(12), dp(5), dp(12), dp(8));
    root.addView(drawer, params);
    drawer.setVisibility(View.GONE);
    drawerArt = new ImageView(this);
    drawerArt.setImageResource(R.drawable.rakoto_logo);
    drawerArt.setBackground(Ui.background(Color.WHITE, 12, this));
    drawerArt.setClipToOutline(true);
    drawerArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    drawer.addView(drawerArt, lp(49, 49));
    space(drawer, 10);
    LinearLayout labels = column();
    drawerTitle = text("RAKOTO Player", 13, Ui.NAVY);
    drawerTitle.setTypeface(null, Typeface.BOLD);
    drawerTitle.setSingleLine();
    drawerTitle.setEllipsize(TextUtils.TruncateAt.END);
    drawerArtist = text("Fichier local", 10, Ui.MUTED);
    drawerArtist.setSingleLine();
    drawerArtist.setEllipsize(TextUtils.TruncateAt.END);
    labels.addView(drawerTitle);
    labels.addView(drawerArtist);
    drawerProgress = new SeekBar(this);
    drawerProgress.setMax(10000);
    drawerProgress.setEnabled(false);
    drawerProgress.setPadding(0, 0, 0, 0);
    drawerProgress.setThumb(null);
    labels.addView(drawerProgress, lp(-1, 12));
    drawer.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    miniPlay = Ui.icon(this, "play", "Lire ou mettre en pause", true);
    drawer.addView(miniPlay, lp(46, 46));
    ImageButton next = Ui.icon(this, "next", "Suivant", false);
    drawer.addView(next, lp(43, 46));
    click(
        miniPlay,
        () -> {
          if (service != null) service.toggle();
        });
    click(
        next,
        () -> {
          if (service != null) service.next(1);
        });
    click(labels, this::showPlayer);
    click(drawerArt, this::showPlayer);
  }

  private void reloadLibrary() {
    displayed = new ArrayList<>();
    if (library == null) return;
    String term = query.toLowerCase(Locale.ROOT);
    for (MediaEntry entry : library.entries()) {
      if (filter == 1 && entry.video
          || filter == 2 && !entry.video
          || filter == 3 && !library.favorite(entry.uri)) continue;
      if (!term.isEmpty()
          && !(entry.title + " " + entry.artist + " " + entry.name)
              .toLowerCase(Locale.ROOT)
              .contains(term)) continue;
      displayed.add(entry);
    }
    if (adapter != null) adapter.notifyDataSetChanged();
  }

  private class MediaAdapter extends BaseAdapter {
    public int getCount() {
      return displayed.size();
    }

    public Object getItem(int position) {
      return displayed.get(position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View recycled, android.view.ViewGroup parent) {
      LinearLayout row;
      ImageView icon;
      TextView title, subtitle;
      if (recycled == null) {
        row = MainActivity.this.row();
        row.setPadding(dp(7), dp(8), dp(7), dp(8));
        icon = new ImageView(MainActivity.this);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(Ui.background(Ui.SOFT, 13, MainActivity.this));
        row.addView(icon, lp(48, 48));
        space(row, 12);
        LinearLayout labels = column();
        title = text("", 14, Ui.INK);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        subtitle = text("", 11, Ui.MUTED);
        subtitle.setPadding(0, dp(5), 0, 0);
        subtitle.setSingleLine();
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.setTag(new View[] {icon, title, subtitle});
      } else {
        row = (LinearLayout) recycled;
        View[] values = (View[]) row.getTag();
        icon = (ImageView) values[0];
        title = (TextView) values[1];
        subtitle = (TextView) values[2];
      }
      MediaEntry entry = displayed.get(position);
      icon.setImageDrawable(new Ui.Glyph(entry.video ? "video" : "music", Ui.BLUE));
      title.setText((library.favorite(entry.uri) ? "♥ " : "") + entry.title);
      subtitle.setText(entry.artist + " · " + (entry.video ? "Vidéo" : "Audio / média"));
      return row;
    }
  }

  private void pickMedia() {
    Intent intent =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    startActivityForResult(intent, OPEN_MEDIA);
  }

  private void pickSubtitle() {
    Intent intent =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    startActivityForResult(intent, OPEN_SUBTITLE);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) return;
    List<Uri> values = new ArrayList<>();
    if (data.getClipData() != null) {
      for (int i = 0; i < data.getClipData().getItemCount(); i++)
        values.add(data.getClipData().getItemAt(i).getUri());
    } else if (data.getData() != null) values.add(data.getData());
    if (request == OPEN_MEDIA) {
      List<MediaEntry> imported = new ArrayList<>();
      for (Uri uri : values) {
        try {
          if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
            getContentResolver()
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        imported.add(MediaEntry.read(this, uri));
      }
      library.addAll(imported);
      reloadLibrary();
      if (!imported.isEmpty()) playEntries(imported, 0);
    } else if (request == OPEN_SUBTITLE) loadSubtitles(values);
  }

  void playEntries(List<MediaEntry> entries, int index) {
    if (service == null) {
      toast("Le moteur se prépare. Réessayez dans un instant.");
      return;
    }
    Intent foreground = new Intent(this, PlaybackService.class).setAction(PlaybackService.PLAY);
    if (Build.VERSION.SDK_INT >= 26) startForegroundService(foreground);
    else startService(foreground);
    showPlayer();
    service.open(entries, index);
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        && !library.preferences.getBoolean("notifications_asked", false)) {
      library.preferences.edit().putBoolean("notifications_asked", true).apply();
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 103);
    }
  }

  private void mediaActions(MediaEntry entry) {
    new AlertDialog.Builder(this)
        .setTitle(entry.title)
        .setItems(
            new String[] {
              library.favorite(entry.uri) ? "Retirer des favoris" : "Ajouter aux favoris",
              "Informations du fichier",
              "Retirer de la bibliothèque"
            },
            (dialog, which) -> {
              if (which == 0) {
                library.toggleFavorite(entry.uri);
                reloadLibrary();
              } else if (which == 1)
                new AlertDialog.Builder(this)
                    .setTitle("Fichier local")
                    .setMessage(
                        entry.name
                            + "\n"
                            + (entry.size > 0
                                ? String.format(Locale.FRANCE, "%.2f Mo", entry.size / 1000000.0)
                                : "Taille non renseignée")
                            + "\n"
                            + entry.mime)
                    .setPositiveButton("Fermer", null)
                    .show();
              else {
                library.remove(entry.uri);
                reloadLibrary();
              }
            })
        .show();
  }

  private void showPlayer() {
    playerVisible = true;
    libraryPage.setVisibility(View.GONE);
    playerPage.setVisibility(View.VISIBLE);
    drawer.setVisibility(View.GONE);
    attachVideo();
    refresh();
    showControls();
  }

  private void showLibrary() {
    fullscreen(false);
    detachVideo();
    playerVisible = false;
    libraryPage.setVisibility(View.VISIBLE);
    playerPage.setVisibility(View.GONE);
    reloadLibrary();
    refresh();
  }

  private void attachVideo() {
    if (service == null || attached || !visible) return;
    service.player.attachViews(video, null, true, false);
    attached = true;
    restoreVideoTrack();
  }

  private void restoreVideoTrack() {
    if (service == null || !attached || !service.hasVideo) return;
    int first = -1, chosen = -1;
    for (org.videolan.libvlc.MediaPlayer.TrackDescription track : service.videoTracks())
      if (track.id >= 0) {
        if (first < 0) first = track.id;
        if (track.id == selectedVideo) chosen = track.id;
      }
    if (first >= 0) service.player.setVideoTrack(chosen >= 0 ? chosen : first);
  }

  private void detachVideo() {
    if (service == null || !attached) return;
    if (service.hasVideo) {
      selectedVideo = service.player.getVideoTrack();
      service.player.setVideoTrack(-1);
    }
    service.player.detachViews();
    attached = false;
  }

  private void refresh() {
    if (service == null) return;
    MediaEntry current = service.current;
    if (current != null) {
      trackTitle.setText(current.title);
      artist.setText(current.artist);
      drawerTitle.setText(current.title);
      drawerArtist.setText(service.loading ? "Chargement…" : current.artist);
      loadArtwork(current);
    }
    boolean playing = service.player.isPlaying();
    Ui.icon(play, playing ? "pause" : "play", true);
    Ui.icon(miniPlay, playing ? "pause" : "play", true);
    drawer.setVisibility(current != null && !playerVisible && !pip ? View.VISIBLE : View.GONE);
    audioPlaceholder.setVisibility(!service.hasVideo && !pip ? View.VISIBLE : View.GONE);
    trackSelection.setText(service.loading ? "Analyse du média…" : selectedTracks());
    if (!service.error.isEmpty() && !service.error.equals(lastError)) {
      lastError = service.error;
      toast(lastError);
    } else if (service.error.isEmpty()) lastError = "";
    if (playerVisible && attached && service.hasVideo && service.player.getVideoTrack() < 0)
      restoreVideoTrack();
    updatePip();
    refreshTimeline();
  }

  private void refreshTimeline() {
    if (service == null || seeking) return;
    long length = Math.max(0, service.player.getLength()),
        position = Math.max(0, service.player.getTime());
    int progress = length > 0 ? (int) Math.min(10000, position * 10000 / length) : 0;
    timeline.setProgress(progress);
    timeline.setEnabled(length > 0 && service.player.isSeekable());
    drawerProgress.setProgress(progress);
    time.setText(Ui.time(position));
    duration.setText(Ui.time(length));
  }

  private String selectedTracks() {
    String audio = "Audio désactivé", sub = "Sans sous-titres";
    if (service == null) return "";
    for (org.videolan.libvlc.MediaPlayer.TrackDescription t : service.audioTracks())
      if (t.id == service.player.getAudioTrack() && t.id >= 0) audio = trackLabel(t, false);
    for (org.videolan.libvlc.MediaPlayer.TrackDescription t : service.subtitleTracks())
      if (t.id == service.player.getSpuTrack() && t.id >= 0) sub = trackLabel(t, true);
    return audio + " · " + sub;
  }

  private String trackLabel(
      org.videolan.libvlc.MediaPlayer.TrackDescription description, boolean subtitle) {
    if (description.id < 0) return "Désactivé";
    for (IMedia.Track track : service.tracks())
      if (track.id == description.id
          && track.type == (subtitle ? IMedia.Track.Type.Text : IMedia.Track.Type.Audio)) {
        String language = language(track.language),
            codec = track.codec == null ? "" : track.codec.toUpperCase(Locale.ROOT).trim();
        String channels = "";
        if (track instanceof IMedia.AudioTrack) {
          int count = ((IMedia.AudioTrack) track).channels;
          channels = count == 6 ? "5.1" : count == 8 ? "7.1" : count > 0 ? count + " canaux" : "";
        }
        return language
            + (codec.isEmpty() ? "" : " · " + codec)
            + (channels.isEmpty() ? "" : " · " + channels);
      }
    return description.name == null ? "Piste " + description.id : description.name;
  }

  private String language(String value) {
    if (value == null || value.isEmpty() || value.equals("und")) return "Langue non renseignée";
    String[][] codes = {
      {"fra", "fr"},
      {"fre", "fr"},
      {"eng", "en"},
      {"jpn", "ja"},
      {"mlg", "mg"},
      {"deu", "de"},
      {"spa", "es"}
    };
    for (String[] pair : codes) if (pair[0].equals(value)) value = pair[1];
    String name = new Locale(value).getDisplayLanguage(Locale.FRENCH);
    return name.isEmpty()
        ? value
        : name.substring(0, 1).toUpperCase(Locale.FRENCH) + name.substring(1);
  }

  private void trackDialog(boolean subtitle) {
    if (service == null || service.current == null) {
      toast("Ouvrez un média d’abord.");
      return;
    }
    org.videolan.libvlc.MediaPlayer.TrackDescription[] tracks =
        subtitle ? service.subtitleTracks() : service.audioTracks();
    List<Integer> ids = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    int selected = -1,
        current = subtitle ? service.player.getSpuTrack() : service.player.getAudioTrack();
    for (org.videolan.libvlc.MediaPlayer.TrackDescription track : tracks) {
      ids.add(track.id);
      labels.add(trackLabel(track, subtitle));
      if (track.id == current) selected = ids.size() - 1;
    }
    if (!ids.contains(-1)) {
      ids.add(-1);
      labels.add("Désactivé");
      if (current < 0) selected = ids.size() - 1;
    }
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(subtitle ? "Sous-titres détectés" : "Pistes audio détectées")
            .setSingleChoiceItems(
                labels.toArray(new String[0]),
                selected,
                (d, which) -> {
                  boolean ok =
                      subtitle
                          ? service.player.setSpuTrack(ids.get(which))
                          : service.player.setAudioTrack(ids.get(which));
                  if (!ok) toast("La piste ne peut pas être activée pour ce média.");
                  d.dismiss();
                  refresh();
                })
            .setNegativeButton("Fermer", null)
            .create();
    hold(dialog);
    dialog.show();
  }

  private void tracks(boolean subtitles) {
    trackDialog(subtitles);
  }

  private void subtitles() {
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Sous-titres")
            .setItems(
                new String[] {
                  "Choisir une piste intégrée",
                  "Charger un fichier SRT / ASS / VTT / SUB / IDX",
                  "Décalage des sous-titres",
                  "Désactiver"
                },
                (d, which) -> {
                  if (which == 0) trackDialog(true);
                  else if (which == 1) pickSubtitle();
                  else if (which == 2) delay(true);
                  else if (service != null) {
                    service.player.setSpuTrack(-1);
                    refresh();
                  }
                })
            .create();
    hold(dialog);
    dialog.show();
  }

  private void options(View anchor) {
    if (service == null) return;
    PopupMenu popup = new PopupMenu(this, anchor);
    String[] labels = {
      "Vitesse de lecture",
      "Égaliseur à 10 bandes",
      "Volume",
      "Synchronisation audio",
      "Format de l’image",
      "Informations média",
      service.shuffle ? "Désactiver l’aléatoire" : "Activer l’aléatoire",
      "Répétition",
      "Ajouter / retirer des favoris",
      "Arrêter"
    };
    for (int i = 0; i < labels.length; i++) popup.getMenu().add(0, i, i, labels[i]);
    if (service.hasVideo
        && Build.VERSION.SDK_INT >= 26
        && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
      popup.getMenu().add(0, 10, 10, "Vidéo flottante (Picture-in-Picture)");
    popup.setOnMenuItemClickListener(
        item -> {
          switch (item.getItemId()) {
            case 0:
              speed();
              break;
            case 1:
              equalizer();
              break;
            case 2:
              volume();
              break;
            case 3:
              delay(false);
              break;
            case 4:
              aspect();
              break;
            case 5:
              mediaInfo();
              break;
            case 6:
              service.shuffle = !service.shuffle;
              library.preferences.edit().putBoolean("shuffle", service.shuffle).apply();
              toast(service.shuffle ? "Lecture aléatoire activée" : "Lecture aléatoire désactivée");
              break;
            case 7:
              repeat();
              break;
            case 8:
              if (service.current != null) {
                library.toggleFavorite(service.current.uri);
                reloadLibrary();
                toast("Favoris mis à jour");
              }
              break;
            case 9:
              service.stop();
              refresh();
              break;
            case 10:
              enterPip();
              break;
          }
          return true;
        });
    menuOpen = true;
    handler.removeCallbacks(hideControls);
    popup.setOnDismissListener(
        menu -> {
          menuOpen = false;
          showControls();
        });
    popup.show();
  }

  private void hold(AlertDialog dialog) {
    menuOpen = true;
    handler.removeCallbacks(hideControls);
    dialog.setOnDismissListener(
        d -> {
          menuOpen = false;
          showControls();
        });
  }

  private void speed() {
    float[] speeds = {.25f, .5f, .75f, 1f, 1.25f, 1.5f, 2f};
    String[] labels = {"0,25×", "0,5×", "0,75×", "1×", "1,25×", "1,5×", "2×"};
    int current = 3;
    for (int i = 0; i < speeds.length; i++)
      if (Math.abs(service.player.getRate() - speeds[i]) < .01) current = i;
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Vitesse")
            .setSingleChoiceItems(
                labels,
                current,
                (d, i) -> {
                  service.player.setRate(speeds[i]);
                  if (Math.abs(service.player.getRate() - speeds[i]) > .05f)
                    toast("Vitesse non disponible pour ce média.");
                  d.dismiss();
                })
            .create();
    hold(dialog);
    dialog.show();
  }

  private void repeat() {
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Répétition")
            .setSingleChoiceItems(
                new String[] {"Désactivée", "Ce média", "Toute la file"},
                service.repeat,
                (d, i) -> {
                  service.repeat = i;
                  library.preferences.edit().putInt("repeat", i).apply();
                  d.dismiss();
                })
            .create();
    hold(dialog);
    dialog.show();
  }

  private void volume() {
    SeekBar bar = new SeekBar(this);
    bar.setMax(100);
    bar.setProgress(service.volume);
    bar.setPadding(dp(24), dp(12), dp(24), dp(12));
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int value, boolean user) {
            if (user) service.setVolume(value);
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Volume du lecteur")
            .setView(bar)
            .setPositiveButton("Fermer", null)
            .create();
    hold(dialog);
    dialog.show();
  }

  private void delay(boolean subtitle) {
    final long[] value = {subtitle ? service.player.getSpuDelay() : service.player.getAudioDelay()};
    LinearLayout panel = column();
    panel.setPadding(dp(20), dp(12), dp(20), dp(14));
    TextView status = text("", 16, Ui.NAVY);
    status.setGravity(Gravity.CENTER);
    panel.addView(status);
    Runnable update =
        () -> status.setText(String.format(Locale.FRANCE, "%+.0f ms", value[0] / 1000.0));
    update.run();
    LinearLayout buttons = row();
    for (int delta : new int[] {-500, -100, 100, 500}) {
      Button button = Ui.button(this, (delta > 0 ? "+" : "") + delta, false);
      buttons.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
      click(
          button,
          () -> {
            value[0] = Math.max(-10000000, Math.min(10000000, value[0] + delta * 1000L));
            boolean ok =
                subtitle
                    ? service.player.setSpuDelay(value[0])
                    : service.player.setAudioDelay(value[0]);
            if (!ok) toast("Réglage non disponible pour ce média.");
            update.run();
          });
    }
    panel.addView(buttons);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(subtitle ? "Décalage des sous-titres" : "Décalage audio")
            .setView(panel)
            .setNeutralButton(
                "Réinitialiser",
                (d, i) -> {
                  if (subtitle) service.player.setSpuDelay(0);
                  else service.player.setAudioDelay(0);
                })
            .setPositiveButton("Fermer", null)
            .create();
    hold(dialog);
    dialog.show();
  }

  private void aspect() {
    String[] labels = {"Automatique", "16:9", "4:3", "21:9"};
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Format d’image")
            .setItems(
                labels,
                (d, i) -> {
                  service.player.setAspectRatio(i == 0 ? null : labels[i]);
                  service.player.setScale(0);
                })
            .create();
    hold(dialog);
    dialog.show();
  }

  private void equalizer() {
    LinearLayout panel = column();
    panel.setPadding(dp(16), dp(8), dp(16), dp(12));
    Switch enabled = new Switch(this);
    enabled.setText("Activer l’égaliseur");
    enabled.setChecked(library.preferences.getBoolean("eq_enabled", false));
    panel.addView(enabled, lp(-1, 48));
    enabled.setOnCheckedChangeListener(
        (button, checked) -> {
          library.preferences.edit().putBoolean("eq_enabled", checked).apply();
          service.applyEqualizer();
        });
    int count = org.videolan.libvlc.MediaPlayer.Equalizer.getBandCount();
    float[] values = new float[count];
    TextView[] levels = new TextView[count];
    SeekBar[] bars = new SeekBar[count];
    for (int i = 0; i < count; i++) {
      final int band = i;
      values[i] = library.preferences.getFloat("eq:" + i, 0);
      LinearLayout row = row();
      float hz = org.videolan.libvlc.MediaPlayer.Equalizer.getBandFrequency(i);
      TextView name =
          text(
              hz >= 1000
                  ? String.format(Locale.ROOT, "%.1f kHz", hz / 1000)
                  : Math.round(hz) + " Hz",
              11,
              Ui.MUTED);
      row.addView(name, lp(65, 40));
      SeekBar bar = new SeekBar(this);
      bars[i] = bar;
      bar.setMax(40);
      bar.setProgress(Math.round(values[i] + 20));
      row.addView(bar, new LinearLayout.LayoutParams(0, dp(40), 1));
      TextView level = text(Math.round(values[i]) + " dB", 10, Ui.MUTED);
      levels[i] = level;
      row.addView(level, lp(47, 40));
      panel.addView(row);
      bar.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view, int p, boolean user) {
              if (user) {
                values[band] = p - 20;
                level.setText((p - 20) + " dB");
                service.equalizer(values);
              }
            }

            public void onStartTrackingTouch(SeekBar view) {}

            public void onStopTrackingTouch(SeekBar view) {}
          });
    }
    ScrollView scroll = new ScrollView(this);
    scroll.addView(panel);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Égaliseur natif")
            .setView(scroll)
            .setNeutralButton(
                "Remettre à zéro",
                (d, which) -> {
                  Arrays.fill(values, 0);
                  service.equalizer(values);
                })
            .setPositiveButton("Fermer", null)
            .create();
    hold(dialog);
    dialog.show();
  }

  private void mediaInfo() {
    if (service.current == null) return;
    StringBuilder text =
        new StringBuilder(service.current.name)
            .append("\n\nDurée : ")
            .append(Ui.time(service.player.getLength()))
            .append("\n");
    if (service.current.size > 0)
      text.append(
          String.format(Locale.FRANCE, "Taille : %.2f Mo\n", service.current.size / 1000000.0));
    for (IMedia.Track track : service.tracks()) {
      text.append("\n")
          .append(
              track.type == IMedia.Track.Type.Video
                  ? "VIDÉO"
                  : track.type == IMedia.Track.Type.Audio ? "AUDIO" : "SOUS-TITRE")
          .append(" · ")
          .append(track.codec == null ? "Non renseigné" : track.codec)
          .append("\n")
          .append(language(track.language));
      if (track instanceof IMedia.VideoTrack) {
        IMedia.VideoTrack v = (IMedia.VideoTrack) track;
        text.append("\n").append(v.width).append(" × ").append(v.height);
        if (v.frameRateDen > 0)
          text.append(
              String.format(
                  Locale.FRANCE, " · %.2f images/s", v.frameRateNum / (double) v.frameRateDen));
      }
      if (track instanceof IMedia.AudioTrack) {
        IMedia.AudioTrack a = (IMedia.AudioTrack) track;
        text.append("\n").append(a.channels).append(" canaux · ").append(a.rate).append(" Hz");
      }
      if (track.bitrate > 0) text.append("\n").append(track.bitrate / 1000).append(" kb/s");
      text.append("\n");
    }
    TextView view = text(text.toString(), 13, Ui.INK);
    view.setPadding(dp(20), dp(12), dp(20), dp(18));
    view.setTextIsSelectable(true);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(view);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Informations réellement détectées")
            .setView(scroll)
            .setPositiveButton("Fermer", null)
            .create();
    hold(dialog);
    dialog.show();
  }

  private void settings() {
    LinearLayout panel = column();
    panel.setPadding(dp(22), dp(12), dp(22), dp(12));
    String[] keys = {"resume", "motion", "auto_pip"},
        labels =
            {
              "Reprendre à la dernière position",
              "Animations discrètes",
              "PiP automatique pour les vidéos (Android 8+)"
            };
    for (int i = 0; i < keys.length; i++) {
      final String key = keys[i];
      Switch toggle = new Switch(this);
      toggle.setText(labels[i]);
      toggle.setTextSize(13);
      toggle.setPadding(0, dp(9), 0, dp(9));
      toggle.setChecked(library.preferences.getBoolean(key, !key.equals("auto_pip")));
      toggle.setOnCheckedChangeListener(
          (button, checked) -> library.preferences.edit().putBoolean(key, checked).apply());
      panel.addView(toggle, lp(-1, -2));
    }
    TextView about =
        text(
            "RAKOTO Player Android\n"
                + "Version 1.0.0 bêta 1\n\n"
                + "Lecture locale avec LibVLC 3.7.7 (VideoLAN). Aucun catalogue ou compte n’est"
                + " nécessaire.\n\n"
                + "La fenêtre flottante vidéo utilise le Picture-in-Picture Android. Le son en"
                + " arrière-plan se contrôle dans les notifications. Le tiroir Windows et le"
                + " visualiseur audio ne sont pas portés dans cette première bêta.",
            12,
            Ui.MUTED);
    about.setPadding(0, dp(18), 0, dp(8));
    panel.addView(about);
    Button notices = Ui.button(this, "Licences des composants", false);
    panel.addView(notices, lp(-1, 48));
    click(notices, this::licenses);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(panel);
    new AlertDialog.Builder(this)
        .setTitle("Votre RAKOTO")
        .setView(scroll)
        .setPositiveButton("Fermer", null)
        .show();
  }

  private void licenses() {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (InputStream input = getAssets().open("THIRD-PARTY-NOTICES.txt")) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
      }
      TextView text = text(bytes.toString("UTF-8"), 12, Ui.INK);
      text.setPadding(dp(20), dp(12), dp(20), dp(20));
      text.setTextIsSelectable(true);
      ScrollView scroll = new ScrollView(this);
      scroll.addView(text);
      new AlertDialog.Builder(this)
          .setTitle("Licences et sources")
          .setView(scroll)
          .setPositiveButton("Fermer", null)
          .show();
    } catch (Exception e) {
      toast("Notice indisponible.");
    }
  }

  private void loadArtwork(MediaEntry entry) {
    if (entry.uri.equals(artUri)) return;
    artUri = entry.uri;
    album.setImageResource(R.drawable.rakoto_logo);
    drawerArt.setImageResource(R.drawable.rakoto_logo);
    artworkWorker.execute(
        () -> {
          Bitmap image = null;
          MediaMetadataRetriever retriever = new MediaMetadataRetriever();
          try (ParcelFileDescriptor fd =
              getContentResolver().openFileDescriptor(Uri.parse(entry.uri), "r")) {
            if (fd != null) {
              retriever.setDataSource(fd.getFileDescriptor());
              byte[] cover = retriever.getEmbeddedPicture();
              if (cover != null) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(cover, 0, cover.length, options);
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512)
                  options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                image = BitmapFactory.decodeByteArray(cover, 0, cover.length, options);
              } else if (entry.video && Build.VERSION.SDK_INT >= 27)
                image =
                    retriever.getScaledFrameAtTime(
                        1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 384, 216);
            }
          } catch (Exception ignored) {
          } finally {
            try {
              retriever.release();
            } catch (Exception ignored) {
            }
          }
          Bitmap result = image;
          if (result != null)
            handler.post(
                () -> {
                  if (!isDestroyed() && entry.uri.equals(artUri)) {
                    album.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    drawerArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    album.setImageBitmap(result);
                    drawerArt.setImageBitmap(result);
                  } else result.recycle();
                });
        });
  }

  private void loadSubtitles(List<Uri> values) {
    if (service == null || service.current == null) return;
    String id = service.current.uri;
    artworkWorker.execute(
        () -> {
          try {
            File folder = new File(getCacheDir(), "subtitles/" + UUID.randomUUID());
            if (!folder.mkdirs()) throw new IOException("Dossier indisponible");
            File selected = null;
            for (Uri uri : values) {
              String name = MediaEntry.read(this, uri).name;
              String lower = name.toLowerCase(Locale.ROOT);
              if (!lower.matches(".*\\.(srt|ass|ssa|vtt|sub|idx)$")) continue;
              name = name.replaceAll("[^\\p{L}\\p{N} ._-]", "_");
              File target = new File(folder, name);
              try (InputStream in = getContentResolver().openInputStream(uri);
                  OutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IOException("Fichier inaccessible");
                byte[] buffer = new byte[16384];
                int count, total = 0;
                while ((count = in.read(buffer)) != -1) {
                  total += count;
                  if (total > 20 * 1024 * 1024) throw new IOException("Sous-titre trop volumineux");
                  out.write(buffer, 0, count);
                }
              }
              if (selected == null || lower.endsWith(".idx")) selected = target;
            }
            if (selected == null)
              throw new IOException("Sélectionnez un sous-titre SRT, ASS, SSA, VTT, SUB ou IDX.");
            File subtitle = selected;
            handler.post(
                () -> {
                  if (service != null
                      && service.current != null
                      && id.equals(service.current.uri)) {
                    boolean ok =
                        service.player.addSlave(
                            IMedia.Slave.Type.Subtitle, subtitle.getAbsolutePath(), true);
                    toast(ok ? "Sous-titre chargé" : "Le moteur ne peut pas ouvrir ce sous-titre.");
                    refresh();
                  }
                });
          } catch (Exception e) {
            handler.post(
                () -> toast(e.getMessage() == null ? "Sous-titre inaccessible" : e.getMessage()));
          }
        });
  }

  private void fullscreen(boolean enabled) {
    fullscreen = enabled;
    if (Build.VERSION.SDK_INT >= 30) {
      WindowInsetsController controller = getWindow().getInsetsController();
      if (controller != null) {
        if (enabled) {
          controller.hide(WindowInsets.Type.systemBars());
          controller.setSystemBarsBehavior(
              WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else controller.show(WindowInsets.Type.systemBars());
      }
    } else
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              enabled
                  ? View.SYSTEM_UI_FLAG_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                      | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                  : View.SYSTEM_UI_FLAG_VISIBLE);
    playerToolbar.setVisibility(enabled || pip ? View.GONE : View.VISIBLE);
    applyInsets();
    showControls();
  }

  private void applyInsets() {
    if (root != null)
      root.setPadding(
          fullscreen || pip ? 0 : insetLeft,
          fullscreen || pip ? 0 : insetTop,
          fullscreen || pip ? 0 : insetRight,
          fullscreen || pip ? 0 : insetBottom);
  }

  private void showControls() {
    handler.removeCallbacks(hideControls);
    if (controls == null || pip) return;
    controls.setVisibility(View.VISIBLE);
    if (fullscreen && !menuOpen && !seeking) handler.postDelayed(hideControls, 3000);
  }

  private void enterPip() {
    if (Build.VERSION.SDK_INT < 26
        || service == null
        || !service.hasVideo
        || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
      toast("Le mode vidéo flottant n’est pas disponible sur cet appareil.");
      return;
    }
    showPlayer();
    fullscreen(false);
    try {
      enterPictureInPictureMode(pipParameters());
    } catch (Exception e) {
      toast("Android n’autorise pas le mode flottant pour le moment.");
    }
  }

  @androidx.annotation.RequiresApi(26)
  private PictureInPictureParams pipParameters() {
    boolean playing = service != null && service.player.isPlaying();
    RemoteAction action =
        new RemoteAction(
            Icon.createWithResource(
                this,
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play),
            playing ? "Pause" : "Lire",
            "RAKOTO Player",
            service.toggleIntent());
    PictureInPictureParams.Builder builder =
        new PictureInPictureParams.Builder()
            .setAspectRatio(new Rational(16, 9))
            .setActions(Collections.singletonList(action));
    Rect bounds = new Rect();
    if (video.getGlobalVisibleRect(bounds)) builder.setSourceRectHint(bounds);
    if (Build.VERSION.SDK_INT >= 31)
      builder.setAutoEnterEnabled(
          playerVisible
              && service.hasVideo
              && playing
              && library.preferences.getBoolean("auto_pip", false));
    return builder.build();
  }

  private void updatePip() {
    if (Build.VERSION.SDK_INT >= 26 && service != null)
      try {
        setPictureInPictureParams(pipParameters());
      } catch (Exception ignored) {
      }
    if (video != null)
      video.setKeepScreenOn(
          visible
              && playerVisible
              && service != null
              && service.hasVideo
              && service.player.isPlaying());
  }

  @Override
  public void onPictureInPictureModeChanged(boolean enabled, Configuration configuration) {
    super.onPictureInPictureModeChanged(enabled, configuration);
    pip = enabled;
    controls.setVisibility(enabled ? View.GONE : View.VISIBLE);
    playerToolbar.setVisibility(enabled ? View.GONE : View.VISIBLE);
    drawer.setVisibility(View.GONE);
    audioPlaceholder.setVisibility(
        enabled ? View.GONE : service != null && service.hasVideo ? View.GONE : View.VISIBLE);
    applyInsets();
    if (!enabled) showControls();
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    if (Build.VERSION.SDK_INT < 31
        && playerVisible
        && service != null
        && service.player.isPlaying()
        && service.hasVideo
        && library.preferences.getBoolean("auto_pip", false)) enterPip();
  }

  private void adaptLayout() {
    boolean landscape =
        getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    libraryLandscape.setVisibility(landscape ? View.GONE : View.VISIBLE);
    audioContent.setGravity(landscape ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
    audioContent.setPadding(dp(28), dp(landscape ? 68 : 65), dp(28), dp(landscape ? 150 : 220));
    album.setVisibility(landscape ? View.GONE : View.VISIBLE);
    trackTitle.setTextSize(landscape ? 17 : 23);
    trackTitle.setPadding(0, dp(landscape ? 7 : 22), 0, 0);
    trackSelection.setVisibility(landscape ? View.GONE : View.VISIBLE);
    controls.getChildAt(0).setLayoutParams(lp(-1, landscape ? 32 : 40));
    controls.getChildAt(1).setLayoutParams(lp(-1, landscape ? 52 : 66));
  }

  @Override
  public void onConfigurationChanged(Configuration configuration) {
    super.onConfigurationChanged(configuration);
    adaptLayout();
    if (fullscreen) fullscreen(true);
    updatePip();
  }

  private void navigateBack() {
    if (fullscreen) fullscreen(false);
    else if (playerVisible) showLibrary();
    else finish();
  }

  // API 33+ uses the native OnBackInvokedCallback registered in onCreate.
  // This override remains exclusively for Android 6 through Android 12.
  @android.annotation.SuppressLint("GestureBackNavigation")
  @Override
  public void onBackPressed() {
    if (Build.VERSION.SDK_INT < 33) navigateBack();
    else super.onBackPressed();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (service == null) pendingIntent = intent;
    else handleIntent(intent);
  }

  private void handleIntent(Intent intent) {
    if (intent == null || service == null) return;
    Uri uri = intent.getData();
    if (Intent.ACTION_VIEW.equals(intent.getAction())
        && uri != null
        && ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()))) {
      MediaEntry entry = MediaEntry.read(this, uri);
      library.addAll(Collections.singletonList(entry));
      reloadLibrary();
      playEntries(Collections.singletonList(entry), 0);
    } else if (intent.getBooleanExtra("player", false) && service.current != null) showPlayer();
  }

  private void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onStart() {
    super.onStart();
    visible = true;
    if (service != null) {
      service.listen(listener);
      if (playerVisible) attachVideo();
      refresh();
    }
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override
  protected void onStop() {
    visible = false;
    video.setKeepScreenOn(false);
    handler.removeCallbacks(tick);
    handler.removeCallbacks(hideControls);
    if (service != null) {
      service.savePosition();
      if (!pip) {
        service.unlisten(listener);
        detachVideo();
      }
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    artworkWorker.shutdownNow();
    if (service != null) {
      service.unlisten(listener);
      detachVideo();
    }
    if (bound) unbindService(connection);
    super.onDestroy();
  }

  PlaybackService playbackForTest() {
    return service;
  }
}
