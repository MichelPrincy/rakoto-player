package com.rakotoplayer.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;

public final class Library {
  final SharedPreferences preferences;

  public Library(Context context) {
    preferences = context.getSharedPreferences("rakoto", Context.MODE_PRIVATE);
  }

  public List<MediaEntry> entries() {
    List<MediaEntry> entries = new ArrayList<>();
    try {
      JSONArray json = new JSONArray(preferences.getString("library", "[]"));
      for (int i = 0; i < json.length(); i++) {
        MediaEntry item = MediaEntry.from(json.getJSONObject(i));
        if (!item.uri.isEmpty()) entries.add(item);
      }
    } catch (Exception ignored) {
    }
    return entries;
  }

  public void save(List<MediaEntry> entries) {
    JSONArray json = new JSONArray();
    for (int i = 0; i < Math.min(entries.size(), 2000); i++) json.put(entries.get(i).json());
    preferences.edit().putString("library", json.toString()).apply();
  }

  public void addAll(List<MediaEntry> items) {
    List<MediaEntry> all = entries();
    Set<String> known = new HashSet<>();
    for (MediaEntry e : all) known.add(e.uri);
    for (MediaEntry e : items) if (known.add(e.uri)) all.add(e);
    save(all);
  }

  public void update(MediaEntry item) {
    List<MediaEntry> all = entries();
    for (int i = 0; i < all.size(); i++)
      if (all.get(i).uri.equals(item.uri)) {
        all.set(i, item);
        save(all);
        return;
      }
  }

  public void remove(String uri) {
    List<MediaEntry> all = entries();
    for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).uri.equals(uri)) all.remove(i);
    save(all);
  }

  public boolean favorite(String uri) {
    return preferences.getStringSet("favorites", new HashSet<>()).contains(uri);
  }

  public void toggleFavorite(String uri) {
    Set<String> values = new HashSet<>(preferences.getStringSet("favorites", new HashSet<>()));
    if (!values.remove(uri)) values.add(uri);
    preferences.edit().putStringSet("favorites", values).apply();
  }

  public void position(String uri, long time) {
    preferences.edit().putLong("position:" + uri, time).apply();
  }

  public long position(String uri) {
    return preferences.getBoolean("resume", true) ? preferences.getLong("position:" + uri, 0) : 0;
  }
}
