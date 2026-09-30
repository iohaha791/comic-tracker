package com.ilham.shelf;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Local cache of comics plus an offline edit queue. Only used when the Shelf
 * server can't be reached: online, the app just shows the website in the
 * WebView as before. This exists so the shelf is still viewable and editable
 * offline, with changes pushed to the server the next time it's reachable.
 */
public class ShelfDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "shelf_cache.db";
    private static final int DB_VERSION = 2;

    private static final String TABLE = "comics";
    private static final String COL_ID = "id";
    private static final String COL_SERVER_ID = "server_id";
    private static final String COL_TITLE = "title";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_LAST_CHAPTER = "last_chapter";
    private static final String COL_COVER_PATH = "cover_path";
    private static final String COL_LOCAL_COVER_PATH = "local_cover_path";
    private static final String COL_COVER_DIRTY = "cover_dirty";
    private static final String COL_UPDATED_AT = "updated_at";
    private static final String COL_DIRTY = "dirty";
    private static final String COL_PENDING_CREATE = "pending_create";
    private static final String COL_DELETED = "deleted";

    public ShelfDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SERVER_ID + " INTEGER, " +
                COL_TITLE + " TEXT NOT NULL, " +
                COL_DESCRIPTION + " TEXT, " +
                COL_LAST_CHAPTER + " TEXT, " +
                COL_COVER_PATH + " TEXT, " +
                COL_LOCAL_COVER_PATH + " TEXT, " +
                COL_COVER_DIRTY + " INTEGER DEFAULT 0, " +
                COL_UPDATED_AT + " TEXT, " +
                COL_DIRTY + " INTEGER DEFAULT 0, " +
                COL_PENDING_CREATE + " INTEGER DEFAULT 0, " +
                COL_DELETED + " INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_LOCAL_COVER_PATH + " TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + COL_COVER_DIRTY + " INTEGER DEFAULT 0");
        }
    }

    /** A single cached/offline-editable comic row. */
    public static class Comic {
        public long localId;
        public Long serverId; // null until it's been created on the server
        public String title;
        public String description;
        public String lastChapter;
        public String coverPath;      // relative server path, e.g. /static/uploads/xyz.jpg
        public String localCoverPath; // absolute path to a locally cached/picked image file
        public boolean coverDirty;    // a new cover was picked offline and still needs uploading
        public String updatedAt;
        public boolean dirty;
        public boolean pendingCreate;
        public boolean deleted;
    }

    private Comic fromCursor(Cursor c) {
        Comic comic = new Comic();
        comic.localId = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        int serverIdIdx = c.getColumnIndexOrThrow(COL_SERVER_ID);
        comic.serverId = c.isNull(serverIdIdx) ? null : c.getLong(serverIdIdx);
        comic.title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
        comic.description = c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION));
        comic.lastChapter = c.getString(c.getColumnIndexOrThrow(COL_LAST_CHAPTER));
        comic.coverPath = c.getString(c.getColumnIndexOrThrow(COL_COVER_PATH));
        comic.localCoverPath = c.getString(c.getColumnIndexOrThrow(COL_LOCAL_COVER_PATH));
        comic.coverDirty = c.getInt(c.getColumnIndexOrThrow(COL_COVER_DIRTY)) != 0;
        comic.updatedAt = c.getString(c.getColumnIndexOrThrow(COL_UPDATED_AT));
        comic.dirty = c.getInt(c.getColumnIndexOrThrow(COL_DIRTY)) != 0;
        comic.pendingCreate = c.getInt(c.getColumnIndexOrThrow(COL_PENDING_CREATE)) != 0;
        comic.deleted = c.getInt(c.getColumnIndexOrThrow(COL_DELETED)) != 0;
        return comic;
    }

    /** Everything the user should see offline (excludes rows queued for deletion). */
    public List<Comic> getVisibleComics() {
        return queryWhere(COL_DELETED + " = 0", COL_TITLE + " COLLATE NOCASE ASC");
    }

    public List<Comic> getPendingCreates() {
        return queryWhere(COL_PENDING_CREATE + " = 1 AND " + COL_DELETED + " = 0", null);
    }

    public List<Comic> getDirtyUpdates() {
        return queryWhere(COL_DIRTY + " = 1 AND " + COL_PENDING_CREATE + " = 0 AND " + COL_DELETED + " = 0", null);
    }

    public List<Comic> getPendingDeletes() {
        return queryWhere(COL_DELETED + " = 1", null);
    }

    /** Rows (already on the server) with a locally-picked cover still waiting to be uploaded. */
    public List<Comic> getPendingCoverUploads() {
        return queryWhere(COL_COVER_DIRTY + " = 1 AND " + COL_PENDING_CREATE + " = 0 AND " + COL_DELETED + " = 0", null);
    }

    private List<Comic> queryWhere(String where, String orderBy) {
        List<Comic> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, null, where, null, null, null, orderBy)) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    /** New comic created while offline. Not yet on the server. */
    public long insertLocal(String title, String description, String lastChapter) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_LAST_CHAPTER, lastChapter);
        cv.put(COL_UPDATED_AT, isoNow());
        cv.put(COL_DIRTY, 0);
        cv.put(COL_PENDING_CREATE, 1);
        return db.insert(TABLE, null, cv);
    }

    /** Edit made offline to a comic that already exists locally (synced or not). */
    public void updateLocalEdit(long localId, String title, String description, String lastChapter) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_LAST_CHAPTER, lastChapter);
        cv.put(COL_UPDATED_AT, isoNow());
        cv.put(COL_DIRTY, 1);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    /** A new cover image was picked (offline, or as part of an offline edit). */
    public void setLocalCover(long localId, String localCoverPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOCAL_COVER_PATH, localCoverPath);
        cv.put(COL_COVER_DIRTY, 1);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    /** Called once a locally-picked cover has been uploaded to the server. */
    public void clearCoverDirty(long localId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_COVER_DIRTY, 0);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    /** Called when a cover image freshly downloaded from the server is cached locally. */
    public void setCachedServerCover(long localId, String localCoverPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOCAL_COVER_PATH, localCoverPath);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    public void markDeleted(long localId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DELETED, 1);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    /** Called after the server has accepted a locally-created comic. */
    public void markCreated(long localId, long serverId, String updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SERVER_ID, serverId);
        cv.put(COL_PENDING_CREATE, 0);
        cv.put(COL_DIRTY, 0);
        if (updatedAt != null) cv.put(COL_UPDATED_AT, updatedAt);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    /** Called after a locally-edited comic has been pushed to the server. */
    public void markSynced(long localId, String updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_DIRTY, 0);
        if (updatedAt != null) cv.put(COL_UPDATED_AT, updatedAt);
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    public void deleteLocalRow(long localId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE, COL_ID + " = ?", new String[]{String.valueOf(localId)});
    }

    /**
     * Upsert a comic pulled fresh from the server into the local cache.
     * Leaves local_cover_path/cover_dirty untouched here; the caller (SyncManager)
     * decides separately whether to (re)download the cover image.
     * Returns the local row id (existing or newly created).
     */
    public long upsertFromServer(long serverId, String title, String description,
                                  String lastChapter, String coverPath, String updatedAt) {
        SQLiteDatabase db = getWritableDatabase();
        long existingLocalId = -1;
        try (Cursor c = db.query(TABLE, new String[]{COL_ID}, COL_SERVER_ID + " = ?",
                new String[]{String.valueOf(serverId)}, null, null, null)) {
            if (c.moveToFirst()) existingLocalId = c.getLong(0);
        }

        ContentValues cv = new ContentValues();
        cv.put(COL_SERVER_ID, serverId);
        cv.put(COL_TITLE, title);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_LAST_CHAPTER, lastChapter);
        cv.put(COL_COVER_PATH, coverPath);
        cv.put(COL_UPDATED_AT, updatedAt);
        cv.put(COL_DIRTY, 0);
        cv.put(COL_PENDING_CREATE, 0);
        cv.put(COL_DELETED, 0);

        if (existingLocalId >= 0) {
            db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(existingLocalId)});
            return existingLocalId;
        } else {
            return db.insert(TABLE, null, cv);
        }
    }

    /** Whether this row currently has a locally-picked cover still awaiting upload. */
    public boolean isCoverDirty(long localId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, new String[]{COL_COVER_DIRTY}, COL_ID + " = ?",
                new String[]{String.valueOf(localId)}, null, null, null)) {
            return c.moveToFirst() && c.getInt(0) != 0;
        }
    }

    /** Drop cached rows whose server-side comic no longer exists (deleted elsewhere, e.g. the website). */
    public void removeGoneFromServer(List<Long> presentServerIds) {
        List<Comic> local = queryWhere(COL_SERVER_ID + " IS NOT NULL AND " + COL_DIRTY + " = 0 AND "
                + COL_PENDING_CREATE + " = 0 AND " + COL_DELETED + " = 0", null);
        for (Comic comic : local) {
            if (comic.serverId != null && !presentServerIds.contains(comic.serverId)) {
                deleteLocalRow(comic.localId);
            }
        }
    }

    private static String isoNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
