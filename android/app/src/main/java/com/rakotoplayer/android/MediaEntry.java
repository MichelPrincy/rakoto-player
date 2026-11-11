package com.rakotoplayer.android;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONObject;

public final class MediaEntry {
  public final String uri;
  public String name, title, artist, mime, folder = "Fichiers ajoutés", source = "manual";
  public long size, durationMs, addedAt = System.currentTimeMillis(), modifiedAt;
  public boolean video;

  public MediaEntry(String uri, String name) {
    this.uri = uri;
    this.name = name;
    this.title = name;
    this.artist = "Fichier local";
    this.mime = "";
  }

  public static MediaEntry read(Context context, Uri uri) {
    String name = uri.getLastPathSegment();
    MediaEntry item = new MediaEntry(uri.toString(), name == null ? "Média local" : name);
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                uri,
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null)) {
      if (cursor != null && cursor.moveToFirst()) {
        if (!cursor.isNull(0)) item.name = cursor.getString(0);
        item.title = item.name;
        if (!cursor.isNull(1)) item.size = cursor.getLong(1);
      }
    } catch (Exception ignored) {
    }
    try {
      String type = context.getContentResolver().getType(uri);
      item.mime = type == null ? "" : type;
    } catch (Exception ignored) {
    }
    item.video = item.mime.startsWith("video/");
    if (!item.video
        && item.name
            .toLowerCase(java.util.Locale.ROOT)
            .matches(".*\\.(mkv|mp4|avi|mov|wmv|flv|webm|m4v|mpeg|mpg|ts|m2ts|3gp)$"))
      item.video = true;
    return item;
  }

  public JSONObject json() {
    JSONObject object = new JSONObject();
    try {
      object
          .put("uri", uri)
          .put("name", name)
          .put("title", title)
          .put("artist", artist)
          .put("mime", mime)
          .put("size", size)
          .put("video", video)
          .put("folder", folder)
          .put("source", source)
          .put("duration", durationMs)
          .put("added", addedAt)
          .put("modified", modifiedAt);
    } catch (Exception ignored) {
    }
    return object;
  }

  public static MediaEntry from(JSONObject object) {
    MediaEntry item =
        new MediaEntry(object.optString("uri"), object.optString("name", "Média local"));
    item.title = object.optString("title", item.name);
    item.artist = object.optString("artist", "Fichier local");
    item.mime = object.optString("mime");
    item.size = object.optLong("size");
    item.video = object.optBoolean("video");
    item.folder = object.optString("folder", "Fichiers ajoutés");
    item.source = object.optString("source", "manual");
    item.durationMs = object.optLong("duration");
    item.addedAt = object.optLong("added", System.currentTimeMillis());
    item.modifiedAt = object.optLong("modified");
    return item;
  }

  public boolean isPrivate() {
    return "vault".equals(source);
  }
}
