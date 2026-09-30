package com.ilham.shelf;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks whether the Shelf server is reachable, and if so pushes any offline
 * edits (including locally-picked covers) then refreshes the local cache
 * (including downloading cover images for offline viewing). Runs entirely on
 * a background thread; the callback always fires back on the main thread.
 */
public class SyncManager {

    public interface Callback {
        void onFinished(boolean serverOnline);
    }

    public static void syncInBackground(String baseUrl, ShelfDatabase db, File coversDir, Callback callback) {
        new Thread(() -> {
            boolean online = ApiClient.isServerOnline(baseUrl);
            if (online) {
                try {
                    coversDir.mkdirs();
                    push(baseUrl, db);
                    pull(baseUrl, db, coversDir);
                } catch (Exception e) {
                    // Partial sync failure: keep whatever the cache already has,
                    // and just fall back to offline mode for this launch.
                    online = false;
                }
            }
            boolean finalOnline = online;
            new Handler(Looper.getMainLooper()).post(() -> callback.onFinished(finalOnline));
        }).start();
    }

    private static void push(String baseUrl, ShelfDatabase db) throws Exception {
        for (ShelfDatabase.Comic comic : db.getPendingDeletes()) {
            if (comic.serverId != null) {
                try {
                    ApiClient.deleteComic(baseUrl, comic.serverId);
                } catch (Exception ignored) {
                    // best-effort: still drop it locally so it doesn't linger forever
                }
            }
            db.deleteLocalRow(comic.localId);
        }

        for (ShelfDatabase.Comic comic : db.getPendingCreates()) {
            JSONObject created = ApiClient.createComic(baseUrl, comic.title, comic.description, comic.lastChapter);
            db.markCreated(comic.localId, created.optLong("id"), created.optString("updated_at", null));
        }

        for (ShelfDatabase.Comic comic : db.getDirtyUpdates()) {
            if (comic.serverId == null) continue;
            JSONObject updated = ApiClient.updateComic(baseUrl, comic.serverId, comic.title, comic.description, comic.lastChapter);
            db.markSynced(comic.localId, updated.optString("updated_at", null));
        }

        // Cover uploads run last, once every comic that needs one is guaranteed
        // to have a real server_id (freshly-created ones included).
        for (ShelfDatabase.Comic comic : db.getPendingCoverUploads()) {
            if (comic.serverId == null || comic.localCoverPath == null) continue;
            File imageFile = new File(comic.localCoverPath);
            if (!imageFile.exists()) {
                db.clearCoverDirty(comic.localId);
                continue;
            }
            try {
                ApiClient.uploadCover(baseUrl, comic.serverId, imageFile);
                db.clearCoverDirty(comic.localId);
            } catch (Exception ignored) {
                // leave cover_dirty set; it'll retry on the next successful sync
            }
        }
    }

    private static void pull(String baseUrl, ShelfDatabase db, File coversDir) throws Exception {
        JSONArray comics = ApiClient.listComics(baseUrl);
        List<Long> presentIds = new ArrayList<>();
        if (comics != null) {
            for (int i = 0; i < comics.length(); i++) {
                JSONObject c = comics.getJSONObject(i);
                long serverId = c.getLong("id");
                presentIds.add(serverId);
                String coverPath = c.isNull("cover_path") ? null : c.optString("cover_path", null);

                long localId = db.upsertFromServer(
                        serverId,
                        c.optString("title", ""),
                        c.optString("description", ""),
                        c.optString("last_chapter", ""),
                        coverPath,
                        c.optString("updated_at", null)
                );

                // Don't clobber a cover the user just picked offline and hasn't
                // uploaded yet (rare: only if the earlier upload attempt failed).
                if (coverPath != null && !db.isCoverDirty(localId)) {
                    File dest = new File(coversDir, "cover_" + serverId);
                    try {
                        ApiClient.downloadToFile(baseUrl + coverPath, dest);
                        db.setCachedServerCover(localId, dest.getAbsolutePath());
                    } catch (Exception ignored) {
                        // keep whatever cover (if any) was cached from a previous sync
                    }
                }
            }
        }
        db.removeGoneFromServer(presentIds);
    }
}
