package com.ilham.shelf;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PREFS = "shelf_server";
    private static final String KEY_URL = "url";
    private static final int FILE_CHOOSER = 1001;
    private static final int COVER_PICK = 1002;

    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final int DESKTOP_WIDTH_PX = 1024;

    private SharedPreferences prefs;
    private ShelfDatabase shelfDb;
    private File coversDir;
    private String serverBaseUrl;

    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;

    private LinearLayout offlineListContainer;
    private ImageView pendingCoverPreview;   // the open dialog's cover ImageView, while a dialog is showing
    private String pendingCoverPickedPath;   // a cover just picked from the gallery, awaiting Save

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        shelfDb = new ShelfDatabase(this);
        coversDir = new File(getFilesDir(), "covers");
        coversDir.mkdirs();

        String saved = prefs.getString(KEY_URL, null);
        if (saved == null || saved.trim().isEmpty()) {
            showServerSetup();
        } else {
            startApp(saved);
        }
    }

    /** Checks the server, pushes any offline edits, refreshes the cache, then shows the right screen. */
    private void startApp(String url) {
        serverBaseUrl = url;
        showLoading();
        SyncManager.syncInBackground(url, shelfDb, coversDir, this::onSyncFinished);
    }

    private void onSyncFinished(boolean online) {
        if (online) {
            showWebView(serverBaseUrl);
        } else {
            showOfflineList();
        }
    }

    // ---------- first-time server setup ----------

    private void showServerSetup() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(40), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(27, 28, 34));

        TextView title = text("Shelf", 32, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2));

        TextView subtitle = text("Connect to your Shelf server", 16, Color.rgb(167, 168, 176));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = lp(-1, -2);
        sp.setMargins(0, dp(8), 0, dp(32));
        root.addView(subtitle, sp);

        EditText host = field("Server IP or hostname", "192.168.1.20");
        root.addView(host, lp(-1, -2));

        EditText port = field("Port", "5050");
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams pp = lp(-1, -2);
        pp.setMargins(0, dp(12), 0, dp(12));
        root.addView(port, pp);

        Button connect = new Button(this);
        connect.setText("CONNECT");
        connect.setTextColor(Color.WHITE);
        connect.setOnClickListener(v -> connect(host, port));
        root.addView(connect, lp(-1, dp(52)));

        TextView hint = text("Example: 192.168.1.20:5050\nThe server address is saved on this phone.", 14, Color.rgb(167, 168, 176));
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = lp(-1, -2);
        hp.setMargins(0, dp(22), 0, 0);
        root.addView(hint, hp);

        setContentView(root);
    }

    private void connect(EditText host, EditText port) {
        String h = host.getText().toString().trim();
        String p = port.getText().toString().trim();
        if (h.isEmpty()) { host.setError("Enter an IP or hostname"); return; }
        if (p.isEmpty()) { port.setError("Enter a port"); return; }

        int portNumber;
        try { portNumber = Integer.parseInt(p); }
        catch (NumberFormatException e) { port.setError("Invalid port"); return; }
        if (portNumber < 1 || portNumber > 65535) { port.setError("Port must be 1–65535"); return; }

        String url = h.matches("^https?://.*") ? h : "http://" + h;
        if (!url.matches("^https?://.*:[0-9]+(/.*)?$")) {
            url = url.replaceAll("/+$", "") + ":" + portNumber;
        }
        url = url.replaceAll("/+$", "");
        prefs.edit().putString(KEY_URL, url).apply();
        startApp(url);
    }

    // ---------- loading screen ----------

    private void showLoading() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(27, 28, 34));

        TextView label = text("Connecting to Shelf…", 16, Color.rgb(167, 168, 176));
        label.setGravity(Gravity.CENTER);
        root.addView(label, lp(-1, -2));

        setContentView(root);
    }

    // ---------- online: the real website, in desktop-site mode ----------

    @SuppressLint("SetJavaScriptEnabled")
    private void showWebView(String url) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(27, 28, 34));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(27, 28, 34));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Desktop-site mode: request and render the page the way desktop Chrome would.
        s.setUserAgentString(DESKTOP_USER_AGENT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        // WebView only computes its zoom-to-fit scale once, at the very first
        // layout pass (before onPageFinished's JS widens the page to 1024).
        // Without this, it stays at the narrow-layout scale even after the
        // page widens, which is why it looked "zoomed in" until pinched.
        // Pre-set the scale it'll need once the page is actually 1024 wide.
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int scalePercent = Math.max(20, Math.min(100, (screenWidthDp * 100) / DESKTOP_WIDTH_PX));
        webView.setInitialScale(scalePercent);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String current = prefs.getString(KEY_URL, "");
                String baseHost = Uri.parse(current).getHost();
                if (uri.getHost() != null && uri.getHost().equalsIgnoreCase(baseHost)) return false;
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(i);
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // The site's own <meta viewport> forces mobile-width layout, which
                // otherwise overrides useWideViewPort/loadWithOverviewMode below.
                // Overwrite it after each load so the page lays out at desktop
                // width, then WebView zooms out to fit \u2014 the same trick Chrome's
                // "Desktop site" toggle uses.
                view.evaluateJavascript(
                        "(function() {" +
                        "var meta = document.querySelector('meta[name=viewport]');" +
                        "if (!meta) {" +
                        "  meta = document.createElement('meta');" +
                        "  meta.setAttribute('name', 'viewport');" +
                        "  document.getElementsByTagName('head')[0].appendChild(meta);" +
                        "}" +
                        "meta.setAttribute('content', 'width=" + DESKTOP_WIDTH_PX + ", initial-scale=1');" +
                        "})()",
                        null
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                progress.setProgress(newProgress);
            }

            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try { startActivityForResult(intent, FILE_CHOOSER); }
                catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        webView.loadUrl(url);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else if (requestCode == COVER_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handlePickedCover(data.getData());
        }
    }

    private void handlePickedCover(Uri picked) {
        try {
            File dest = new File(coversDir, "pick_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(picked);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) throw new IOException("Couldn't open picked image");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            pendingCoverPickedPath = dest.getAbsolutePath();
            if (pendingCoverPreview != null) {
                pendingCoverPreview.setImageBitmap(decodeCover(pendingCoverPickedPath));
            }
        } catch (IOException e) {
            Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------- offline: native fallback backed by the local cache ----------

    private void showOfflineList() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(27, 28, 34));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView banner = text(
                "Offline — showing your last saved comics. Changes here sync automatically once Shelf's server is back online.",
                14, Color.rgb(193, 68, 59));
        LinearLayout.LayoutParams bp = lp(-1, -2);
        bp.setMargins(0, 0, 0, dp(20));
        root.addView(banner, bp);

        Button retry = new Button(this);
        retry.setText("TRY RECONNECTING");
        retry.setTextColor(Color.WHITE);
        retry.setOnClickListener(v -> startApp(serverBaseUrl));
        LinearLayout.LayoutParams rp = lp(-1, dp(48));
        rp.setMargins(0, 0, 0, dp(16));
        root.addView(retry, rp);

        Button add = new Button(this);
        add.setText("+ ADD COMIC");
        add.setTextColor(Color.WHITE);
        add.setOnClickListener(v -> showEditDialog(null));
        LinearLayout.LayoutParams ap = lp(-1, dp(48));
        ap.setMargins(0, 0, 0, dp(20));
        root.addView(add, ap);

        offlineListContainer = new LinearLayout(this);
        offlineListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(offlineListContainer, lp(-1, -2));

        scroll.addView(root);
        setContentView(scroll);

        refreshOfflineList();
    }

    private void refreshOfflineList() {
        offlineListContainer.removeAllViews();
        List<ShelfDatabase.Comic> comics = shelfDb.getVisibleComics();

        if (comics.isEmpty()) {
            TextView empty = text(
                    "No comics cached yet. Connect to your server at least once, then they'll be available here too.",
                    14, Color.rgb(167, 168, 176));
            offlineListContainer.addView(empty, lp(-1, -2));
            return;
        }

        for (ShelfDatabase.Comic comic : comics) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackgroundColor(Color.rgb(37, 38, 46));
            row.setClickable(true);
            row.setOnClickListener(v -> showEditDialog(comic));

            ImageView thumb = new ImageView(this);
            LinearLayout.LayoutParams thumbParams = lp(dp(48), dp(72));
            thumbParams.setMargins(0, 0, dp(14), 0);
            thumb.setLayoutParams(thumbParams);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap cachedCover = decodeCover(comic.localCoverPath);
            if (cachedCover != null) {
                thumb.setImageBitmap(cachedCover);
            } else {
                thumb.setBackgroundColor(Color.rgb(60, 61, 70));
            }
            row.addView(thumb);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView title = text(comic.title, 16, Color.WHITE);
            textCol.addView(title, lp(-1, -2));

            String chapterLine = (comic.lastChapter == null || comic.lastChapter.isEmpty())
                    ? "No progress logged" : comic.lastChapter;
            TextView chapter = text(chapterLine, 13, Color.rgb(167, 168, 176));
            LinearLayout.LayoutParams cp = lp(-1, -2);
            cp.setMargins(0, dp(4), 0, 0);
            textCol.addView(chapter, cp);

            if (comic.pendingCreate || comic.dirty || comic.coverDirty) {
                TextView pending = text("Not yet synced", 11, Color.rgb(193, 68, 59));
                LinearLayout.LayoutParams pp = lp(-1, -2);
                pp.setMargins(0, dp(6), 0, 0);
                textCol.addView(pending, pp);
            }

            row.addView(textCol, lp(0, -2, 1f));

            LinearLayout.LayoutParams rowParams = lp(-1, -2);
            rowParams.setMargins(0, 0, 0, dp(10));
            offlineListContainer.addView(row, rowParams);
        }
    }

    /** Downsampled decode so gallery-sized photos don't blow up memory for a small thumbnail. */
    private Bitmap decodeCover(String path) {
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists()) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 4;
        return BitmapFactory.decodeFile(path, opts);
    }

    private void showEditDialog(ShelfDatabase.Comic existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(16), dp(20), dp(4));

        ImageView coverPreview = new ImageView(this);
        LinearLayout.LayoutParams coverParams = lp(dp(90), dp(130));
        coverPreview.setLayoutParams(coverParams);
        coverPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap existingCover = existing != null ? decodeCover(existing.localCoverPath) : null;
        if (existingCover != null) {
            coverPreview.setImageBitmap(existingCover);
        } else {
            coverPreview.setBackgroundColor(Color.rgb(60, 61, 70));
        }
        form.addView(coverPreview);
        pendingCoverPreview = coverPreview;
        pendingCoverPickedPath = null;

        Button chooseCover = new Button(this);
        chooseCover.setText("CHOOSE COVER");
        chooseCover.setTextColor(Color.WHITE);
        chooseCover.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            try { startActivityForResult(intent, COVER_PICK); }
            catch (Exception e) { Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show(); }
        });
        LinearLayout.LayoutParams ccp = lp(-1, -2);
        ccp.setMargins(0, dp(8), 0, dp(16));
        form.addView(chooseCover, ccp);

        EditText titleField = field("Title", existing != null ? existing.title : "");
        form.addView(titleField, lp(-1, -2));

        EditText lastChapterField = field("Last chapter read", existing != null ? existing.lastChapter : "");
        LinearLayout.LayoutParams lp1 = lp(-1, -2);
        lp1.setMargins(0, dp(12), 0, 0);
        form.addView(lastChapterField, lp1);

        EditText descriptionField = field("Description (optional)", existing != null ? existing.description : "");
        LinearLayout.LayoutParams lp2 = lp(-1, -2);
        lp2.setMargins(0, dp(12), 0, 0);
        form.addView(descriptionField, lp2);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add comic" : "Edit comic")
                .setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String title = titleField.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "Title can't be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String desc = descriptionField.getText().toString().trim();
                    String chapter = lastChapterField.getText().toString().trim();

                    long targetLocalId;
                    if (existing == null) {
                        targetLocalId = shelfDb.insertLocal(title, desc, chapter);
                    } else {
                        targetLocalId = existing.localId;
                        shelfDb.updateLocalEdit(targetLocalId, title, desc, chapter);
                    }
                    if (pendingCoverPickedPath != null) {
                        shelfDb.setLocalCover(targetLocalId, pendingCoverPickedPath);
                    }
                    refreshOfflineList();
                })
                .setNegativeButton("Cancel", null);

        if (existing != null) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                shelfDb.markDeleted(existing.localId);
                refreshOfflineList();
            });
        }

        builder.setOnDismissListener(d -> {
            pendingCoverPreview = null;
            pendingCoverPickedPath = null;
        });

        builder.show();
    }

    // ---------- small view-building helpers ----------

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(130, 131, 140));
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackgroundColor(Color.rgb(37, 38, 46));
        return e;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void changeServer() {
        prefs.edit().remove(KEY_URL).apply();
        showServerSetup();
    }
}
