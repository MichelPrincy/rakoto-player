package com.rakotoplayer.android;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Chunk-authenticated encryption: bounded memory and seeking without plaintext files. */
public final class VaultFile {
  static final int CHUNK = 256 * 1024, HEADER = 36, TAG = 16;
  private static final byte[] MAGIC = {'R', 'A', 'K', 'O', 'V', 'L', 'T', '2'};

  public interface Progress {
    void bytes(long done) throws IOException;
  }

  private static void cancelled() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted())
      throw new InterruptedIOException("Opération annulée");
  }

  private static byte[] fileKey(byte[] master, byte[] header) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(master, "HmacSHA256"));
    mac.update("RAKOTO.VaultFile.v2".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    mac.update(header, 12, 16);
    return mac.doFinal();
  }

  private static byte[] crypt(
      int mode, byte[] key, byte[] header, long index, byte[] data, int length)
      throws GeneralSecurityException {
    if (index > 0xffffffffL) throw new GeneralSecurityException("Fichier trop volumineux");
    byte[] nonce = ByteBuffer.allocate(12).put(header, 28, 8).putInt((int) index).array();
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
    cipher.updateAAD(header);
    cipher.updateAAD(
        ByteBuffer.allocate(12)
            .putLong(index)
            .putInt(mode == Cipher.ENCRYPT_MODE ? length : length - TAG)
            .array());
    return cipher.doFinal(data, 0, length);
  }

  public static long encrypt(InputStream input, File output, byte[] master, Progress progress)
      throws IOException, GeneralSecurityException {
    byte[] header = ByteBuffer.allocate(HEADER).put(MAGIC).putInt(CHUNK).array();
    byte[] random = new byte[24];
    new SecureRandom().nextBytes(random);
    System.arraycopy(random, 0, header, 12, 24);
    byte[] key = fileKey(master, header), buffer = new byte[CHUNK];
    long total = 0, index = 0;
    try (FileOutputStream target = new FileOutputStream(output)) {
      target.write(header);
      while (true) {
        cancelled();
        int size = 0;
        while (size < buffer.length) {
          int read = input.read(buffer, size, buffer.length - size);
          if (read < 0) break;
          if (read == 0) {
            int one = input.read();
            if (one < 0) break;
            buffer[size++] = (byte) one;
          } else size += read;
          cancelled();
        }
        if (size == 0) break;
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, key, header, index++, buffer, size);
        target.write(encrypted);
        total += size;
        progress.bytes(total);
        if (size < CHUNK) break;
      }
      if (total == 0) throw new IOException("Le fichier est vide");
      target.getFD().sync();
      return total;
    } finally {
      Arrays.fill(key, (byte) 0);
      Arrays.fill(buffer, (byte) 0);
    }
  }

  public static final class Reader implements Closeable {
    private final RandomAccessFile file;
    private final byte[] header = new byte[HEADER], key;
    private byte[] cached;
    private long cachedIndex = -1;
    public final long size;

    public Reader(File path, byte[] master, long expectedSize)
        throws IOException, GeneralSecurityException {
      RandomAccessFile opened = new RandomAccessFile(path, "r");
      byte[] derived = null;
      try {
        opened.readFully(header);
        if (!Arrays.equals(MAGIC, Arrays.copyOf(header, 8))
            || ByteBuffer.wrap(header, 8, 4).getInt() != CHUNK)
          throw new IOException("Format du coffre invalide");
        long payload = opened.length() - HEADER;
        if (payload <= TAG) throw new IOException("Fichier du coffre tronqué");
        long chunks = (payload + CHUNK + TAG - 1) / (CHUNK + TAG);
        long last = payload - (chunks - 1) * (CHUNK + TAG);
        if (last <= TAG) throw new IOException("Dernier bloc invalide");
        size = payload - chunks * TAG;
        if (size != expectedSize) throw new IOException("Taille du coffre incohérente");
        derived = fileKey(master, header);
        file = opened;
        key = derived;
      } catch (IOException | GeneralSecurityException failure) {
        opened.close();
        if (derived != null) Arrays.fill(derived, (byte) 0);
        throw failure;
      }
    }

    private byte[] chunk(long index) throws IOException, GeneralSecurityException {
      if (index == cachedIndex) return cached;
      int length = (int) Math.min(CHUNK, size - index * CHUNK);
      if (length <= 0) throw new EOFException();
      byte[] encrypted = new byte[length + TAG];
      file.seek(HEADER + index * (CHUNK + TAG));
      file.readFully(encrypted);
      byte[] plain = crypt(Cipher.DECRYPT_MODE, key, header, index, encrypted, encrypted.length);
      if (cached != null) Arrays.fill(cached, (byte) 0);
      cached = plain;
      cachedIndex = index;
      return plain;
    }

    public synchronized int read(long offset, byte[] target, int count)
        throws IOException, GeneralSecurityException {
      if (offset < 0 || count < 0 || count > target.length)
        throw new IOException("Position invalide");
      if (offset >= size) return 0;
      int written = 0;
      count = (int) Math.min(count, size - offset);
      while (written < count) {
        cancelled();
        long index = offset / CHUNK;
        byte[] plain = chunk(index);
        int within = (int) (offset % CHUNK),
            length = Math.min(count - written, plain.length - within);
        System.arraycopy(plain, within, target, written, length);
        written += length;
        offset += length;
      }
      return written;
    }

    public void copyTo(OutputStream output, Progress progress)
        throws IOException, GeneralSecurityException {
      byte[] buffer = new byte[CHUNK];
      long position = 0;
      try {
        while (position < size) {
          cancelled();
          int count = read(position, buffer, buffer.length);
          if (count <= 0) throw new EOFException();
          output.write(buffer, 0, count);
          position += count;
          progress.bytes(position);
        }
        output.flush();
      } finally {
        Arrays.fill(buffer, (byte) 0);
      }
    }

    public void verify(Progress progress) throws IOException, GeneralSecurityException {
      byte[] buffer = new byte[CHUNK];
      long position = 0;
      try {
        while (position < size) {
          int read = read(position, buffer, buffer.length);
          if (read <= 0) throw new EOFException();
          position += read;
          progress.bytes(position);
        }
      } finally {
        Arrays.fill(buffer, (byte) 0);
      }
    }

    @Override
    public synchronized void close() throws IOException {
      Arrays.fill(key, (byte) 0);
      if (cached != null) Arrays.fill(cached, (byte) 0);
      file.close();
    }
  }
}
