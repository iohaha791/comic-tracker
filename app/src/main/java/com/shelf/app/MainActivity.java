package com.shelf.app;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private ShelfDatabase db;
    private ComicAdapter adapter;
    private List<ShelfDatabase.Comic> allComics = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new ShelfDatabase(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(); // picks up anything added/edited/deleted in DetailActivity
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(27, 28, 34));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(28), dp(20), dp(12));

        TextView title = new TextView(this);
        title.setText("Shelf");
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.SERIF);
        title.setTextSize(30);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your comics, where you left off.");
        subtitle.setTextColor(Color.rgb(167, 168, 176));
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(2);
        header.addView(subtitle, subParams);

        root.addView(header);

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setPadding(dp(20), 0, dp(20), dp(14));

        EditText search = new EditText(this);
        search.setHint("Search title or description…");
        search.setHintTextColor(Color.rgb(130, 131, 140));
        search.setTextColor(Color.WHITE);
        search.setBackgroundColor(Color.rgb(37, 38, 46));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setSingleLine(true);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, -2, 1f);
        controlsRow.addView(search, searchParams);

        Button addButton = new Button(this);
        addButton.setText("+ ADD");
        addButton.setTextColor(Color.WHITE);
        addButton.setBackgroundColor(Color.rgb(193, 68, 59));
        addButton.setOnClickListener(v -> openAdd());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-2, -1);
        addParams.leftMargin = dp(10);
        controlsRow.addView(addButton, addParams);

        root.addView(controlsRow);

        GridView gridView = new GridView(this);
        gridView.setNumColumns(3);
        gridView.setHorizontalSpacing(dp(4));
        gridView.setVerticalSpacing(dp(4));
        gridView.setPadding(dp(16), 0, dp(16), dp(24));
        gridView.setClipToPadding(false);

        adapter = new ComicAdapter(this, this::openEdit);
        gridView.setAdapter(adapter);

        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(gridView, gridParams);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        setContentView(root);
        reload();
    }

    private void reload() {
        allComics = db.getAll();
        adapter.setItems(allComics);
    }

    private void applyFilter(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setItems(allComics);
            return;
        }
        String q = query.toLowerCase(Locale.US);
        List<ShelfDatabase.Comic> filtered = new ArrayList<>();
        for (ShelfDatabase.Comic c : allComics) {
            boolean titleMatch = c.title != null && c.title.toLowerCase(Locale.US).contains(q);
            boolean descMatch = c.description != null && c.description.toLowerCase(Locale.US).contains(q);
            if (titleMatch || descMatch) filtered.add(c);
        }
        adapter.setItems(filtered);
    }

    private void openAdd() {
        Intent intent = new Intent(this, DetailActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_up_in, 0);
    }

    private void openEdit(ShelfDatabase.Comic comic, ImageView coverView) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_COMIC_ID, comic.id);
        ActivityOptions options =
                ActivityOptions.makeSceneTransitionAnimation(this, coverView, "cover_" + comic.id);
        startActivity(intent, options.toBundle());
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
