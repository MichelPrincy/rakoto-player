package com.rakotoplayer.android;

import android.content.*;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.*;

/** Publishes only a complete authenticated export, using the real Android gallery index. */
final class VaultGallery {
  static void restore(Context context, Vault vault, MediaEntry entry, Vault.Progress progress)
      throws Exception {
    String name = entry.name.replaceAll("[/\\\\\\p{Cntrl}]", "_");
    if (name.isEmpty() || name.equals(".") || name.equals(".."))
      throw new IOException("Nom de fichier invalide");
    String mime = entry.mime;
    if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
      int at = name.lastIndexOf('.');
      mime =
          at < 0
              ? null
              : MimeTypeMap.getSingleton()
                  .getMimeTypeFromExtension(name.substring(at + 1).toLowerCase(Locale.ROOT));
    }
    if (mime == null || (!mime.startsWith("image/") && !mime.startsWith("video/")))
      mime = entry.image ? "image/jpeg" : "video/mp4";
    if (Build.VERSION.SDK_INT >= 29) {
      ContentResolver resolver = context.getContentResolver();
      ContentValues values = new ContentValues();
      values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
      values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
      values.put(
          MediaStore.MediaColumns.RELATIVE_PATH,
          (entry.image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES)
              + "/RAKOTO Player");
      values.put(MediaStore.MediaColumns.IS_PENDING, 1);
      Uri collection =
          entry.image
              ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
              : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
      Uri output = resolver.insert(collection, values);
      if (output == null) throw new IOException("Galerie indisponible");
      SharedPreferences journal = context.getSharedPreferences("rakoto_pending_exports", 0);
      Set<String> pending = new HashSet<>(journal.getStringSet("uris", Collections.emptySet()));
      pending.add(output.toString());
      boolean published = false;
      try {
        if (!journal.edit().putStringSet("uris", pending).commit())
          throw new IOException("Journal d’export indisponible");
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(output, "w")) {
          if (fd == null) throw new IOException("Impossible d’écrire dans la galerie");
          FileOutputStream stream = new FileOutputStream(fd.getFileDescriptor());
          vault.exportTo(entry, stream, progress);
          stream.flush();
          fd.getFileDescriptor().sync();
        }
        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        if (resolver.update(output, values, null, null) != 1)
          throw new IOException("Publication dans la galerie impossible");
        published = true;
      } finally {
        if (!published) resolver.delete(output, null, null);
        pending.remove(output.toString());
        journal.edit().putStringSet("uris", pending).commit();
      }
    } else {
      File folder =
          new File(
              Environment.getExternalStoragePublicDirectory(
                  entry.image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES),
              "RAKOTO Player");
      if (!folder.exists() && !folder.mkdirs())
        throw new IOException("Dossier de galerie inaccessible");
      File complete = new File(folder, name);
      int dot = name.lastIndexOf('.');
      String stem = dot > 0 ? name.substring(0, dot) : name,
          extension = dot > 0 ? name.substring(dot) : "";
      int suffix = 1;
      while (complete.exists())
        complete = new File(folder, stem + " (" + (suffix++) + ")" + extension);
      File partial = new File(folder, ".rakoto-" + UUID.randomUUID() + ".partial");
      SharedPreferences journal = context.getSharedPreferences("rakoto_pending_exports", 0);
      Set<String> partials = new HashSet<>(journal.getStringSet("files", Collections.emptySet()));
      partials.add(partial.getAbsolutePath());
      if (!journal.edit().putStringSet("files", partials).commit())
        throw new IOException("Journal d’export indisponible");
      boolean published = false;
      try {
        try (FileOutputStream output = new FileOutputStream(partial)) {
          vault.exportTo(entry, output, progress);
          output.getFD().sync();
        }
        if (complete.exists())
          throw new IOException(
              "Un fichier de même nom vient d’être créé. Réessayez pour préserver les deux"
                  + " fichiers.");
        if (!partial.renameTo(complete))
          throw new IOException("Publication dans la galerie impossible");
        published = true;
        MediaScannerConnection.scanFile(
            context, new String[] {complete.getAbsolutePath()}, new String[] {mime}, null);
      } finally {
        if (!published) partial.delete();
        partials.remove(partial.getAbsolutePath());
        journal.edit().putStringSet("files", partials).commit();
      }
    }
  }

  static void cleanup(Context context) {
    SharedPreferences local = context.getSharedPreferences("rakoto_pending_exports", 0);
    Set<String> partials = new HashSet<>(local.getStringSet("files", Collections.emptySet()));
    for (String path : new HashSet<>(partials))
      try {
        File file = new File(path);
        String directory = file.getCanonicalFile().getParent();
        File
            pictures =
                new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "RAKOTO Player"),
            movies =
                new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "RAKOTO Player");
        if ((pictures.getCanonicalPath().equals(directory)
                || movies.getCanonicalPath().equals(directory))
            && file.getName().matches("\\.rakoto-[a-f0-9-]{36}\\.partial")
            && (!file.exists() || file.delete())) partials.remove(path);
      } catch (Exception ignored) {
      }
    local.edit().putStringSet("files", partials).apply();
    if (Build.VERSION.SDK_INT < 29) return;
    SharedPreferences journal = context.getSharedPreferences("rakoto_pending_exports", 0);
    Set<String> pending = new HashSet<>(journal.getStringSet("uris", Collections.emptySet()));
    for (String text : new HashSet<>(pending))
      try {
        Uri uri = Uri.parse(text);
        if (!"media".equals(uri.getAuthority())) continue;
        try (Cursor cursor =
            context
                .getContentResolver()
                .query(uri, new String[] {MediaStore.MediaColumns.IS_PENDING}, null, null, null)) {
          if (cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 1)
            context.getContentResolver().delete(uri, null, null);
        }
        pending.remove(text);
      } catch (Exception ignored) {
      }
    journal.edit().putStringSet("uris", pending).apply();
  }
}
