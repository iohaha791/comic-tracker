package com.shelf.app;

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

/** The app's only data store. Everything lives here on the phone; no server involved. */
public class ShelfDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "shelf.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE = "comics";
    private static final String COL_ID = "id";
    private static final String COL_TITLE = "title";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_LAST_CHAPTER = "last_chapter";
    private static final String COL_COVER_PATH = "cover_path";
    private static final String COL_UPDATED_AT = "updated_at";

    public ShelfDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TITLE + " TEXT NOT NULL, " +
                COL_DESCRIPTION + " TEXT, " +
                COL_LAST_CHAPTER + " TEXT, " +
                COL_COVER_PATH + " TEXT, " +
                COL_UPDATED_AT + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // first version; nothing to migrate yet
    }

    public static class Comic {
        public long id;
        public String title;
        public String description;
        public String lastChapter;
        public String coverPath; // absolute path to a locally-stored image file, or null
        public String updatedAt;
    }

    private Comic fromCursor(Cursor c) {
        Comic comic = new Comic();
        comic.id = c.getLong(c.getColumnIndexOrThrow(COL_ID));
        comic.title = c.getString(c.getColumnIndexOrThrow(COL_TITLE));
        comic.description = c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION));
        comic.lastChapter = c.getString(c.getColumnIndexOrThrow(COL_LAST_CHAPTER));
        comic.coverPath = c.getString(c.getColumnIndexOrThrow(COL_COVER_PATH));
        comic.updatedAt = c.getString(c.getColumnIndexOrThrow(COL_UPDATED_AT));
        return comic;
    }

    public List<Comic> getAll() {
        List<Comic> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, null, null, null, null, null,
                COL_TITLE + " COLLATE NOCASE ASC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public Comic getById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) return fromCursor(c);
        }
        return null;
    }

    public long insert(String title, String description, String lastChapter, String coverPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_LAST_CHAPTER, lastChapter);
        cv.put(COL_COVER_PATH, coverPath);
        cv.put(COL_UPDATED_AT, nowText());
        return db.insert(TABLE, null, cv);
    }

    /** coverPath == null means "leave the existing cover as-is". */
    public void update(long id, String title, String description, String lastChapter, String coverPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, title);
        cv.put(COL_DESCRIPTION, description);
        cv.put(COL_LAST_CHAPTER, lastChapter);
        if (coverPath != null) cv.put(COL_COVER_PATH, coverPath);
        cv.put(COL_UPDATED_AT, nowText());
        db.update(TABLE, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    private static String nowText() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
