package com.shelf.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ComicAdapter extends BaseAdapter {
    public interface OnComicClick {
        void onClick(ShelfDatabase.Comic comic, ImageView coverView);
    }

    private final Activity activity;
    private final OnComicClick listener;
    private List<ShelfDatabase.Comic> items = new ArrayList<>();

    public ComicAdapter(Activity activity, OnComicClick listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void setItems(List<ShelfDatabase.Comic> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return items.get(position).id; }

    @Override
    public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
        ShelfDatabase.Comic comic = items.get(position);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(6);
        card.setPadding(pad, pad, pad, pad);
        card.setClickable(true);
        card.setElevation(dp(1));

        TypedValue tv = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        if (tv.resourceId != 0) card.setForeground(activity.getDrawable(tv.resourceId));

        ImageView cover = new ImageView(activity);
        LinearLayout.LayoutParams coverParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        cover.setLayoutParams(coverParams);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setClipToOutline(true);
        cover.setTransitionName("cover_" + comic.id);

        GradientDrawable coverBg = new GradientDrawable();
        coverBg.setCornerRadius(dp(6));
        coverBg.setColor(Color.rgb(234, 228, 212));
        cover.setBackground(coverBg);

        Bitmap bmp = decodeCover(comic.coverPath);
        if (bmp != null) cover.setImageBitmap(bmp);
        card.addView(cover);

        TextView title = new TextView(activity);
        title.setText(comic.title);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.SERIF);
        title.setTextSize(14);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        card.addView(title, titleParams);

        String chapterLine = (comic.lastChapter == null || comic.lastChapter.isEmpty())
                ? "No progress logged" : comic.lastChapter;
        TextView chapter = new TextView(activity);
        chapter.setText(chapterLine);
        chapter.setTextColor(Color.rgb(193, 68, 59));
        chapter.setTextSize(12);
        LinearLayout.LayoutParams chapterParams = new LinearLayout.LayoutParams(-1, -2);
        chapterParams.topMargin = dp(4);
        card.addView(chapter, chapterParams);

        card.setOnClickListener(v -> listener.onClick(comic, cover));

        return card;
    }

    private Bitmap decodeCover(String path) {
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists()) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;
        return BitmapFactory.decodeFile(path, opts);
    }

    private int dp(int v) {
        return (int) (v * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
