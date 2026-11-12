package com.rakotoplayer.android;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.security.keystore.*;
import android.util.*;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

/** Device-bound encrypted vault. PINs, clear filenames and clear videos are not stored. */
public final class Vault {
  private static final String ALIAS = "RAKOTO.Vault.DeviceKey.v2";
  private final Context context;
  private final SharedPreferences settings;
  private final File directory;
  private byte[] master;
  private long generation;

  public interface Progress {
    void changed(String stage, long done, long total);
  }

  public Vault(Context context) {
    this.context = context.getApplicationContext();
    settings = this.context.getSharedPreferences("rakoto_vault", Context.MODE_PRIVATE);
    directory = new File(this.context.getFilesDir(), "vault");
    if (!directory.exists() && !directory.mkdirs())
      throw new IllegalStateException("Coffre indisponible");
  }

  public boolean configured() {
    return settings.contains("envelope");
  }

  public synchronized boolean unlocked() {
    return master != null;
  }

  public synchronized long generation() {
    return generation;
  }

  public synchronized void lock() {
    if (master != null) Arrays.fill(master, (byte) 0);
    master = null;
    generation++;
  }

  private synchronized byte[] key() {
    if (master == null) throw new IllegalStateException("Déverrouillez le coffre");
    return master.clone();
  }

  private static byte[] random(int length) {
    byte[] value = new byte[length];
    new SecureRandom().nextBytes(value);
    return value;
  }

  private SecretKey deviceKey(boolean create) throws GeneralSecurityException, IOException {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
    if (!create)
      throw new GeneralSecurityException("La clé du coffre est indisponible sur cet appareil");
    KeyGenerator generator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(
        new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
    return generator.generateKey();
  }

  private static byte[] seal(SecretKey key, byte[] input) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key);
    cipher.updateAAD("RAKOTO.Vault.v2".getBytes(StandardCharsets.US_ASCII));
    byte[] data = cipher.doFinal(input),
        nonce = cipher.getIV(),
        output = new byte[12 + data.length];
    System.arraycopy(nonce, 0, output, 0, 12);
    System.arraycopy(data, 0, output, 12, data.length);
    return output;
  }

  private static byte[] unseal(SecretKey key, byte[] input) throws GeneralSecurityException {
    if (input.length < 28) throw new GeneralSecurityException("Données chiffrées invalides");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(input, 12)));
    cipher.updateAAD("RAKOTO.Vault.v2".getBytes(StandardCharsets.US_ASCII));
    return cipher.doFinal(input, 12, input.length - 12);
  }

  private static SecretKey pinKey(char[] pin, byte[] salt, String algorithm)
      throws GeneralSecurityException {
    PBEKeySpec spec = new PBEKeySpec(pin, salt, 600000, 256);
    byte[] derived = null;
    try {
      derived = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
      return new SecretKeySpec(derived, "AES");
    } finally {
      spec.clearPassword();
      if (derived != null) Arrays.fill(derived, (byte) 0);
    }
  }

  private void protect(char[] pin, byte[] key) throws Exception {
    if (pin.length != 4) throw new IllegalArgumentException("Le code doit contenir 4 chiffres");
    for (char c : pin)
      if (c < '0' || c > '9') throw new IllegalArgumentException("Chiffres uniquement");
    byte[] salt = random(16);
    String algorithm = Build.VERSION.SDK_INT >= 26 ? "PBKDF2WithHmacSHA256" : "PBKDF2WithHmacSHA1";
    byte[] inner = seal(pinKey(pin, salt, algorithm), key);
    try {
      byte[] envelope = seal(deviceKey(true), inner);
      if (!settings
          .edit()
          .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
          .putString("algorithm", algorithm)
          .putString("envelope", Base64.encodeToString(envelope, Base64.NO_WRAP))
          .putInt("failures", 0)
          .putLong("next_attempt", 0)
          .commit()) throw new IOException("Enregistrement du code impossible");
    } finally {
      Arrays.fill(inner, (byte) 0);
    }
  }

  public void create(char[] pin) throws Exception {
    if (configured()) throw new IllegalStateException("Un code existe déjà");
    long expected = generation();
    byte[] created = random(32);
    try {
      writeCatalog(created, new ArrayList<>());
      protect(pin, created);
      synchronized (this) {
        if (generation != expected)
          throw new IllegalStateException("Code créé. Déverrouillez le coffre pour continuer.");
        master = created.clone();
        generation++;
      }
    } finally {
      Arrays.fill(created, (byte) 0);
      Arrays.fill(pin, '\0');
    }
  }

  public void unlock(char[] pin) throws Exception {
    long expected = generation();
    byte[] inner = null, opened = null;
    try {
      if (!configured()) throw new IllegalStateException("Créez le code du coffre");
      long wait = settings.getLong("next_attempt", 0) - System.currentTimeMillis();
      if (wait > 0)
        throw new IllegalStateException("Réessayez dans " + ((wait + 999) / 1000) + " secondes");
      // Persist before checking, so restarting the process does not reset attempts.
      int failures = settings.getInt("failures", 0) + 1;
      long delay =
          failures >= 5 ? Math.min(3600000L, 30000L * (1L << Math.min(7, failures - 5))) : 0;
      if (!settings
          .edit()
          .putInt("failures", failures)
          .putLong("next_attempt", delay > 0 ? System.currentTimeMillis() + delay : 0)
          .commit()) throw new IOException("Contrôle du code indisponible");
      inner =
          unseal(
              deviceKey(false), Base64.decode(settings.getString("envelope", ""), Base64.NO_WRAP));
      opened =
          unseal(
              pinKey(
                  pin,
                  Base64.decode(settings.getString("salt", ""), Base64.NO_WRAP),
                  settings.getString("algorithm", "PBKDF2WithHmacSHA256")),
              inner);
      readCatalog(opened);
      if (!settings.edit().putInt("failures", 0).putLong("next_attempt", 0).commit())
        throw new IOException("Enregistrement du déverrouillage impossible");
      synchronized (this) {
        if (generation != expected) throw new IllegalStateException("Déverrouillage annulé");
        lock();
        master = opened.clone();
      }
    } catch (AEADBadTagException wrong) {
      throw new IllegalArgumentException(
          "Code incorrect. "
              + (settings.getInt("failures", 0) >= 5
                  ? "Veuillez patienter avant de réessayer."
                  : ""));
    } finally {
      Arrays.fill(pin, '\0');
      if (inner != null) Arrays.fill(inner, (byte) 0);
      if (opened != null) Arrays.fill(opened, (byte) 0);
    }
  }

  public void changePin(char[] pin) throws Exception {
    byte[] key = key();
    try {
      protect(pin, key);
    } finally {
      Arrays.fill(key, (byte) 0);
      Arrays.fill(pin, '\0');
    }
  }

  private AtomicFile catalog() {
    return new AtomicFile(new File(directory, "catalog.v2"));
  }

  private synchronized List<MediaEntry> readCatalog(byte[] key) throws Exception {
    byte[] clear = unseal(new SecretKeySpec(key, "AES"), catalog().readFully());
    try {
      JSONArray json = new JSONArray(new String(clear, StandardCharsets.UTF_8));
      List<MediaEntry> entries = new ArrayList<>();
      for (int i = 0; i < json.length(); i++) {
        MediaEntry e = MediaEntry.from(json.getJSONObject(i));
        if (!e.isPrivate()) throw new IOException("Entrée privée invalide");
        file(e);
        entries.add(e);
      }
      return entries;
    } finally {
      Arrays.fill(clear, (byte) 0);
    }
  }

  private synchronized void writeCatalog(byte[] key, List<MediaEntry> entries) throws Exception {
    JSONArray array = new JSONArray();
    for (MediaEntry e : entries) array.put(e.json());
    byte[] clear = array.toString().getBytes(StandardCharsets.UTF_8), encrypted;
    try {
      encrypted = seal(new SecretKeySpec(key, "AES"), clear);
    } finally {
      Arrays.fill(clear, (byte) 0);
    }
    AtomicFile atomic = catalog();
    FileOutputStream output = null;
    try {
      output = atomic.startWrite();
      output.write(encrypted);
      atomic.finishWrite(output);
      syncDirectory();
    } catch (Exception failure) {
      if (output != null) atomic.failWrite(output);
      throw failure;
    }
  }

  private void syncDirectory() throws Exception {
    java.io.FileDescriptor folder =
        android.system.Os.open(directory.getAbsolutePath(), android.system.OsConstants.O_RDONLY, 0);
    try {
      android.system.Os.fsync(folder);
    } finally {
      android.system.Os.close(folder);
    }
  }

  private File file(MediaEntry entry) {
    String id = Uri.parse(entry.uri).getHost();
    if (id == null || !UUID.fromString(id).toString().equals(id))
      throw new IllegalArgumentException("Identifiant du coffre invalide");
    File modern = new File(directory, id + (entry.image ? ".rpi" : ".rpv"));
    return modern.exists() ? modern : new File(directory, id + ".vlt");
  }

  public List<MediaEntry> entries() throws Exception {
    byte[] key = key();
    try {
      List<MediaEntry> entries = readCatalog(key);
      boolean renamed = false;
      for (MediaEntry entry : entries) {
        File old = file(entry);
        if (old.getName().endsWith(".vlt")) {
          File modern =
              new File(directory, Uri.parse(entry.uri).getHost() + (entry.image ? ".rpi" : ".rpv"));
          if (old.exists() && !modern.exists() && old.renameTo(modern)) renamed = true;
        }
      }
      if (renamed) syncDirectory();
      return entries;
    } finally {
      Arrays.fill(key, (byte) 0);
    }
  }

  /** Associates a beta-3 copy only after proving the selected original has identical bytes. */
  public MediaEntry linkOriginal(MediaEntry entry, Uri original, Progress progress)
      throws Exception {
    long expected = generation();
    MessageDigest encrypted = MessageDigest.getInstance("SHA-256");
    OutputStream sink =
        new OutputStream() {
          @Override
          public void write(int value) {}

          @Override
          public void write(byte[] value, int offset, int length) {}
        };
    exportTo(
        entry,
        new DigestOutputStream(sink, encrypted),
        (stage, done, total) -> progress.changed("Vérification du coffre", done, total));
    MessageDigest source = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[VaultFile.CHUNK];
    long size = 0;
    try (InputStream input = context.getContentResolver().openInputStream(original)) {
      if (input == null) throw new IOException("Original inaccessible");
      int read;
      while ((read = input.read(buffer)) != -1) {
        if (Thread.currentThread().isInterrupted() || !unlocked() || generation() != expected)
          throw new InterruptedIOException("Association annulée");
        source.update(buffer, 0, read);
        size += read;
        progress.changed("Vérification de l’original", size, entry.size);
      }
    } finally {
      Arrays.fill(buffer, (byte) 0);
    }
    byte[] digest = source.digest();
    if (size != entry.size || !MessageDigest.isEqual(encrypted.digest(), digest))
      throw new IOException(
          "Ce fichier ne correspond pas au contenu chiffré. Aucun original n’a été supprimé.");
    byte[] secret = key();
    try {
      synchronized (this) {
        if (generation() != expected) throw new IOException("Coffre verrouillé");
        List<MediaEntry> entries = readCatalog(secret);
        for (MediaEntry item : entries)
          if (item.uri.equals(entry.uri)) {
            item.originalUri = original.toString();
            item.originalDigest = hex(digest);
            item.moved = false;
            writeCatalog(secret, entries);
            return item;
          }
        throw new IOException("Fichier privé introuvable");
      }
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
  }

  public List<MediaEntry> importMedia(List<Uri> uris, Progress progress) throws Exception {
    byte[] key = key();
    List<MediaEntry> imported = new ArrayList<>();
    try {
      List<MediaEntry> catalog = readCatalog(key);
      for (Uri uri : uris) {
        if (Thread.currentThread().isInterrupted())
          throw new InterruptedIOException("Import annulé");
        MediaEntry original = MediaEntry.read(context, uri);
        MediaEntry pending = null;
        for (MediaEntry entry : catalog)
          if (uri.toString().equals(entry.originalUri)) {
            pending = entry;
            break;
          }
        if (pending != null) {
          try {
            verifyOriginal(pending, progress);
            imported.add(pending);
            continue;
          } catch (InterruptedIOException cancelled) {
            throw cancelled;
          } catch (IOException | GeneralSecurityException changed) {
            // Keep the old encrypted version; a freshly verified import owns the current source.
            pending.originalUri = "";
            pending.originalDigest = "";
            pending.moved = false;
          }
        }
        if (!original.image) MediaScanner.duration(context, original);
        if (!original.video && !original.image)
          throw new IOException("Sélectionnez une vidéo ou une image : " + original.name);
        String id = UUID.randomUUID().toString();
        File partial = new File(directory, id + ".part"),
            complete = new File(directory, id + (original.image ? ".rpi" : ".rpv"));
        boolean saved = false;
        try {
          long size;
          MessageDigest digest = MessageDigest.getInstance("SHA-256");
          try (InputStream source = context.getContentResolver().openInputStream(uri)) {
            InputStream input = source == null ? null : new DigestInputStream(source, digest);
            if (input == null) throw new IOException("Vidéo inaccessible");
            size =
                VaultFile.encrypt(
                    input,
                    partial,
                    key,
                    done ->
                        progress.changed("Chiffrement · " + original.name, done, original.size));
          }
          try (VaultFile.Reader reader = new VaultFile.Reader(partial, key, size)) {
            reader.verify(done -> progress.changed("Vérification · " + original.name, done, size));
          }
          if (!partial.renameTo(complete))
            throw new IOException("Enregistrement de la vidéo impossible");
          MediaEntry item = new MediaEntry("rakoto-vault://" + id, original.name);
          item.title = original.title;
          item.video = original.video;
          item.image = original.image;
          item.source = "vault";
          item.mime = original.mime;
          item.size = size;
          item.durationMs = original.durationMs;
          item.width = original.width;
          item.height = original.height;
          item.originalUri = uri.toString();
          item.originalDigest = hex(digest.digest());
          item.folder = "Coffre";
          catalog.add(item);
          writeCatalog(key, catalog);
          saved = true;
          imported.add(item);
        } finally {
          if (partial.exists()) partial.delete();
          if (!saved && complete.exists()) complete.delete();
        }
      }
      return imported;
    } finally {
      Arrays.fill(key, (byte) 0);
    }
  }

  private static String hex(byte[] value) {
    StringBuilder text = new StringBuilder(value.length * 2);
    for (byte b : value) text.append(String.format(Locale.ROOT, "%02x", b & 255));
    return text.toString();
  }

  /** Recheck the encrypted item and source bytes before a destructive source operation. */
  public void verifyOriginal(MediaEntry entry, Progress progress) throws Exception {
    if (entry.originalUri.isEmpty() || !entry.originalDigest.matches("[a-f0-9]{64}"))
      throw new IOException("Aucun original associé à ce fichier");
    long generation = generation();
    byte[] secret = key();
    try (VaultFile.Reader reader = new VaultFile.Reader(file(entry), secret, entry.size)) {
      reader.verify(
          done -> {
            if (!unlocked() || generation != generation())
              throw new InterruptedIOException("Coffre verrouillé");
            progress.changed("Vérification du coffre", done, entry.size);
          });
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[VaultFile.CHUNK];
    long done = 0;
    try (InputStream source =
        context.getContentResolver().openInputStream(Uri.parse(entry.originalUri))) {
      if (source == null) throw new IOException("Original inaccessible");
      int read;
      while ((read = source.read(buffer)) != -1) {
        if (Thread.currentThread().isInterrupted() || !unlocked() || generation != generation())
          throw new InterruptedIOException("Vérification annulée");
        digest.update(buffer, 0, read);
        done += read;
        progress.changed("Vérification de l’original", done, entry.size);
      }
    } finally {
      Arrays.fill(buffer, (byte) 0);
    }
    if (done != entry.size
        || !MessageDigest.isEqual(digest.digest(), decodeHex(entry.originalDigest)))
      throw new IOException(
          "L’original a changé. Il est conservé ; réimportez sa nouvelle version.");
  }

  private static byte[] decodeHex(String value) {
    byte[] bytes = new byte[value.length() / 2];
    for (int i = 0; i < bytes.length; i++)
      bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    return bytes;
  }

  public synchronized void moved(MediaEntry entry) throws Exception {
    byte[] secret = key();
    try {
      List<MediaEntry> entries = readCatalog(secret);
      boolean found = false;
      for (MediaEntry item : entries)
        if (item.uri.equals(entry.uri) && item.originalUri.equals(entry.originalUri)) {
          item.originalUri = "";
          item.originalDigest = "";
          item.moved = true;
          found = true;
        }
      if (!found) throw new IOException("Fichier privé introuvable");
      writeCatalog(secret, entries);
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
  }

  /** Authenticated export; the caller publishes the output only after this returns. */
  public void exportTo(MediaEntry entry, OutputStream output, Progress progress) throws Exception {
    byte[] secret = key();
    long expected = generation();
    try (VaultFile.Reader reader = new VaultFile.Reader(file(entry), secret, entry.size)) {
      reader.copyTo(
          output,
          done -> {
            if (!unlocked() || expected != generation())
              throw new InterruptedIOException("Export annulé : coffre verrouillé");
            progress.changed("Restauration", done, entry.size);
          });
      if (!unlocked() || expected != generation())
        throw new InterruptedIOException("Export annulé : coffre verrouillé");
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
  }

  public void delete(MediaEntry entry) throws Exception {
    byte[] key = key();
    try {
      List<MediaEntry> entries = readCatalog(key);
      for (int i = entries.size() - 1; i >= 0; i--)
        if (entries.get(i).uri.equals(entry.uri)) entries.remove(i);
      writeCatalog(key, entries);
      File file = file(entry);
      if (file.exists() && !file.delete())
        throw new IOException("La copie chiffrée n’a pas pu être effacée");
    } finally {
      Arrays.fill(key, (byte) 0);
    }
  }

  public ParcelFileDescriptor open(MediaEntry entry) throws Exception {
    byte[] key = key();
    VaultFile.Reader reader = null;
    try {
      reader = new VaultFile.Reader(file(entry), key, entry.size);
      if (Build.VERSION.SDK_INT >= 26) {
        ParcelFileDescriptor descriptor = VaultProxy.open(context, reader);
        reader = null;
        return descriptor;
      }
      // Android 6/7: unlink the private temporary file before writing any clear data.
      File temporary = File.createTempFile("vault-play-", ".tmp", context.getCacheDir());
      ParcelFileDescriptor descriptor = null;
      try (RandomAccessFile target = new RandomAccessFile(temporary, "rw")) {
        descriptor = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY);
        if (!temporary.delete()) throw new IOException("Lecture privée indisponible");
        byte[] buffer = new byte[VaultFile.CHUNK];
        try {
          long offset = 0;
          while (offset < reader.size) {
            int read = reader.read(offset, buffer, buffer.length);
            target.write(buffer, 0, read);
            offset += read;
          }
        } finally {
          Arrays.fill(buffer, (byte) 0);
        }
        ParcelFileDescriptor ready = descriptor;
        descriptor = null;
        return ready;
      } finally {
        if (descriptor != null) descriptor.close();
        if (temporary.exists()) temporary.delete();
      }
    } finally {
      Arrays.fill(key, (byte) 0);
      if (reader != null) reader.close();
    }
  }
}
