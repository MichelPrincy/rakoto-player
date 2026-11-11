package com.rakotoplayer.android;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import java.util.*;
import org.videolan.libvlc.*;
import org.videolan.libvlc.interfaces.IMedia;

public class PlaybackService extends Service {
  public static final String PLAY = "com.rakotoplayer.PLAY",
      TOGGLE = "com.rakotoplayer.TOGGLE",
      NEXT = "com.rakotoplayer.NEXT",
      PREVIOUS = "com.rakotoplayer.PREVIOUS",
      STOP = "com.rakotoplayer.STOP";
  private static final String CHANNEL = "rakoto_playback";

  public interface Listener {
    void changed();
  }

  public final class LocalBinder extends Binder {
    public PlaybackService service() {
      return PlaybackService.this;
    }
  }

  public final LocalBinder binder = new LocalBinder();
  public org.videolan.libvlc.MediaPlayer player;
  public MediaEntry current;
  public String error = "";
  public boolean hasVideo, loading;
  public List<MediaEntry> queue = new ArrayList<>();
  public int queueIndex = -1, repeat = 0, volume = 100;
  public boolean shuffle;
  private LibVLC vlc;
  private Library library;
  private ParcelFileDescriptor descriptor;
  private MediaSession session;
  private AudioManager audioManager;
  private AudioFocusRequest focusRequest;
  private final AudioManager.OnAudioFocusChangeListener focusListener = this::audioFocus;
  private PowerManager.WakeLock wakeLock;
  private org.videolan.libvlc.MediaPlayer.Equalizer equalizer;
  private long lastWake;
  private boolean foreground, resumeOnFocus;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Set<Listener> listeners = new HashSet<>();
  private long lastSave;
  private final BroadcastReceiver noisy =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          pause();
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();
    library = new Library(this);
    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    ArrayList<String> options =
        new ArrayList<>(
            Arrays.asList(
                "--audio-time-stretch",
                "--no-video-title-show",
                "--file-caching=500",
                "--subsdec-encoding=UTF-8"));
    vlc = new LibVLC(this, options);
    player = new org.videolan.libvlc.MediaPlayer(vlc);
    wakeLock =
        ((PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RAKOTO:Playback");
    wakeLock.setReferenceCounted(false);
    equalizer = org.videolan.libvlc.MediaPlayer.Equalizer.create();
    player.setEventListener(this::event);
    session = new MediaSession(this, "RAKOTO Player");
    session.setCallback(
        new MediaSession.Callback() {
          @Override
          public void onPlay() {
            play();
          }

          @Override
          public void onPause() {
            pause();
          }

          @Override
          public void onSkipToNext() {
            next(1);
          }

          @Override
          public void onSkipToPrevious() {
            next(-1);
          }

          @Override
          public void onSeekTo(long position) {
            seek(position);
          }

          @Override
          public void onStop() {
            stop();
          }
        });
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel =
          new NotificationChannel(
              CHANNEL, "Lecture multimédia", NotificationManager.IMPORTANCE_LOW);
      channel.setDescription("Commandes de lecture RAKOTO Player");
      getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(noisy, filter);
    repeat = library.preferences.getInt("repeat", 0);
    shuffle = library.preferences.getBoolean("shuffle", false);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null) {
      String action = intent.getAction();
      if (TOGGLE.equals(action)) toggle();
      else if (NEXT.equals(action)) next(1);
      else if (PREVIOUS.equals(action)) next(-1);
      else if (STOP.equals(action)) stop();
    }
    return START_NOT_STICKY;
  }

  public void listen(Listener listener) {
    listeners.add(listener);
  }

  public void unlisten(Listener listener) {
    listeners.remove(listener);
  }

  private void notifyChanged() {
    for (Listener listener : new ArrayList<>(listeners)) listener.changed();
  }

  private PendingIntent action(String action, int request) {
    return PendingIntent.getService(
        this,
        request,
        new Intent(this, PlaybackService.class).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  public PendingIntent toggleIntent() {
    return action(TOGGLE, 2);
  }

  private Notification notification() {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class).putExtra("player", true),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder builder =
        Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);
    return builder
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(current == null ? "RAKOTO Player" : current.title)
        .setContentText(
            loading ? "Chargement du média…" : current == null ? "Lecture locale" : current.artist)
        .setContentIntent(open)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setOngoing(player.isPlaying())
        .addAction(
            new Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Précédent", action(PREVIOUS, 1))
                .build())
        .addAction(
            new Notification.Action.Builder(
                    player.isPlaying()
                        ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play,
                    player.isPlaying() ? "Pause" : "Lire",
                    action(TOGGLE, 2))
                .build())
        .addAction(
            new Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Suivant", action(NEXT, 3))
                .build())
        .addAction(
            new Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", action(STOP, 4))
                .build())
        .setStyle(
            new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
        .build();
  }

  private void updateSession() {
    if (current != null)
      session.setMetadata(
          new MediaMetadata.Builder()
              .putString(MediaMetadata.METADATA_KEY_TITLE, current.title)
              .putString(MediaMetadata.METADATA_KEY_ARTIST, current.artist)
              .putLong(MediaMetadata.METADATA_KEY_DURATION, Math.max(0, player.getLength()))
              .build());
    long actions =
        PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_PAUSE
            | PlaybackState.ACTION_STOP
            | PlaybackState.ACTION_SEEK_TO
            | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
    session.setPlaybackState(
        new PlaybackState.Builder()
            .setActions(actions)
            .setState(
                player.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                Math.max(0, player.getTime()),
                player.getRate())
            .build());
    if (foreground) getSystemService(NotificationManager.class).notify(1, notification());
    notifyChanged();
  }

  private boolean focus() {
    int result;
    if (Build.VERSION.SDK_INT >= 26) {
      if (focusRequest == null)
        focusRequest =
            new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setOnAudioFocusChangeListener(focusListener)
                .build();
      result = audioManager.requestAudioFocus(focusRequest);
    } else
      result =
          audioManager.requestAudioFocus(
              focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  private void audioFocus(int change) {
    if (change == AudioManager.AUDIOFOCUS_GAIN) {
      player.setVolume(volume);
      if (resumeOnFocus) {
        resumeOnFocus = false;
        play();
      }
    } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
      player.setVolume(Math.max(0, volume / 4));
    else {
      resumeOnFocus = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && player.isPlaying();
      pause();
    }
  }

  public void open(List<MediaEntry> items, int index) {
    if (index < 0 || index >= items.size()) return;
    savePosition();
    player.stop();
    closeDescriptor();
    queue = new ArrayList<>(items);
    queueIndex = index;
    current = queue.get(index);
    error = "";
    hasVideo = false;
    loading = true;
    foreground = true;
    startForeground(1, notification());
    session.setActive(true);
    notifyChanged();
    try {
      Uri uri = Uri.parse(current.uri);
      String scheme = uri.getScheme();
      if (!"content".equals(scheme) && !"file".equals(scheme))
        throw new IllegalArgumentException("URI locale requise");
      descriptor = getContentResolver().openFileDescriptor(uri, "r");
      if (descriptor == null) throw new java.io.IOException("Fichier inaccessible");
      Media media = new Media(vlc, descriptor.getFileDescriptor());
      media.setHWDecoderEnabled(true, false);
      long start = library.position(current.uri);
      if (start > 1000) media.addOption(":start-time=" + (start / 1000.0));
      media.parseAsync(Media.Parse.ParseLocal, 5000);
      player.setMedia(media);
      media.release();
      player.setVolume(volume);
      applyEqualizer();
      if (focus()) player.play();
      else {
        loading = false;
        error = "La sortie audio est utilisée par une autre application.";
        updateSession();
      }
    } catch (Exception failure) {
      loading = false;
      error = "Accès au fichier impossible. Réimportez-le depuis le sélecteur Android.";
      stopForeground(true);
      foreground = false;
      notifyChanged();
    }
  }

  public void play() {
    if (current == null) return;
    if (!foreground) {
      foreground = true;
      startForeground(1, notification());
    }
    if (focus()) {
      player.play();
      session.setActive(true);
      updateSession();
    }
  }

  public void pause() {
    if (player != null) {
      player.pause();
      releaseWake();
      savePosition();
      updateSession();
    }
  }

  public void toggle() {
    if (player.isPlaying()) pause();
    else play();
  }

  public void seek(long position) {
    if (player.isSeekable())
      player.setTime(Math.max(0, Math.min(position, player.getLength())), false);
    updateSession();
  }

  public void next(int direction) {
    if (queue.isEmpty()) return;
    if (direction < 0 && player.getTime() > 3000) {
      seek(0);
      return;
    }
    int next = queueIndex + direction;
    if (shuffle && queue.size() > 1) {
      do {
        next = new Random().nextInt(queue.size());
      } while (next == queueIndex);
    } else next = (next + queue.size()) % queue.size();
    open(queue, next);
  }

  public void stop() {
    savePosition();
    player.stop();
    releaseWake();
    loading = false;
    stopForeground(true);
    foreground = false;
    session.setActive(false);
    if (Build.VERSION.SDK_INT >= 26 && focusRequest != null)
      audioManager.abandonAudioFocusRequest(focusRequest);
    else audioManager.abandonAudioFocus(focusListener);
    notifyChanged();
    stopSelf();
  }

  public void setVolume(int value) {
    volume = Math.max(0, Math.min(100, value));
    player.setVolume(volume);
    notifyChanged();
  }

  public void savePosition() {
    if (current != null && player.getTime() > 0) library.position(current.uri, player.getTime());
  }

  private void metadata() {
    IMedia media = player.getMedia();
    if (media == null || current == null) return;
    try {
      String title = media.getMeta(Media.Meta.Title), artist = media.getMeta(Media.Meta.Artist);
      if (title != null && !title.trim().isEmpty()) current.title = title;
      if (artist != null && !artist.trim().isEmpty()) current.artist = artist;
      hasVideo = false;
      for (int i = 0; i < media.getTrackCount(); i++) {
        IMedia.Track track = media.getTrack(i);
        if (track != null && track.type == IMedia.Track.Type.Video) hasVideo = true;
      }
      current.video = hasVideo;
      library.update(current);
    } finally {
      media.release();
    }
  }

  private void event(org.videolan.libvlc.MediaPlayer.Event event) {
    if (event.type == org.videolan.libvlc.MediaPlayer.Event.Playing) {
      loading = false;
      renewWake();
      metadata();
      updateSession();
    } else if (event.type == org.videolan.libvlc.MediaPlayer.Event.ESAdded
        || event.type == org.videolan.libvlc.MediaPlayer.Event.LengthChanged) {
      metadata();
      notifyChanged();
    } else if (event.type == org.videolan.libvlc.MediaPlayer.Event.Paused) updateSession();
    else if (event.type == org.videolan.libvlc.MediaPlayer.Event.EndReached) {
      if (current != null) library.position(current.uri, 0);
      if (repeat == 1) open(queue, queueIndex);
      else if (queueIndex + 1 < queue.size() || repeat == 2 || shuffle) next(1);
      else stop();
    } else if (event.type == org.videolan.libvlc.MediaPlayer.Event.EncounteredError) {
      loading = false;
      releaseWake();
      error = "Ce média est endommagé, inaccessible ou non pris en charge par le moteur.";
      stopForeground(true);
      foreground = false;
      notifyChanged();
    } else if (event.type == org.videolan.libvlc.MediaPlayer.Event.TimeChanged
        && SystemClock.elapsedRealtime() - lastSave > 5000) {
      lastSave = SystemClock.elapsedRealtime();
      savePosition();
      if (player.isPlaying() && SystemClock.elapsedRealtime() - lastWake > 300000) renewWake();
    }
  }

  public List<IMedia.Track> tracks() {
    List<IMedia.Track> result = new ArrayList<>();
    IMedia media = player.getMedia();
    if (media == null) return result;
    try {
      for (int i = 0; i < media.getTrackCount(); i++) {
        IMedia.Track track = media.getTrack(i);
        if (track != null) result.add(track);
      }
    } finally {
      media.release();
    }
    return result;
  }

  public void equalizer(float[] bands) {
    for (int i = 0; i < bands.length; i++)
      library.preferences.edit().putFloat("eq:" + i, bands[i]).apply();
    applyEqualizer();
  }

  public void applyEqualizer() {
    if (!library.preferences.getBoolean("eq_enabled", false)) {
      player.setEqualizer(null);
      return;
    }
    for (int i = 0; i < org.videolan.libvlc.MediaPlayer.Equalizer.getBandCount(); i++)
      equalizer.setAmp(i, library.preferences.getFloat("eq:" + i, 0));
    player.setEqualizer(equalizer);
  }

  public org.videolan.libvlc.MediaPlayer.TrackDescription[] audioTracks() {
    org.videolan.libvlc.MediaPlayer.TrackDescription[] result = player.getAudioTracks();
    return result == null ? new org.videolan.libvlc.MediaPlayer.TrackDescription[0] : result;
  }

  public org.videolan.libvlc.MediaPlayer.TrackDescription[] subtitleTracks() {
    org.videolan.libvlc.MediaPlayer.TrackDescription[] result = player.getSpuTracks();
    return result == null ? new org.videolan.libvlc.MediaPlayer.TrackDescription[0] : result;
  }

  public org.videolan.libvlc.MediaPlayer.TrackDescription[] videoTracks() {
    org.videolan.libvlc.MediaPlayer.TrackDescription[] result = player.getVideoTracks();
    return result == null ? new org.videolan.libvlc.MediaPlayer.TrackDescription[0] : result;
  }

  private void renewWake() {
    wakeLock.acquire(600000);
    lastWake = SystemClock.elapsedRealtime();
  }

  private void releaseWake() {
    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
  }

  private void closeDescriptor() {
    try {
      if (descriptor != null) descriptor.close();
    } catch (Exception ignored) {
    }
    descriptor = null;
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    savePosition();
    releaseWake();
    player.stop();
    player.release();
    vlc.release();
    closeDescriptor();
    session.release();
    if (Build.VERSION.SDK_INT >= 26 && focusRequest != null)
      audioManager.abandonAudioFocusRequest(focusRequest);
    else audioManager.abandonAudioFocus(focusListener);
    try {
      unregisterReceiver(noisy);
    } catch (Exception ignored) {
    }
    super.onDestroy();
  }
}
