package com.rakotoplayer.android;

import android.content.Context;
import android.os.*;
import android.os.storage.StorageManager;
import android.system.*;
import androidx.annotation.RequiresApi;
import java.io.*;

/** A seekable read-only native descriptor. No decrypted video is written on Android 8+. */
@RequiresApi(26)
final class VaultProxy {
  static ParcelFileDescriptor open(Context context, VaultFile.Reader reader) throws IOException {
    HandlerThread thread = new HandlerThread("RAKOTO-vault-reader");
    thread.start();
    try {
      return context
          .getSystemService(StorageManager.class)
          .openProxyFileDescriptor(
              ParcelFileDescriptor.MODE_READ_ONLY,
              new ProxyFileDescriptorCallback() {
                @Override
                public long onGetSize() {
                  return reader.size;
                }

                @Override
                public int onRead(long offset, int size, byte[] data) throws ErrnoException {
                  try {
                    return reader.read(offset, data, size);
                  } catch (Exception failure) {
                    throw new ErrnoException("lecture du coffre", OsConstants.EIO);
                  }
                }

                @Override
                public void onRelease() {
                  try {
                    reader.close();
                  } catch (IOException ignored) {
                  } finally {
                    thread.quitSafely();
                  }
                }
              },
              new Handler(thread.getLooper()));
    } catch (IOException | RuntimeException failure) {
      try {
        reader.close();
      } catch (IOException ignored) {
      }
      thread.quitSafely();
      throw failure;
    }
  }
}
