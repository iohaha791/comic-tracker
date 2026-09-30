package com.shelf.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DetailActivity extends Activity {
    public static final String EXTRA_COMIC_ID = "comic_id";
    private static final int COVER_PICK = 2001;

    private ShelfDatabase db;
    private File coversDir;
    private ShelfDatabase.Comic existing; // null means "adding a new comic"
    private String pickedCoverPath;

    private ImageView coverImage;
    private EditText titleField, chapterField, descField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new ShelfDatabase(this);
        coversDir = new File(getFilesDir(), "covers");
        coversDir.mkdirs();

        long comicId = getIntent().getLongExtra(EXTRA_COMIC_ID, -1);
        existing = comicId >= 0 ? db.getById(comicId) : null;

        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(27, 28, 34));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));

        TextView back = new TextView(this);
        back.setText("← Back to shelf");
        back.setTextColor(Color.rgb(167, 168, 176));
        back.setTextSize(14);
        back.setPadding(0, 0, 0, dp(16));
        back.setOnClickListener(v -> closeActivity());
        root.addView(back);

        coverImage = new ImageView(this);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(-1, dp(260));
        coverImage.setLayoutParams(coverParams);
        coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverImage.setBackgroundColor(Color.rgb(60, 61, 70));
        Bitmap existingCover = existing != null ? decodeCover(existing.coverPath) : null;
        if (existingCover != null) coverImage.setImageBitmap(existingCover);
        if (existing != null) coverImage.setTransitionName("cover_" + existing.id);
        root.addView(coverImage);

        Button chooseCover = new Button(this);
        chooseCover.setText("CHANGE COVER");
        chooseCover.setTextColor(Color.WHITE);
        chooseCover.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            try { startActivityForResult(intent, COVER_PICK); }
            catch (Exception e) { Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show(); }
        });
        LinearLayout.LayoutParams ccp = new LinearLayout.LayoutParams(-1, -2);
        ccp.topMargin = dp(10);
        ccp.bottomMargin = dp(20);
        root.addView(chooseCover, ccp);

        titleField = field("Title", existing != null ? existing.title : "");
        root.addView(titleField, matchWrap());

        chapterField = field("Last chapter read", existing != null ? existing.lastChapter : "");
        LinearLayout.LayoutParams chP = matchWrap();
        chP.topMargin = dp(12);
        root.addView(chapterField, chP);

        descField = field("Description (optional)", existing != null ? existing.description : "");
        descField.setMinLines(3);
        LinearLayout.LayoutParams dP = matchWrap();
        dP.topMargin = dp(12);
        root.addView(descField, dP);

        Button save = new Button(this);
        save.setText(existing == null ? "ADD TO SHELF" : "SAVE CHANGES");
        save.setTextColor(Color.WHITE);
        save.setBackgroundColor(Color.rgb(193, 68, 59));
        save.setOnClickListener(v -> onSave());
        LinearLayout.LayoutParams saveP = matchWrap();
        saveP.topMargin = dp(24);
        root.addView(save, saveP);

        if (existing != null) {
            TextView delete = new TextView(this);
            delete.setText("Delete comic");
            delete.setTextColor(Color.rgb(167, 168, 176));
            delete.setTextSize(13);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, dp(18), 0, 0);
            delete.setOnClickListener(v -> confirmDelete());
            root.addView(delete, matchWrap());
        }

        scroll.addView(root);
        setContentView(scroll);
    }

    private void onSave() {
        String title = titleField.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Title can't be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        String desc = descField.getText().toString().trim();
        String chapter = chapterField.getText().toString().trim();

        if (existing == null) {
            db.insert(title, desc, chapter, pickedCoverPath);
        } else {
            db.update(existing.id, title, desc, chapter, pickedCoverPath);
        }
        closeActivity();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete comic")
                .setMessage("Remove \"" + existing.title + "\" from your shelf?")
                .setPositiveButton("Delete", (d, w) -> {
                    db.delete(existing.id);
                    closeActivity();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == COVER_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handlePickedCover(data.getData());
        }
    }

    private void handlePickedCover(Uri picked) {
        try {
            File dest = new File(coversDir, "cover_" + System.currentTimeMillis() + ".jpg");
            try (InputStream in = getContentResolver().openInputStream(picked);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) throw new IOException("Couldn't open picked image");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            pickedCoverPath = dest.getAbsolutePath();
            coverImage.setImageBitmap(decodeCover(pickedCoverPath));
        } catch (IOException e) {
            Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onBackPressed() {
        closeActivity();
    }

    /** Reverses the shared-element zoom when editing; slides back down when adding new. */
    private void closeActivity() {
        if (existing != null) {
            finishAfterTransition();
        } else {
            finish();
            overridePendingTransition(0, R.anim.slide_down_out);
        }
    }

    private Bitmap decodeCover(String path) {
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists()) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;
        return BitmapFactory.decodeFile(path, opts);
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(130, 131, 140));
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        e.setBackgroundColor(Color.rgb(37, 38, 46));
        return e;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
