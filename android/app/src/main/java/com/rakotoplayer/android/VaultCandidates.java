package com.rakotoplayer.android;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.util.*;

final class VaultCandidates {
  static List<MediaEntry> list(Context context, Library library) {
    Map<String, MediaEntry> result = new LinkedHashMap<>();
    for (MediaEntry item : library.entries(true, false, null)) result.put(item.uri, item);
    Set<String> volumes = new LinkedHashSet<>();
    if (Build.VERSION.SDK_INT >= 29) volumes.addAll(MediaStore.getExternalVolumeNames(context));
    else volumes.add("external");
    for (String volume : volumes)
      for (boolean image : new boolean[] {true, false}) {
        Uri collection =
            image
                ? MediaStore.Images.Media.getContentUri(volume)
                : MediaStore.Video.Media.getContentUri(volume);
        List<String> columns =
            new ArrayList<>(
                Arrays.asList(
                    "_id", "_display_name", "mime_type", "_size", "date_added", "width", "height"));
        if (!image) columns.add("duration");
        try (Cursor cursor =
            context
                .getContentResolver()
                .query(collection, columns.toArray(new String[0]), null, null, null)) {
          if (cursor == null) continue;
          while (cursor.moveToNext()) {
            MediaEntry entry =
                new MediaEntry(
                    ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                    cursor.isNull(1) ? "Média local" : cursor.getString(1));
            entry.mime = cursor.isNull(2) ? "" : cursor.getString(2);
            entry.size = cursor.getLong(3);
            entry.addedAt = cursor.getLong(4) * 1000;
            entry.width = cursor.getInt(5);
            entry.height = cursor.getInt(6);
            entry.image = image;
            entry.video = !image;
            if (!image) entry.durationMs = cursor.getLong(7);
            result.put(entry.uri, entry);
          }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
      }
    List<MediaEntry> entries = new ArrayList<>(result.values());
    Collections.sort(entries, (a, b) -> Long.compare(b.addedAt, a.addedAt));
    return entries;
  }
}
