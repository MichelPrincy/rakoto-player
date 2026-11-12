package com.rakotoplayer.android;

import android.app.Instrumentation;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** Isolated synthetic data only; never resets or edits the user's vault or original media. */
final class VaultInstrumentationCases {
  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static byte[] read(InputStream input) throws Exception {
    try (InputStream source = input;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[16384];
      int count;
      while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
      return output.toByteArray();
    }
  }

  static String run(Instrumentation test) throws Exception {
    Context base = test.getTargetContext();
    String id = UUID.randomUUID().toString();
    File files = new File(base.getFilesDir(), "synthetic-vault-" + id);
    require(files.mkdirs(), "dossier isolé");
    Context isolated =
        new ContextWrapper(base) {
          @Override
          public Context getApplicationContext() {
            return this;
          }

          @Override
          public File getFilesDir() {
            return files;
          }

          @Override
          public SharedPreferences getSharedPreferences(String name, int mode) {
            return base.getSharedPreferences("synthetic." + id + "." + name, mode);
          }
        };
    File original = new File(files, "RAKOTO-test-" + id + ".png");
    Bitmap picture = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
    picture.eraseColor(Color.rgb(37, 99, 235));
    try (FileOutputStream output = new FileOutputStream(original)) {
      require(picture.compress(Bitmap.CompressFormat.PNG, 100, output), "PNG synthétique");
      output.getFD().sync();
    }
    picture.recycle();
    byte[] bytes = read(new FileInputStream(original));
    Vault vault = new Vault(isolated);
    vault.create(new char[] {'1', '2', '3', '4'});
    try {
      MediaEntry entry =
          vault
              .importMedia(
                  Collections.singletonList(Uri.fromFile(original)), (stage, done, total) -> {})
              .get(0);
      require(entry.image && !entry.video, "image détectée");
      require(original.exists(), "l’import ne supprime rien avant vérification");
      require(
          new File(new File(files, "vault"), Uri.parse(entry.uri).getHost() + ".rpi").isFile(),
          "extension chiffrée rpi");
      try (ParcelFileDescriptor descriptor = vault.open(entry)) {
        Bitmap decoded = BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor());
        require(
            decoded != null && decoded.getWidth() == 3 && decoded.getHeight() == 2,
            "décodage réel depuis le coffre");
        decoded.recycle();
      }
      byte[] changed = bytes.clone();
      changed[changed.length - 1] ^= 1;
      try (FileOutputStream output = new FileOutputStream(original)) {
        output.write(changed);
      }
      boolean rejected = false;
      try {
        vault.verifyOriginal(entry, (stage, done, total) -> {});
      } catch (IOException expected) {
        rejected = true;
      }
      require(rejected && original.exists(), "original modifié conservé");
      try (FileOutputStream output = new FileOutputStream(original)) {
        output.write(bytes);
        output.getFD().sync();
      }
      VerifiedRemoval.verify(() -> vault.verifyOriginal(entry, (stage, done, total) -> {}))
          .remove(original::delete, () -> vault.moved(entry));
      require(!original.exists() && vault.entries().get(0).moved, "déplacement vérifié réel");
      ByteArrayOutputStream restored = new ByteArrayOutputStream();
      vault.exportTo(entry, restored, (stage, done, total) -> {});
      require(Arrays.equals(bytes, restored.toByteArray()), "restauration exacte");
      if (Build.VERSION.SDK_INT >= 29) {
        VaultGallery.restore(isolated, vault, entry, (stage, done, total) -> {});
        Uri created = null;
        try (Cursor cursor =
            base.getContentResolver()
                .query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[] {"_id"},
                    "_display_name=?",
                    new String[] {original.getName()},
                    null)) {
          require(cursor != null && cursor.moveToFirst(), "publication réelle dans la galerie");
          created =
              ContentUris.withAppendedId(
                  MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
        }
        try {
          require(
              Arrays.equals(bytes, read(base.getContentResolver().openInputStream(created))),
              "octets dans la galerie");
        } finally {
          base.getContentResolver().delete(created, null, null);
        }
      }
      vault.delete(entry);
      require(vault.entries().isEmpty(), "retrait du coffre après restauration");
    } finally {
      vault.lock();
    }
    return "Coffre isolé : PNG réel, rpi, modification rejetée, déplacement, décodage privé et"
               + " restauration exacte vérifiés.";
  }
}
