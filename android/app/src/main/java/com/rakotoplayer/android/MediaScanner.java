package com.rakotoplayer.android;

import android.content.*;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.util.*;

/** Queries all accessible media, without IS_MUSIC, minimum length or size filters. */
public final class MediaScanner {
  public interface Progress {
    void changed(String message);
  }

  public static final class Result {
    public int audio, video;
    public final List<String> unavailable = new ArrayList<>();
  }

  private final Context context;
  private final Library library;

  public MediaScanner(Context context, Library library) {
    this.context = context.getApplicationContext();
    this.library = library;
  }

  private static void cancelled() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Scan annulé");
  }

  public Result scan(Progress progress) throws IOException {
    Result result = new Result();
    Set<String> volumes = new LinkedHashSet<>();
    if (Build.VERSION.SDK_INT >= 29) volumes.addAll(MediaStore.getExternalVolumeNames(context));
    else volumes.add("external");
    volumes.add("internal");
    for (String volume : volumes)
      for (boolean video : new boolean[] {false, true}) {
        cancelled();
        Uri collection =
            video
                ? MediaStore.Video.Media.getContentUri(volume)
                : MediaStore.Audio.Media.getContentUri(volume);
        progress.changed(
            "Recherche " + (video ? "des vidéos" : "de tous les fichiers audio") + "…");
        try {
          List<MediaEntry> entries = collection(collection, volume, video);
          library.replaceScan("store:" + collection, entries, true);
          if (video) result.video += entries.size();
          else result.audio += entries.size();
        } catch (SecurityException failure) {
          library.replaceScan("store:" + collection, Collections.emptyList(), true);
          result.unavailable.add((video ? "Vidéos" : "Audio") + " · " + volume);
        } catch (IllegalArgumentException failure) {
          result.unavailable.add((video ? "Vidéos" : "Audio") + " · " + volume);
        }
      }
    for (String tree : library.folders()) {
      cancelled();
      progress.changed("Scan des dossiers ajoutés…");
      try {
        List<MediaEntry> entries = tree(Uri.parse(tree));
        library.replaceScan("tree:" + tree, entries, true);
        for (MediaEntry e : entries) {
          if (e.video) result.video++;
          else result.audio++;
        }
      } catch (SecurityException | IllegalArgumentException failure) {
        result.unavailable.add("Un dossier ajouté n’est plus autorisé.");
      }
    }
    return result;
  }

  private List<MediaEntry> collection(Uri collection, String volume, boolean video)
      throws IOException {
    List<String> columns =
        new ArrayList<>(
            Arrays.asList(
                "_id",
                "_display_name",
                "mime_type",
                "_size",
                "duration",
                "date_added",
                "date_modified",
                "title"));
    if (!video) {
      columns.add("artist");
      columns.add("album");
    } else {
      columns.add("width");
      columns.add("height");
    }
    columns.add(Build.VERSION.SDK_INT >= 29 ? "relative_path" : "_data");
    List<MediaEntry> entries = new ArrayList<>();
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(collection, columns.toArray(new String[0]), null, null, null)) {
      if (cursor == null) throw new IllegalArgumentException("Index média indisponible");
      while (cursor.moveToNext()) {
        cancelled();
        long id = number(cursor, "_id");
        String name = string(cursor, "_display_name");
        if (name.isEmpty()) name = "Média " + id;
        MediaEntry entry =
            new MediaEntry(ContentUris.withAppendedId(collection, id).toString(), name);
        entry.mime = string(cursor, "mime_type");
        entry.size = number(cursor, "_size");
        entry.video = video;
        entry.album = video ? "" : string(cursor, "album");
        entry.width = video ? (int) number(cursor, "width") : 0;
        entry.height = video ? (int) number(cursor, "height") : 0;
        entry.durationMs = number(cursor, "duration");
        entry.modifiedAt = number(cursor, "date_modified") * 1000;
        long added = number(cursor, "date_added") * 1000;
        if (added > 0) entry.addedAt = added;
        String title = string(cursor, "title"), artist = video ? "" : string(cursor, "artist");
        if (!title.isEmpty()) entry.title = title;
        if (!artist.isEmpty() && !"<unknown>".equals(artist)) entry.artist = artist;
        String label =
            "internal".equals(volume)
                ? "Système"
                : "external".equals(volume) || "external_primary".equals(volume)
                    ? "Téléphone"
                    : "Carte SD " + volume;
        String path = string(cursor, Build.VERSION.SDK_INT >= 29 ? "relative_path" : "_data");
        if (Build.VERSION.SDK_INT < 29) {
          int at = path.lastIndexOf('/');
          path = at >= 0 ? path.substring(0, at) : "";
          if (path.startsWith("/storage/emulated/0/")) path = path.substring(20);
          else if (path.startsWith("/storage/")) {
            String rest = path.substring(9);
            int slash = rest.indexOf('/');
            if (slash >= 0) {
              label = "Carte SD " + rest.substring(0, slash);
              path = rest.substring(slash + 1);
            }
          } else if (path.startsWith("/system/")) path = path.substring(8);
        }
        entry.folder = clean(label + "/" + path);
        entries.add(entry);
      }
    }
    return entries;
  }

  private static String clean(String value) {
    return value.replaceAll("^/+|/+$", "").replaceAll("/{2,}", "/");
  }

  private static String string(Cursor c, String name) {
    int i = c.getColumnIndex(name);
    return i < 0 || c.isNull(i) ? "" : c.getString(i);
  }

  private static long number(Cursor c, String name) {
    int i = c.getColumnIndex(name);
    return i < 0 || c.isNull(i) ? 0 : c.getLong(i);
  }

  private static final class Directory {
    final String id, path;

    Directory(String id, String path) {
      this.id = id;
      this.path = path;
    }
  }

  private List<MediaEntry> tree(Uri tree) throws IOException {
    List<MediaEntry> entries = new ArrayList<>();
    String root = DocumentsContract.getTreeDocumentId(tree), name = "Dossier ajouté";
    Map<String, MediaEntry> known = new HashMap<>();
    for (MediaEntry entry : library.entries()) known.put(entry.uri, entry);
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                DocumentsContract.buildDocumentUriUsingTree(tree, root),
                new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null,
                null,
                null)) {
      if (c != null && c.moveToFirst()) name = c.getString(0);
    }
    ArrayDeque<Directory> pending = new ArrayDeque<>();
    pending.add(new Directory(root, "Dossiers ajoutés/" + name));
    Set<String> visited = new HashSet<>();
    while (!pending.isEmpty()) {
      cancelled();
      Directory folder = pending.removeFirst();
      if (!visited.add(folder.id)) continue;
      Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id);
      try (Cursor c =
          context
              .getContentResolver()
              .query(
                  children,
                  new String[] {
                    "document_id", "_display_name", "mime_type", "_size", "last_modified"
                  },
                  null,
                  null,
                  null)) {
        if (c == null) throw new IllegalArgumentException("Dossier indisponible");
        while (c.moveToNext()) {
          cancelled();
          String id = c.getString(0), display = c.getString(1), mime = c.getString(2);
          if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
            pending.add(new Directory(id, folder.path + "/" + display));
            continue;
          }
          int kind = kind(display, mime);
          if (kind < 0
              && mime != null
              && !mime.isEmpty()
              && !"application/octet-stream".equals(mime)) continue;
          MediaEntry entry =
              new MediaEntry(
                  DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), display);
          entry.mime = mime == null ? "" : mime;
          entry.size = c.isNull(3) ? 0 : c.getLong(3);
          entry.modifiedAt = c.isNull(4) ? 0 : c.getLong(4);
          entry.video = kind == 1;
          entry.folder = clean(folder.path);
          MediaEntry cached = known.get(entry.uri);
          if (cached != null
              && cached.size == entry.size
              && cached.modifiedAt == entry.modifiedAt
              && cached.durationMs > 0) {
            entry.durationMs = cached.durationMs;
            entry.video = cached.video;
            entry.title = cached.title;
            entry.artist = cached.artist;
            entry.album = cached.album;
            entry.width = cached.width;
            entry.height = cached.height;
          } else {
            boolean detected = duration(context, entry);
            if (kind < 0 && !detected) continue;
          }
          entries.add(entry);
        }
      }
    }
    return entries;
  }

  public static int kind(String name, String mime) {
    if (mime != null) {
      if (mime.startsWith("video/")) return 1;
      if (mime.startsWith("audio/")) return 0;
    }
    String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (lower.matches(
        ".*\\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpeg|mpg|ts|m2ts|3gp|3g2|vob|ogv|divx|mts)$"))
      return 1;
    if (lower.matches(
        ".*\\.(mp3|flac|wav|aac|ogg|oga|m4a|opus|wma|aiff|aif|amr|ape|alac|ac3|dts|mka|mid|midi|caf|mp2|mp1|au|ra)$"))
      return 0;
    return -1;
  }

  public static boolean duration(Context context, MediaEntry entry) {
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try (ParcelFileDescriptor fd =
        context.getContentResolver().openFileDescriptor(Uri.parse(entry.uri), "r")) {
      if (fd != null) {
        retriever.setDataSource(fd.getFileDescriptor());
        String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (duration != null) entry.durationMs = Long.parseLong(duration);
        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        if (title != null && !title.trim().isEmpty()) entry.title = title;
        if (artist != null && !artist.trim().isEmpty()) entry.artist = artist;
        if (album != null) entry.album = album;
        String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        if (width != null) entry.width = Integer.parseInt(width);
        if (height != null) entry.height = Integer.parseInt(height);
        String video = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO),
            audio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
        if ("yes".equals(video)) entry.video = true;
        return "yes".equals(video) || "yes".equals(audio);
      }
    } catch (Exception ignored) {
    } finally {
      try {
        retriever.release();
      } catch (Exception ignored) {
      }
    }
    return false;
  }
}
