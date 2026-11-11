package com.rakotoplayer.android;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.text.Collator;
import java.util.*;
import org.json.JSONArray;

/** Single persistent catalogue; never silently caps the user's media collection. */
public final class Library {
  final SharedPreferences preferences;
  private final Database database;
  private static Database shared;

  public static final class Playlist {
    public final String id, name;
    public final boolean video;
    public final int count;

    Playlist(String id, String name, boolean video, int count) {
      this.id = id;
      this.name = name;
      this.video = video;
      this.count = count;
    }
  }

  public Library(Context context) {
    Context app = context.getApplicationContext();
    preferences = app.getSharedPreferences("rakoto", Context.MODE_PRIVATE);
    synchronized (Library.class) {
      if (shared == null) shared = new Database(app);
      database = shared;
    }
  }

  private static final String[] COLUMNS = {
    "uri",
    "name",
    "title",
    "artist",
    "mime",
    "size",
    "video",
    "folder",
    "source",
    "duration",
    "added",
    "modified"
  };

  private static final class Database extends SQLiteOpenHelper {
    private final Context context;

    Database(Context context) {
      super(context, "rakoto-library.db", null, 1);
      this.context = context;
      setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(
          "CREATE TABLE media(uri TEXT PRIMARY KEY,name TEXT,title TEXT,artist TEXT,mime TEXT,size"
              + " INTEGER,video INTEGER,folder TEXT,source TEXT,duration INTEGER,added"
              + " INTEGER,modified INTEGER,seen INTEGER DEFAULT 0)");
      db.execSQL("CREATE INDEX media_kind_folder ON media(video,folder)");
      db.execSQL("CREATE TABLE favorites(uri TEXT PRIMARY KEY)");
      db.execSQL(
          "CREATE TABLE playlists(id TEXT PRIMARY KEY,name TEXT NOT NULL,video INTEGER,created"
              + " INTEGER)");
      db.execSQL(
          "CREATE TABLE playlist_items(playlist TEXT,uri TEXT,position INTEGER,PRIMARY"
              + " KEY(playlist,uri))");
      SharedPreferences old = context.getSharedPreferences("rakoto", Context.MODE_PRIVATE);
      try {
        JSONArray items = new JSONArray(old.getString("library", "[]"));
        for (int i = 0; i < items.length(); i++) {
          MediaEntry e = MediaEntry.from(items.getJSONObject(i));
          if (!e.uri.isEmpty() && !e.isPrivate())
            db.insertWithOnConflict("media", null, values(e), SQLiteDatabase.CONFLICT_IGNORE);
        }
      } catch (Exception ignored) {
      }
      for (String uri : old.getStringSet("favorites", Collections.emptySet())) {
        ContentValues v = new ContentValues();
        v.put("uri", uri);
        db.insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_IGNORE);
      }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      throw new IllegalStateException("Migration non définie");
    }
  }

  private static ContentValues values(MediaEntry e) {
    ContentValues v = new ContentValues();
    v.put("uri", e.uri);
    v.put("name", e.name);
    v.put("title", e.title);
    v.put("artist", e.artist);
    v.put("mime", e.mime);
    v.put("size", e.size);
    v.put("video", e.video ? 1 : 0);
    v.put("folder", e.folder);
    v.put("source", e.source);
    v.put("duration", e.durationMs);
    v.put("added", e.addedAt);
    v.put("modified", e.modifiedAt);
    return v;
  }

  private static MediaEntry entry(Cursor c) {
    MediaEntry e = new MediaEntry(c.getString(0), c.getString(1));
    e.title = c.getString(2);
    e.artist = c.getString(3);
    e.mime = c.getString(4);
    e.size = c.getLong(5);
    e.video = c.getInt(6) != 0;
    e.folder = c.getString(7);
    e.source = c.getString(8);
    e.durationMs = c.getLong(9);
    e.addedAt = c.getLong(10);
    e.modifiedAt = c.getLong(11);
    return e;
  }

  public List<MediaEntry> entries() {
    return query(null, null);
  }

  private List<MediaEntry> query(String selection, String[] args) {
    List<MediaEntry> result = new ArrayList<>();
    try (Cursor c =
        database
            .getReadableDatabase()
            .query("media", COLUMNS, selection, args, null, null, "title COLLATE LOCALIZED")) {
      while (c.moveToNext()) result.add(entry(c));
    }
    return result;
  }

  public List<MediaEntry> entries(boolean video, boolean favorites, String playlist) {
    String selection = "video=?";
    List<String> args = new ArrayList<>();
    args.add(video ? "1" : "0");
    if (favorites) selection += " AND uri IN (SELECT uri FROM favorites)";
    if (playlist != null) {
      selection += " AND uri IN (SELECT uri FROM playlist_items WHERE playlist=?)";
      args.add(playlist);
    }
    return query(selection, args.toArray(new String[0]));
  }

  public void save(List<MediaEntry> entries) {
    SQLiteDatabase db = database.getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("media", null, null);
      for (MediaEntry e : entries)
        if (!e.isPrivate())
          db.insertWithOnConflict("media", null, values(e), SQLiteDatabase.CONFLICT_REPLACE);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private void upsert(SQLiteDatabase db, MediaEntry e, long seen) {
    if (e.isPrivate() || e.uri.isEmpty()) return;
    ContentValues v = values(e);
    try (Cursor old =
        db.query(
            "media",
            new String[] {"added", "duration"},
            "uri=?",
            new String[] {e.uri},
            null,
            null,
            null)) {
      if (old.moveToFirst()) {
        v.put("added", old.getLong(0));
        if (e.durationMs <= 0) v.put("duration", old.getLong(1));
      }
    }
    if (seen != 0) v.put("seen", seen);
    int updated = db.update("media", v, "uri=?", new String[] {e.uri});
    if (updated == 0) db.insertOrThrow("media", null, v);
  }

  public void addAll(List<MediaEntry> entries) {
    SQLiteDatabase db = database.getWritableDatabase();
    db.beginTransaction();
    try {
      for (MediaEntry e : entries) upsert(db, e, 0);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void replaceScan(String source, List<MediaEntry> entries, boolean complete) {
    SQLiteDatabase db = database.getWritableDatabase();
    long seen = System.currentTimeMillis();
    db.beginTransaction();
    try {
      for (MediaEntry e : entries) {
        e.source = source;
        upsert(db, e, seen);
      }
      if (complete)
        db.delete("media", "source=? AND seen!=?", new String[] {source, Long.toString(seen)});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void update(MediaEntry item) {
    if (item.isPrivate()) return;
    ContentValues v = new ContentValues();
    v.put("title", item.title);
    v.put("artist", item.artist);
    v.put("video", item.video ? 1 : 0);
    if (item.durationMs > 0) v.put("duration", item.durationMs);
    database.getWritableDatabase().update("media", v, "uri=?", new String[] {item.uri});
  }

  public void remove(String uri) {
    database.getWritableDatabase().delete("media", "uri=?", new String[] {uri});
  }

  public boolean favorite(String uri) {
    try (Cursor c =
        database
            .getReadableDatabase()
            .rawQuery("SELECT 1 FROM favorites WHERE uri=?", new String[] {uri})) {
      return c.moveToFirst();
    }
  }

  public void toggleFavorite(String uri) {
    SQLiteDatabase db = database.getWritableDatabase();
    if (db.delete("favorites", "uri=?", new String[] {uri}) == 0) {
      ContentValues v = new ContentValues();
      v.put("uri", uri);
      db.insertWithOnConflict("favorites", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
  }

  public List<Playlist> playlists(boolean video) {
    List<Playlist> result = new ArrayList<>();
    try (Cursor c =
        database
            .getReadableDatabase()
            .rawQuery(
                "SELECT p.id,p.name,p.video,(SELECT count(*) FROM playlist_items i JOIN media m ON"
                    + " m.uri=i.uri WHERE i.playlist=p.id) FROM playlists p WHERE p.video=? ORDER"
                    + " BY p.name COLLATE LOCALIZED",
                new String[] {video ? "1" : "0"})) {
      while (c.moveToNext())
        result.add(new Playlist(c.getString(0), c.getString(1), c.getInt(2) != 0, c.getInt(3)));
    }
    return result;
  }

  public String createPlaylist(String name, boolean video) {
    String id = UUID.randomUUID().toString();
    ContentValues v = new ContentValues();
    v.put("id", id);
    v.put("name", name.trim());
    v.put("video", video ? 1 : 0);
    v.put("created", System.currentTimeMillis());
    database.getWritableDatabase().insertOrThrow("playlists", null, v);
    return id;
  }

  public void renamePlaylist(String id, String name) {
    ContentValues v = new ContentValues();
    v.put("name", name.trim());
    database.getWritableDatabase().update("playlists", v, "id=?", new String[] {id});
  }

  public void deletePlaylist(String id) {
    SQLiteDatabase db = database.getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("playlist_items", "playlist=?", new String[] {id});
      db.delete("playlists", "id=?", new String[] {id});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void addToPlaylist(String id, String uri) {
    SQLiteDatabase db = database.getWritableDatabase();
    ContentValues v = new ContentValues();
    v.put("playlist", id);
    v.put("uri", uri);
    try (Cursor c =
        db.rawQuery(
            "SELECT COALESCE(MAX(position),-1)+1 FROM playlist_items WHERE playlist=?",
            new String[] {id})) {
      c.moveToFirst();
      v.put("position", c.getLong(0));
    }
    db.insertWithOnConflict("playlist_items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  public void removeFromPlaylist(String id, String uri) {
    database
        .getWritableDatabase()
        .delete("playlist_items", "playlist=? AND uri=?", new String[] {id, uri});
  }

  public void position(String uri, long time) {
    if (!uri.startsWith("rakoto-vault:"))
      preferences.edit().putLong("position:" + uri, time).apply();
  }

  public long position(String uri) {
    return preferences.getBoolean("resume", true) ? preferences.getLong("position:" + uri, 0) : 0;
  }

  public Set<String> folders() {
    return new HashSet<>(preferences.getStringSet("scan_folders", Collections.emptySet()));
  }

  public void addFolder(String uri) {
    Set<String> all = folders();
    all.add(uri);
    preferences.edit().putStringSet("scan_folders", all).apply();
  }

  public static void sort(List<MediaEntry> entries, int order, boolean ascending) {
    Collator collator = Collator.getInstance(Locale.FRENCH);
    Collections.sort(
        entries,
        (a, b) -> {
          int compared;
          if (order == 1) {
            if (a.durationMs == 0 && b.durationMs != 0) return 1;
            if (b.durationMs == 0 && a.durationMs != 0) return -1;
            compared = Long.compare(a.durationMs, b.durationMs);
          } else if (order == 2) compared = Long.compare(a.size, b.size);
          else if (order == 3) compared = Long.compare(a.addedAt, b.addedAt);
          else compared = collator.compare(a.title, b.title);
          if (compared == 0) compared = a.uri.compareTo(b.uri);
          return ascending ? compared : -compared;
        });
  }
}
