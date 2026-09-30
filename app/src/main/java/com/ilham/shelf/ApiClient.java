package com.ilham.shelf;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Talks to Shelf's small JSON API (/api/...). Every method here blocks on
 * network I/O, so callers must run them off the main thread.
 */
public class ApiClient {
    private static final int TIMEOUT_MS = 4000;

    public static boolean isServerOnline(String baseUrl) {
        try {
            JSONObject result = request("GET", baseUrl + "/api/health", null);
            return "ok".equals(result.optString("status"));
        } catch (Exception e) {
            return false;
        }
    }

    public static JSONArray listComics(String baseUrl) throws IOException, JSONException {
        JSONObject result = request("GET", baseUrl + "/api/comics", null);
        return result.optJSONArray("comics");
    }

    public static JSONObject createComic(String baseUrl, String title, String description,
                                          String lastChapter) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("description", description);
        body.put("last_chapter", lastChapter);
        return request("POST", baseUrl + "/api/comics", body);
    }

    public static JSONObject updateComic(String baseUrl, long serverId, String title,
                                          String description, String lastChapter)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("title", title);
        body.put("description", description);
        body.put("last_chapter", lastChapter);
        return request("PUT", baseUrl + "/api/comics/" + serverId, body);
    }

    public static void deleteComic(String baseUrl, long serverId) throws IOException, JSONException {
        request("DELETE", baseUrl + "/api/comics/" + serverId, null);
    }

    /** Uploads a cover image file for an existing server-side comic. */
    public static JSONObject uploadCover(String baseUrl, long serverId, File imageFile)
            throws IOException, JSONException {
        String boundary = "----ShelfBoundary" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/api/comics/" + serverId + "/cover").openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS * 3); // cover uploads can take a bit longer
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            String filename = imageFile.getName();
            String mimeType = filename.toLowerCase(java.util.Locale.US).endsWith(".png") ? "image/png" : "image/jpeg";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Disposition: form-data; name=\"cover\"; filename=\"" + filename + "\"\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                os.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));

                try (java.io.FileInputStream fis = new java.io.FileInputStream(imageFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = fis.read(buffer)) != -1) os.write(buffer, 0, read);
                }

                os.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(stream);
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + text);
            return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }

    /** Downloads raw bytes (e.g. a cover image) from the server into destFile. */
    public static void downloadToFile(String url, java.io.File destFile) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS * 3);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);

            try (InputStream in = conn.getInputStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static JSONObject request(String method, String urlStr, JSONObject body)
            throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(stream);

            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + ": " + text);
            }
            if (text.trim().isEmpty()) return new JSONObject();
            return new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
