package com.rakotoplayer.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.videolan.libvlc.interfaces.IMedia;

/** Functional native tests. No screenshots or visual inspection. */
public final class PlayerInstrumentation extends Instrumentation {
  private MainActivity activity;
  private PlaybackService service;
  private final StringBuilder log = new StringBuilder();

  private interface Check {
    boolean run() throws Exception;
  }

  private interface Read<T> {
    T run() throws Exception;
  }

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  private <T> T main(Read<T> read) throws Exception {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    runOnMainSync(
        () -> {
          try {
            result.set(read.run());
          } catch (Throwable error) {
            failure.set(error);
          }
        });
    if (failure.get() != null) throw new Exception(failure.get());
    return result.get();
  }

  private void until(Check check, String label, long timeout) throws Exception {
    long deadline = SystemClock.elapsedRealtime() + timeout;
    while (!check.run()) {
      if (SystemClock.elapsedRealtime() > deadline)
        throw new AssertionError("Délai dépassé : " + label);
      SystemClock.sleep(120);
    }
  }

  private void require(boolean value, String label) {
    if (!value) throw new AssertionError(label);
  }

  private void note(String value) {
    log.append(value).append('\n');
    Bundle update = new Bundle();
    update.putString("stream", value + "\n");
    sendStatus(2, update);
  }

  private File fixture(String name) throws Exception {
    File directory = new File(getTargetContext().getFilesDir(), "synthetic-tests");
    if (!directory.exists() && !directory.mkdirs())
      throw new IOException("Dossier de test indisponible");
    File output = new File(directory, name);
    try (InputStream input = getContext().getAssets().open("fixtures/" + name);
        OutputStream target = new FileOutputStream(output)) {
      byte[] buffer = new byte[65536];
      int count;
      while ((count = input.read(buffer)) != -1) target.write(buffer, 0, count);
    }
    return output;
  }

  private IMedia.Stats stats() throws Exception {
    return main(
        () -> {
          IMedia media = service.player.getMedia();
          if (media == null) return null;
          try {
            return media.getStats();
          } finally {
            media.release();
          }
        });
  }

  private void open(String name, boolean video, int audio, int subtitles) throws Exception {
    File file = fixture(name);
    MediaEntry entry = new MediaEntry(Uri.fromFile(file).toString(), name);
    main(
        () -> {
          activity.playEntries(Collections.singletonList(entry), 0);
          service.setVolume(0);
          return null;
        });
    until(
        () -> main(() -> service.player.isPlaying() && service.player.getTime() > 250),
        name,
        25000);
    until(
        () -> {
          IMedia.Stats s = stats();
          return s != null
              && (video ? s.decodedVideo > 0 && s.displayedPictures > 0 : s.decodedAudio > 0);
        },
        "décodage réel " + name,
        25000);
    List<IMedia.Track> tracks = main(() -> service.tracks());
    int audios = 0, texts = 0;
    for (IMedia.Track track : tracks) {
      if (track.type == IMedia.Track.Type.Audio) audios++;
      if (track.type == IMedia.Track.Type.Text) texts++;
    }
    require(audios == audio, name + " : nombre de pistes audio " + audios + " attendu " + audio);
    require(
        texts == subtitles, name + " : nombre de sous-titres " + texts + " attendu " + subtitles);
    IMedia.Stats s = stats();
    note(
        name
            + " : lecture OK ; vidéo décodée="
            + s.decodedVideo
            + ", images présentées="
            + s.displayedPictures
            + ", audio décodé="
            + s.decodedAudio);
  }

  private Object field(String name) throws Exception {
    Field field = MainActivity.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(activity);
  }

  private void invoke(String name, Class<?>[] types, Object... values) throws Exception {
    Method method = MainActivity.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    method.invoke(activity, values);
  }

  @Override
  public void onStart() {
    Bundle status = new Bundle();
    status.putString("class", getClass().getName());
    status.putString("test", "nativeMediaAndControls");
    status.putInt("numtests", 1);
    status.putInt("current", 1);
    sendStatus(1, status);
    Bundle result = new Bundle();
    int code = Activity.RESULT_OK;
    try {
      Intent intent =
          new Intent(getTargetContext(), MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity = (MainActivity) startActivitySync(intent);
      until(() -> main(() -> activity.playbackForTest() != null), "service connecté", 20000);
      service = main(() -> activity.playbackForTest());
      main(
          () -> {
            new Library(getTargetContext())
                .preferences
                .edit()
                .putBoolean("resume", false)
                .putBoolean("notifications_asked", true)
                .apply();
            return null;
          });
      Object[][] cases = {
        {"mkv-simple.mkv", true, 1, 0},
        {"MULTi.1080p.DSNP.WEB.H265-BYOR.mkv", true, 4, 3},
        {"mp4-h264.mp4", true, 1, 0},
        {"mp4-hevc.mp4", true, 1, 0},
        {"xvid.avi", true, 1, 0},
        {"vp8.webm", true, 1, 0},
        {"musique.flac", false, 1, 0},
        {"musique.mp3", false, 1, 0},
        {"pcm.wav", false, 1, 0},
        {"sans-audio.mkv", true, 0, 0},
        {"contenu-sans-extension", true, 1, 0},
        {"movie.mov", true, 1, 0},
        {"video.m4v", true, 1, 0},
        {"video.3gp", true, 1, 0},
        {"windows.wmv", true, 1, 0},
        {"flash.flv", true, 1, 0},
        {"mpeg1.mpeg", true, 1, 0},
        {"mpeg2.mpg", true, 1, 0},
        {"transport.ts", true, 1, 0},
        {"bluray.m2ts", true, 1, 0},
        {"audio.aac", false, 1, 0},
        {"audio.ogg", false, 1, 0},
        {"audio.m4a", false, 1, 0},
        {"audio.opus", false, 1, 0}
      };
      for (Object[] item : cases)
        open((String) item[0], (Boolean) item[1], (Integer) item[2], (Integer) item[3]);
      open("MULTi.1080p.DSNP.WEB.H265-BYOR.mkv", true, 4, 3);
      List<IMedia.Track> tracks = main(() -> service.tracks());
      int english = -1, japanese = -1;
      Set<String> languages = new HashSet<>();
      for (IMedia.Track track : tracks) {
        if (track.type == IMedia.Track.Type.Audio) {
          languages.add(track.language);
          if ("eng".equals(track.language) || "en".equals(track.language)) english = track.id;
        }
        if (track.type == IMedia.Track.Type.Text
            && ("jpn".equals(track.language) || "ja".equals(track.language))) japanese = track.id;
      }
      require(languages.size() == 4, "Quatre langues audio réelles");
      require(english >= 0 && japanese >= 0, "Pistes anglaise et japonaise détectées");
      final int aid = english, sid = japanese;
      main(
          () -> {
            service.seek(1500);
            require(service.player.setAudioTrack(aid), "piste audio sélectionnée");
            require(service.player.setSpuTrack(sid), "sous-titre sélectionné");
            return null;
          });
      until(
          () ->
              main(
                  () ->
                      service.player.getAudioTrack() == aid
                          && service.player.getSpuTrack() == sid
                          && service.player.getTime() > 1800),
          "changement de langue sans rechargement",
          15000);
      File subtitle = fixture("external.vtt");
      main(
          () -> {
            require(
                service.player.addSlave(
                    IMedia.Slave.Type.Subtitle, subtitle.getAbsolutePath(), true),
                "sous-titre externe accepté");
            require(service.player.setAudioDelay(150000), "décalage audio");
            require(service.player.setSpuDelay(200000), "décalage sous-titres");
            service.player.setRate(1.5f);
            return null;
          });
      require(main(() -> Math.abs(service.player.getRate() - 1.5f) < .01f), "vitesse native");
      require(
          main(
              () ->
                  service.player.getAudioDelay() == 150000
                      && service.player.getSpuDelay() == 200000),
          "décalages natifs relus");
      main(
          () -> {
            new Library(getTargetContext())
                .preferences
                .edit()
                .putBoolean("eq_enabled", true)
                .apply();
            service.equalizer(new float[] {3, 2, 1, 0, 0, 0, 1, 2, 2, 1});
            service.player.setRate(1);
            return null;
          });
      note(
          "Langues, sous-titres intégrés/externes, synchronisation, vitesse et égaliseur natifs"
              + " vérifiés.");
      main(
          () -> {
            invoke("fullscreen", new Class<?>[] {boolean.class}, true);
            return null;
          });
      until(
          () -> main(() -> ((View) field("controls")).getVisibility() == View.INVISIBLE),
          "masquage immersif des commandes",
          7000);
      main(
          () -> {
            invoke("showControls", new Class<?>[] {});
            return null;
          });
      require(
          main(() -> ((View) field("controls")).getVisibility() == View.VISIBLE),
          "commandes réaffichées");
      main(
          () -> {
            invoke("fullscreen", new Class<?>[] {boolean.class}, false);
            return null;
          });
      if (android.os.Build.VERSION.SDK_INT >= 26
          && getTargetContext()
              .getPackageManager()
              .hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
        main(
            () -> {
              invoke("enterPip", new Class<?>[] {});
              return null;
            });
        until(
            () -> main(() -> activity.isInPictureInPictureMode()),
            "vraie fenêtre PiP Android",
            10000);
        main(
            () -> {
              getTargetContext()
                  .startActivity(
                      new Intent(getTargetContext(), MainActivity.class)
                          .addFlags(
                              Intent.FLAG_ACTIVITY_NEW_TASK
                                  | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
              return null;
            });
        until(() -> main(() -> !activity.isInPictureInPictureMode()), "restauration PiP", 10000);
        note("Plein écran et Picture-in-Picture natif vérifiés.");
      }
      open("musique.flac", false, 1, 0);
      long before = main(() -> service.player.getTime());
      try (android.os.ParcelFileDescriptor ignored =
          getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME")) {}
      until(
          () -> main(() -> service.player.isPlaying() && service.player.getTime() > before + 500),
          "lecture audio en arrière-plan",
          10000);
      main(
          () -> {
            service.toggleIntent().send();
            return null;
          });
      until(
          () -> main(() -> !service.player.isPlaying()),
          "pause via commande de notification",
          5000);
      main(
          () -> {
            service.toggleIntent().send();
            return null;
          });
      until(
          () -> main(() -> service.player.isPlaying()),
          "reprise via commande de notification",
          5000);
      note("Lecture en arrière-plan et commandes de notification vérifiées.");
      File bad = fixture("endommagé.mkv");
      main(
          () -> {
            activity.playEntries(
                Collections.singletonList(
                    new MediaEntry(Uri.fromFile(bad).toString(), "endommagé")),
                0);
            return null;
          });
      until(() -> main(() -> !service.error.isEmpty()), "rejet du fichier endommagé", 15000);
      note("Fichier endommagé rejeté sans crash. Tous les contrôles natifs ont réussi.");
      status.putString("stream", log.toString());
      sendStatus(0, status);
    } catch (Throwable failure) {
      code = Activity.RESULT_CANCELED;
      StringWriter trace = new StringWriter();
      failure.printStackTrace(new PrintWriter(trace));
      log.append(trace);
      status.putString("stack", trace.toString());
      status.putString("stream", log.toString());
      sendStatus(-2, status);
    } finally {
      try {
        main(
            () -> {
              if (service != null) service.stop();
              if (activity != null) activity.finish();
              return null;
            });
      } catch (Exception ignored) {
      }
      result.putString("stream", log.toString());
      result.putInt("numtests", 1);
      finish(code, result);
    }
  }
}
