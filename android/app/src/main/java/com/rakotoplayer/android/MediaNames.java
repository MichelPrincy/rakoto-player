package com.rakotoplayer.android;

import java.util.Locale;

/** A descriptor address is a transport detail, never a user-visible track title. */
public final class MediaNames {
  private MediaNames() {}

  public static boolean readable(String value) {
    if (value == null || value.trim().isEmpty()) return false;
    String text = value.trim().toLowerCase(Locale.ROOT);
    return !text.matches("^(fd|file|content|rakoto-vault|vlc|imem|attachment)(://|%3a%2f%2f).*")
        && !text.matches("^fd:(//)?[0-9]+.*")
        && !text.startsWith("/proc/self/fd/");
  }

  public static String title(String selected, String filename, String detected) {
    if (readable(selected)) return selected.trim();
    if (readable(filename)) return filename.trim();
    return readable(detected) ? detected.trim() : "Média local";
  }
}
