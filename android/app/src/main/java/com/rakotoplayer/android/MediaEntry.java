package com.rakotoplayer.android;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONObject;

public final class MediaEntry {
  public final String uri;
  public String name, title, artist, mime;
  public long size;
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
        item.name = cursor.getString(0);
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
          .put("video", video);
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
    return item;
  }
}
