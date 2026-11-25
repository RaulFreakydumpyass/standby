package com.standby.clock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private static final int REQ_FONT = 1, REQ_IMG = 2;
    private static final int BG = 0xFF0E0E12, CARD = 0xFF1A1A20, TXT = 0xFFFFFFFF, SUB = 0xFF9A9AA6, ACC = 0xFFFF9500;

    private Prefs p;
    private ScrollView scroll;
    private LinearLayout content;
    private final ExecutorService thumbs = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = new Prefs(this);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(48));
        scroll.addView(content);
        setContentView(scroll);
        build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thumbs.shutdownNow();
    }

    // ------------------------------------------------------------------ UI helpers

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams mw() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1f); }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void header(String s) {
        TextView t = text(s.toUpperCase(Locale.ROOT), 13, ACC, true);
        t.setPadding(dp(8), dp(20), 0, dp(8));
        content.addView(t, mw());
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        l.setBackground(g);
        content.addView(l, mw());
        return l;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(10), dp(16), dp(10));
        r.setMinimumHeight(dp(54));
        parent.addView(r, mw());
        return r;
    }

    private LinearLayout labels(String title, String sub) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(text(title, 16, TXT, false));
        if (sub != null) l.addView(text(sub, 12, SUB, false));
        return l;
    }

    private void note(LinearLayout card, String s) {
        TextView t = text(s, 12, SUB, false);
        t.setPadding(dp(16), dp(4), dp(16), dp(12));
        card.addView(t, mw());
    }

    private Button button(LinearLayout card, String label, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(label);
        bt.setAllCaps(false);
        bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = mw();
        lp.setMargins(dp(12), dp(6), dp(12), dp(6));
        card.addView(bt, lp);
        return bt;
    }

    private void sw(LinearLayout card, String title, String sub, final String key) {
        LinearLayout r = row(card);
        r.addView(labels(title, sub), weight());
        Switch s = new Switch(this);
        s.setChecked(p.b(key));
        s.setOnCheckedChangeListener((btn, v) -> p.put(key, v));
        r.addView(s);
    }

    private void choice(LinearLayout card, final String title, final String[] items, final String key) {
        int cur = Math.max(0, Math.min(items.length - 1, p.i(key)));
        LinearLayout r = row(card);
        r.addView(labels(title, null), weight());
        r.addView(text(items[cur], 14, ACC, false));
        r.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(items, Math.max(0, Math.min(items.length - 1, p.i(key))), (d, which) -> {
                    p.put(key, which);
                    d.dismiss();
                    build();
                }).show());
    }

    private void slider(LinearLayout card, String title, final String key, final int min, final int max, final String unit) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout top = new LinearLayout(this);
        top.addView(text(title, 16, TXT, false), weight());
        final TextView val = text(p.i(key) + unit, 14, ACC, false);
        top.addView(val);
        col.addView(top, mw());
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(min, Math.min(max, p.i(key))) - min);
        sb.getProgressDrawable().setColorFilter(ACC, PorterDuff.Mode.SRC_IN);
        sb.getThumb().setColorFilter(ACC, PorterDuff.Mode.SRC_IN);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean fromUser) {
                p.put(key, prog + min);
                val.setText((prog + min) + unit);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(sb, mw());
        card.addView(col, mw());
    }

    private void swatches(LinearLayout card, String title, final String key) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(10));
        col.addView(text(title, 16, TXT, false));
        int per = 6;
        for (int rowIdx = 0; rowIdx < Prefs.PALETTE.length / per; rowIdx++) {
            LinearLayout line = new LinearLayout(this);
            for (int k = 0; k < per; k++) {
                final int idx = rowIdx * per + k;
                View v = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(Prefs.PALETTE[idx]);
                boolean sel = p.i(key) == idx;
                g.setStroke(dp(sel ? 3 : 1), sel ? 0xFF0A84FF : 0x33FFFFFF);
                v.setBackground(g);
                v.setOnClickListener(x -> { p.put(key, idx); build(); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
                lp.setMargins(0, dp(8), dp(12), 0);
                line.addView(v, lp);
            }
            col.addView(line);
        }
        card.addView(col, mw());
    }

    // ------------------------------------------------------------------ screen

    private void build() {
        final int sy = scroll.getScrollY();
        content.removeAllViews();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("StandBy settings", 26, TXT, true), weight());
        Button done = new Button(this);
        done.setText("Done");
        done.setOnClickListener(v -> finish());
        top.addView(done);
        content.addView(top, mw());

        // ---- clock
        header("Clock");
        LinearLayout c = card();
        choice(c, "Clock style", Prefs.STYLE_NAMES, "style");
        sw(c, "24-hour time", null, "h24");
        sw(c, "Show seconds", null, "seconds");
        sw(c, "Blinking colon", "Digital clock only", "blink");
        sw(c, "Bold text", null, "bold");
        sw(c, "Glow", "Soft glow around the clock", "glow");
        slider(c, "Clock size", "size", 40, 100, "%");
        choice(c, "Clock position", new String[]{"Top", "Center", "Bottom"}, "pos");
        swatches(c, "Clock color", "color");
        swatches(c, "Accent color (colon, seconds, second hand)", "accent");

        // ---- date / text / status
        header("Date, text & status");
        c = card();
        sw(c, "Show date", null, "date");
        String[] samples = new String[StandbyView.DATE_PATTERNS.length];
        for (int i = 0; i < samples.length; i++)
            samples[i] = new SimpleDateFormat(StandbyView.DATE_PATTERNS[i], Locale.getDefault()).format(new Date());
        choice(c, "Date format", samples, "dateFmt");
        LinearLayout tr = row(c);
        tr.addView(labels("Custom text", "Shown under the date"), weight());
        EditText et = new EditText(this);
        et.setText(p.s("custom"));
        et.setHint("e.g. Good night");
        et.setSingleLine(true);
        et.setTextColor(TXT);
        et.setHintTextColor(SUB);
        et.setMinWidth(dp(200));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c2) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c2) { }
            @Override public void afterTextChanged(Editable e) { p.put("custom", e.toString()); }
        });
        tr.addView(et);
        sw(c, "Battery level", null, "battery");
        sw(c, "Next alarm", "Shows the next alarm from the clock app", "alarm");

        // ---- fonts
        header("Fonts");
        c = card();
        String curFont = p.s("font");
        for (int i = 0; i < FontStore.SYS.length; i++) fontRow(c, "sys:" + FontStore.SYS[i], null, curFont);
        for (File f : FontStore.imported(this)) fontRow(c, "file:" + f.getName(), f.getName(), curFont);
        button(c, "Import fonts from USB / storage\u2026", v -> pick(REQ_FONT, "*/*"));
        note(c, "Pick .ttf / .otf files (you can select several). They are copied into the app, so you can unplug the USB drive afterwards. In the picker: menu \u22EE \u2192 Show internal storage, or choose the USB drive from the side bar.");

        // ---- background
        header("Background pictures");
        c = card();
        sw(c, "Show background picture", null, "bgOn");
        List<File> imgs = BgStore.list(this);
        String curBg = p.s("bgCurrent");
        for (int i = 0; i < imgs.size(); i++) picRow(c, imgs.get(i), curBg.equals(imgs.get(i).getName()) || (curBg.isEmpty() && i == 0));
        button(c, "Add pictures from USB / storage\u2026", v -> pick(REQ_IMG, "image/*"));
        note(c, "Pictures are copied at original quality (no compression, no resizing) and shown at your screen's full native resolution.");
        choice(c, "Slideshow", new String[]{"Off (use selected picture)", "Every 10 seconds", "Every 30 seconds",
                "Every minute", "Every 5 minutes", "Every 15 minutes"}, "slide");
        sw(c, "Fit whole picture", "Off = fill the screen (crops edges)", "fit");
        slider(c, "Blur background", "blur", 0, 100, "%");
        slider(c, "Darken background", "dim", 0, 90, "%");

        // ---- screen
        header("Screen & night");
        c = card();
        sw(c, "Keep screen on", null, "keepOn");
        sw(c, "Show over lock screen", null, "lockscreen");
        sw(c, "Use system brightness", "Off = your own level", "brightAuto");
        slider(c, "Brightness", "bright", 2, 100, "%");
        choice(c, "Orientation", new String[]{"Auto (portrait + landscape)", "Lock portrait", "Lock landscape"}, "orient");
        choice(c, "Night mode (red clock, minimum brightness)", new String[]{"Off", "On a schedule", "Always on"}, "nightMode");
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = (i < 10 ? "0" : "") + i + ":00";
        choice(c, "Night starts", hours, "nightStart");
        choice(c, "Night ends", hours, "nightEnd");
        sw(c, "Burn-in protection", "Drifts the clock a few pixels every minute", "shift");
        sw(c, "Hourly chime", null, "chime");

        header("Gestures");
        c = card();
        note(c, "Tap: show the settings button\nSwipe up / down: brightness\nSwipe left / right: change clock style");

        c = card();
        button(c, "Reset all settings", v -> new AlertDialog.Builder(this)
                .setMessage("Reset every setting to default? Your fonts and pictures are kept.")
                .setPositiveButton("Reset", (d, w) -> { p.reset(); build(); })
                .setNegativeButton("Cancel", null).show());

        scroll.post(() -> scroll.scrollTo(0, sy));
    }

    private void fontRow(LinearLayout card, final String id, final String fileName, String current) {
        LinearLayout r = row(card);
        TextView t = text(FontStore.label(id) + "   12:34  Aa", 20, TXT, false);
        t.setTypeface(FontStore.load(this, id, false));
        r.addView(t, weight());
        if (fileName != null) {
            TextView del = text("Delete", 13, ACC, true);
            del.setPadding(dp(12), dp(8), dp(12), dp(8));
            del.setOnClickListener(v -> {
                FontStore.delete(this, fileName);
                if (id.equals(p.s("font"))) p.put("font", FontStore.DEFAULT);
                build();
            });
            r.addView(del);
        }
        if (id.equals(current)) r.addView(text("\u2713", 20, ACC, true));
        r.setOnClickListener(v -> { p.put("font", id); build(); });
    }

    private void picRow(LinearLayout card, final File f, boolean selected) {
        LinearLayout r = row(card);
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0xFF000000);
        r.addView(iv, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout l = labels(f.getName().replaceFirst("^[0-9a-z]+_", ""), BgStore.dims(f) + (selected ? "  \u2022  current" : ""));
        l.setPadding(dp(12), 0, dp(8), 0);
        r.addView(l, weight());
        TextView del = text("Delete", 13, ACC, true);
        del.setPadding(dp(12), dp(8), dp(12), dp(8));
        del.setOnClickListener(v -> {
            BgStore.delete(f);
            if (f.getName().equals(p.s("bgCurrent"))) p.put("bgCurrent", "");
            build();
        });
        r.addView(del);
        r.setOnClickListener(v -> { p.put("bgCurrent", f.getName()); build(); });
        final int size = dp(72);
        try {
            thumbs.execute(() -> {
                final Bitmap bm = BgStore.decode(f, size, size, false);
                runOnUiThread(() -> { if (bm != null) iv.setImageBitmap(bm); });
            });
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ importing from USB / storage

    private void pick(int req, String mime) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, req);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No file picker found on this device", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(final int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        final List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        Toast.makeText(this, "Copying\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int ok = 0, bad = 0;
            String lastId = null;
            for (Uri u : uris) {
                try {
                    if (req == REQ_FONT) lastId = FontStore.importFont(this, u);
                    else BgStore.importImage(this, u);
                    ok++;
                } catch (Exception e) {
                    bad++;
                }
            }
            final int fOk = ok, fBad = bad;
            final String fontId = lastId;
            runOnUiThread(() -> {
                if (fontId != null) p.put("font", fontId);
                String what = req == REQ_FONT ? "font" : "picture";
                String msg = fOk + " " + what + (fOk == 1 ? "" : "s") + " saved" + (fBad > 0 ? ", " + fBad + " failed" : "");
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                build();
            });
        }).start();
    }
}
