package com.standby.clock;

import android.animation.ValueAnimator;
import android.app.AlarmManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Draws the whole StandBy screen: background, clock (vertical in portrait, horizontal in landscape), date, status. */
public class StandbyView extends View {

    public interface Host {
        void onChime();
        void onNightChanged(boolean night);
    }

    public static final String[] DATE_PATTERNS = {"EEEE, d MMMM", "EEE d MMM", "dd.MM.yyyy", "yyyy-MM-dd", "MMMM d, yyyy"};
    public static final int[] SLIDE_SECS = {0, 10, 30, 60, 300, 900};

    public Host host;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix bgMatrix = new Matrix();
    private final Rect tb = new Rect();

    // settings snapshot
    private int blurPct;
    private int style, dateFmt, colorIdx, accentIdx, sizePct, pos, dimPct, slideIdx, nightMode, nightStart, nightEnd;
    private boolean h24, showSec, blink, bold, glow, showDate, showBatt, showAlarm, bgOn, fit, shift, chime;
    private String customText = "";
    private Typeface typeface = Typeface.DEFAULT;
    private SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERNS[0], Locale.getDefault());
    private int main = Color.WHITE, accent = Color.WHITE;
    private boolean night;

    // background
    private List<File> files = new ArrayList<>();
    private int bgIndex;
    private Bitmap bgCur, bgNext;
    private float fade = 1f;
    private ValueAnimator fadeAnim;
    private int bgGen;
    private String loadedKey = "";
    private long lastSwitch;

    // live state
    private int battPct = -1;
    private boolean charging;
    private String alarmText = "";
    private String hud;
    private long hudUntil;
    private int lastMinute = -1, lastChimeHour = -1;

    public StandbyView(Context c) {
        super(c);
        prefs = new Prefs(c);
        hudPaint.setColor(Color.WHITE);
        hudPaint.setTextAlign(Paint.Align.CENTER);
        hudBg.setColor(0xB0000000);
        applySettings();
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        applySettings();
        handler.removeCallbacks(tick);
        lastMinute = -1;
        handler.post(tick);
    }

    public void stop() {
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        loader.shutdown();
        super.onDetachedFromWindow();
    }

    public boolean isNight() { return night; }

    public void setBattery(int pct, boolean isCharging) {
        battPct = pct;
        charging = isCharging;
        invalidate();
    }

    public void flash(String text) {
        hud = text;
        hudUntil = System.currentTimeMillis() + 1500;
        invalidate();
        postInvalidateDelayed(1600);
    }

    // ------------------------------------------------------------------ settings

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public void applySettings() {
        style = clamp(prefs.i("style"), 0, 2);
        h24 = prefs.b("h24");
        showSec = prefs.b("seconds");
        blink = prefs.b("blink");
        bold = prefs.b("bold");
        glow = prefs.b("glow");
        sizePct = clamp(prefs.i("size"), 40, 100);
        pos = clamp(prefs.i("pos"), 0, 2);
        colorIdx = clamp(prefs.i("color"), 0, Prefs.PALETTE.length - 1);
        accentIdx = clamp(prefs.i("accent"), 0, Prefs.PALETTE.length - 1);
        showDate = prefs.b("date");
        dateFmt = clamp(prefs.i("dateFmt"), 0, DATE_PATTERNS.length - 1);
        customText = prefs.s("custom");
        showBatt = prefs.b("battery");
        showAlarm = prefs.b("alarm");
        bgOn = prefs.b("bgOn");
        fit = prefs.b("fit");
        dimPct = clamp(prefs.i("dim"), 0, 90);
        blurPct = clamp(prefs.i("blur"), 0, 100);
        slideIdx = clamp(prefs.i("slide"), 0, SLIDE_SECS.length - 1);
        shift = prefs.b("shift");
        chime = prefs.b("chime");
        nightMode = prefs.i("nightMode");
        nightStart = prefs.i("nightStart");
        nightEnd = prefs.i("nightEnd");

        typeface = FontStore.load(getContext(), prefs.s("font"), bold);
        dateFormat = new SimpleDateFormat(DATE_PATTERNS[dateFmt], Locale.getDefault());

        files = BgStore.list(getContext());
        String cur = prefs.s("bgCurrent");
        bgIndex = 0;
        for (int i = 0; i < files.size(); i++) if (files.get(i).getName().equals(cur)) bgIndex = i;
        if (files.isEmpty()) {
            bgGen++;
            finishFade();
            bgCur = null;
            loadedKey = "";
        } else {
            reloadBackground();
        }

        Calendar now = Calendar.getInstance();
        lastChimeHour = now.get(Calendar.HOUR_OF_DAY);
        lastSwitch = System.currentTimeMillis();
        updateNight(now.get(Calendar.HOUR_OF_DAY));
        updateAlarm();
        invalidate();
    }

    private void updateNight(int hour) {
        boolean n;
        if (nightMode == 2) n = true;
        else if (nightMode == 1)
            n = nightStart <= nightEnd ? (hour >= nightStart && hour < nightEnd) : (hour >= nightStart || hour < nightEnd);
        else n = false;
        boolean changed = n != night;
        night = n;
        main = night ? 0xFFE0261C : Prefs.PALETTE[colorIdx];
        accent = night ? 0xFFE0261C : Prefs.PALETTE[accentIdx];
        if (changed && host != null) host.onNightChanged(n);
    }

    private void updateAlarm() {
        alarmText = "";
        if (!showAlarm) return;
        try {
            AlarmManager am = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo info = am.getNextAlarmClock();
            if (info != null) {
                SimpleDateFormat f = new SimpleDateFormat(h24 ? "EEE HH:mm" : "EEE h:mm a", Locale.getDefault());
                alarmText = "\u23F0 " + f.format(new Date(info.getTriggerTime()));
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ ticking

    private boolean fastTick() {
        return showSec || (blink && style != 1) || slideshowActive();
    }

    private boolean slideshowActive() {
        return bgOn && SLIDE_SECS[slideIdx] > 0 && files.size() > 1;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            Calendar c = Calendar.getInstance();
            if (c.get(Calendar.MINUTE) != lastMinute) {
                lastMinute = c.get(Calendar.MINUTE);
                onMinute(c);
            }
            if (slideshowActive() && now - lastSwitch >= SLIDE_SECS[slideIdx] * 1000L - 250) advanceBackground();
            invalidate();
            long step = fastTick() ? 1000L : 60000L;
            handler.postDelayed(this, step - (now % step) + 20);
        }
    };

    private void onMinute(Calendar c) {
        updateAlarm();
        updateNight(c.get(Calendar.HOUR_OF_DAY));
        int hr = c.get(Calendar.HOUR_OF_DAY);
        if (chime && c.get(Calendar.MINUTE) == 0 && hr != lastChimeHour) {
            lastChimeHour = hr;
            if (host != null) host.onChime();
        }
    }

    // ------------------------------------------------------------------ background loading

    private String keyFor(File f) {
        return f.getPath() + "|" + f.lastModified() + "|" + getWidth() + "x" + getHeight() + "|" + fit + "|" + blurPct;
    }

    private void reloadBackground() {
        if (!bgOn || files.isEmpty() || getWidth() == 0) return;
        File f = files.get(clamp(bgIndex, 0, files.size() - 1));
        String key = keyFor(f);
        if (key.equals(loadedKey) && bgCur != null) return;
        loadBackground(f, false, key);
    }

    private void advanceBackground() {
        lastSwitch = System.currentTimeMillis();
        if (files.size() < 2 || getWidth() == 0) return;
        bgIndex = (bgIndex + 1) % files.size();
        File f = files.get(bgIndex);
        prefs.put("bgCurrent", f.getName());
        loadBackground(f, true, keyFor(f));
    }

    private void loadBackground(final File f, final boolean animate, final String key) {
        final int gen = ++bgGen;
        final int w = getWidth(), h = getHeight();
        final boolean fitMode = fit;
        final int blurP = blurPct;
        try {
            loader.execute(() -> {
                final Bitmap bm = BgStore.decode(f, w, h, fitMode, blurP);
                post(() -> {
                    if (gen != bgGen || bm == null) return;
                    loadedKey = key;
                    showBitmap(bm, animate);
                });
            });
        } catch (Exception ignored) {
        }
    }

    private void finishFade() {
        if (fadeAnim != null) {
            fadeAnim.removeAllListeners();
            fadeAnim.removeAllUpdateListeners();
            fadeAnim.cancel();
            fadeAnim = null;
        }
        if (bgNext != null) {
            bgCur = bgNext;
            bgNext = null;
        }
        fade = 1f;
    }

    private void showBitmap(Bitmap bm, boolean animate) {
        finishFade();
        if (!animate || bgCur == null) {
            bgCur = bm;
            invalidate();
            return;
        }
        bgNext = bm;
        fade = 0f;
        fadeAnim = ValueAnimator.ofFloat(0f, 1f);
        fadeAnim.setDuration(900);
        fadeAnim.addUpdateListener(a -> {
            fade = (Float) a.getAnimatedValue();
            invalidate();
        });
        fadeAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                fadeAnim = null;
                if (bgNext != null) {
                    bgCur = bgNext;
                    bgNext = null;
                }
                fade = 1f;
                invalidate();
            }
        });
        fadeAnim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        reloadBackground();
    }

    // ------------------------------------------------------------------ drawing

    private static int withAlpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }
    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }
    private static int hour12(Calendar c) { int h = c.get(Calendar.HOUR); return h == 0 ? 12 : h; }

    private void applyGlow(Paint p, float size) {
        if (glow) p.setShadowLayer(Math.max(6f, size * 0.1f), 0, 0, withAlpha(main, 190));
        else p.clearShadowLayer();
    }

    private void drawBackground(Canvas cv, int w, int h) {
        if (bgCur == null && bgNext == null) return;
        if (bgCur != null) drawBmp(cv, bgCur, w, h, 255);
        if (bgNext != null && fade > 0f) drawBmp(cv, bgNext, w, h, (int) (fade * 255));
        if (dimPct > 0) cv.drawColor(Color.argb(dimPct * 255 / 100, 0, 0, 0));
    }

    private void drawBmp(Canvas cv, Bitmap bm, int w, int h, int alpha) {
        float bw = bm.getWidth(), bh = bm.getHeight();
        float s = fit ? Math.min(w / bw, h / bh) : Math.max(w / bw, h / bh);
        bgMatrix.reset();
        bgMatrix.postScale(s, s);
        bgMatrix.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f);
        bmpPaint.setAlpha(alpha);
        cv.drawBitmap(bm, bgMatrix, bmpPaint);
    }

    private String subLine(Calendar c) {
        if (style == 1) return "";
        StringBuilder sb = new StringBuilder();
        if (showSec) sb.append(two(c.get(Calendar.SECOND)));
        if (!h24) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        }
        return sb.toString();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        cv.drawColor(Color.BLACK);
        if (bgOn && !night) drawBackground(cv, w, h);

        Calendar now = Calendar.getInstance();
        float u = Math.min(w, h);
        float m = u * 0.05f;
        boolean portrait = h > w;

        cv.save();
        if (shift) {   // burn-in protection: tiny drift that changes every minute
            int mod = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            float amp = u * 0.015f;
            cv.translate((float) Math.sin(mod * 0.9) * amp, (float) Math.cos(mod * 1.3) * amp);
        }

        String dateStr = showDate ? dateFormat.format(now.getTime()) : null;
        String cust = customText == null ? "" : customText.trim();
        String sub = subLine(now);
        float secSize = u * 0.11f, dateSize = u * 0.06f, custSize = u * 0.05f, statusSize = u * 0.038f;
        boolean hasStatus = (showBatt && battPct >= 0) || !alarmText.isEmpty();
        if (hasStatus) drawStatus(cv, w, m, statusSize);

        float top = m + (hasStatus ? statusSize * 2.2f : 0f);
        float bottom = h - m;
        float lowerH = (sub.isEmpty() ? 0f : secSize * 1.3f)
                + (dateStr == null ? 0f : dateSize * 1.5f)
                + (cust.isEmpty() ? 0f : custSize * 1.5f);
        float availH = bottom - top - lowerH;
        float clockH = Math.max(availH * sizePct / 100f, u * 0.1f);
        float groupH = clockH + lowerH;
        float groupTop = pos == 0 ? top : pos == 2 ? bottom - groupH : top + (bottom - top - groupH) / 2f;

        RectF r = new RectF(m, groupTop, w - m, groupTop + clockH);
        if (style == 0) {
            drawDigital(cv, r, portrait, now);
        } else if (style == 1) {
            drawAnalog(cv, r, now);
        } else {
            RectF a = new RectF(), d = new RectF();
            if (portrait) {
                float split = r.top + r.height() * 0.46f;
                a.set(r.left, r.top, r.right, split);
                d.set(r.left, split, r.right, r.bottom);
            } else {
                float split = r.left + r.width() * 0.42f;
                a.set(r.left, r.top, split, r.bottom);
                d.set(split, r.top, r.right, r.bottom);
            }
            drawAnalog(cv, a, now);
            drawDigital(cv, d, portrait, now);
        }

        float cx = w / 2f;
        float y = groupTop + clockH;
        if (!sub.isEmpty()) {
            setupSmall(secSize, accent);
            cv.drawText(sub, cx, y + secSize * 1.05f, smallPaint);
            y += secSize * 1.3f;
        }
        if (dateStr != null) {
            setupSmall(dateSize, withAlpha(main, 220));
            cv.drawText(dateStr, cx, y + dateSize * 1.05f, smallPaint);
            y += dateSize * 1.5f;
        }
        if (!cust.isEmpty()) {
            setupSmall(custSize, withAlpha(main, 190));
            cv.drawText(cust, cx, y + custSize * 1.05f, smallPaint);
        }
        cv.restore();
        drawHud(cv, w, h);
    }

    private void setupSmall(float size, int color) {
        smallPaint.setTypeface(typeface);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTextSize(size);
        smallPaint.setColor(color);
        applyGlow(smallPaint, size);
    }

    private void drawStatus(Canvas cv, float w, float m, float size) {
        float cy = m + size * 0.9f;
        int col = withAlpha(main, 210);
        statusPaint.setTypeface(Typeface.DEFAULT);
        statusPaint.setTextSize(size);
        statusPaint.clearShadowLayer();
        if (!alarmText.isEmpty()) {
            statusPaint.setColor(col);
            statusPaint.setTextAlign(Paint.Align.LEFT);
            cv.drawText(alarmText, m, cy + size * 0.35f, statusPaint);
        }
        if (showBatt && battPct >= 0) {
            float bh = size * 0.95f, bw = size * 1.9f, nub = bh * 0.12f;
            float right = w - m - nub;
            RectF body = new RectF(right - bw, cy - bh / 2f, right, cy + bh / 2f);
            int bc = night ? col : (charging ? 0xFF34C759 : (battPct <= 15 ? 0xFFFF3B30 : col));
            linePaint.clearShadowLayer();
            linePaint.setColor(bc);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(Math.max(1.5f, size * 0.08f));
            cv.drawRoundRect(body, bh * 0.25f, bh * 0.25f, linePaint);
            linePaint.setStyle(Paint.Style.FILL);
            float pad = size * 0.14f;
            RectF fill = new RectF(body.left + pad, body.top + pad,
                    body.left + pad + (body.width() - 2 * pad) * battPct / 100f, body.bottom - pad);
            cv.drawRoundRect(fill, bh * 0.1f, bh * 0.1f, linePaint);
            cv.drawRect(right, cy - bh * 0.2f, right + nub, cy + bh * 0.2f, linePaint);
            statusPaint.setColor(bc);
            statusPaint.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(battPct + "%", body.left - size * 0.4f, cy + size * 0.35f, statusPaint);
        }
    }

    private void drawHud(Canvas cv, int w, int h) {
        if (hud == null || System.currentTimeMillis() > hudUntil) return;
        float s = Math.min(w, h) * 0.045f;
        hudPaint.setTextSize(s);
        hudPaint.setTypeface(Typeface.DEFAULT);
        float tw = hudPaint.measureText(hud);
        float pad = s * 0.9f, boxH = s * 2f;
        float top = h * 0.88f - boxH;
        RectF r = new RectF(w / 2f - tw / 2f - pad, top, w / 2f + tw / 2f + pad, top + boxH);
        cv.drawRoundRect(r, boxH / 2f, boxH / 2f, hudBg);
        cv.drawText(hud, w / 2f, r.centerY() + s * 0.35f, hudPaint);
    }

    // ---- digital: vertical stack in portrait, one horizontal line in landscape

    private void drawDigital(Canvas cv, RectF r, boolean portrait, Calendar c) {
        String hh = h24 ? two(c.get(Calendar.HOUR_OF_DAY)) : String.valueOf(hour12(c));
        String mm = two(c.get(Calendar.MINUTE));

        textPaint.setTypeface(typeface);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(100f);
        float dW = 0f;
        for (char ch = '0'; ch <= '9'; ch++) dW = Math.max(dW, textPaint.measureText(String.valueOf(ch)));
        textPaint.getTextBounds("8", 0, 1, tb);
        float cap = tb.height();
        float colonW = dW * 0.45f;
        float gap = cap * 0.22f;

        float scale;
        if (portrait) scale = Math.min(r.width() * 0.94f / (2f * dW), r.height() / (2f * cap + gap));
        else scale = Math.min(r.width() * 0.96f / (4f * dW + colonW), r.height() / cap);
        float sz = 100f * scale;
        dW *= scale;
        cap *= scale;
        colonW *= scale;
        gap *= scale;

        textPaint.setTextSize(sz);
        textPaint.setColor(main);
        applyGlow(textPaint, sz);

        float cx = r.centerX();
        int colonAlpha = (blink && c.get(Calendar.SECOND) % 2 == 1) ? 70 : 255;
        if (portrait) {
            float blockH = 2f * cap + gap;
            float topY = r.top + (r.height() - blockH) / 2f;
            drawCells(cv, hh, cx, topY + cap, dW, colonW, cap, colonAlpha);
            drawCells(cv, mm, cx, topY + 2f * cap + gap, dW, colonW, cap, colonAlpha);
        } else {
            float topY = r.top + (r.height() - cap) / 2f;
            drawCells(cv, hh + ":" + mm, cx, topY + cap, dW, colonW, cap, colonAlpha);
        }
    }

    /** Draws digits in fixed-width cells so the clock never jiggles when digits change. */
    private void drawCells(Canvas cv, String s, float cx, float base, float dW, float colonW, float cap, int colonAlpha) {
        float total = 0f;
        for (int i = 0; i < s.length(); i++) total += s.charAt(i) == ':' ? colonW : dW;
        float x = cx - total / 2f;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == ':') {
                float rad = cap * (bold ? 0.075f : 0.06f);
                float mx = x + colonW / 2f;
                linePaint.clearShadowLayer();
                linePaint.setStyle(Paint.Style.FILL);
                if (glow) {
                    linePaint.setColor(accent);
                    linePaint.setAlpha(colonAlpha * 60 / 255);
                    cv.drawCircle(mx, base - cap * 0.68f, rad * 2.2f, linePaint);
                    cv.drawCircle(mx, base - cap * 0.32f, rad * 2.2f, linePaint);
                }
                linePaint.setColor(accent);
                linePaint.setAlpha(colonAlpha);
                cv.drawCircle(mx, base - cap * 0.68f, rad, linePaint);
                cv.drawCircle(mx, base - cap * 0.32f, rad, linePaint);
                x += colonW;
            } else {
                cv.drawText(String.valueOf(ch), x + dW / 2f, base, textPaint);
                x += dW;
            }
        }
    }

    // ---- analog

    private void drawAnalog(Canvas cv, RectF r, Calendar c) {
        float cx = r.centerX(), cy = r.centerY();
        float R = Math.min(r.width(), r.height()) / 2f * 0.96f;

        linePaint.clearShadowLayer();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            double a = i * Math.PI / 30.0;
            float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
            float len = major ? R * 0.09f : R * 0.04f;
            linePaint.setStrokeWidth(major ? R * 0.022f : R * 0.01f);
            linePaint.setColor(major ? main : withAlpha(main, 120));
            cv.drawLine(cx + sx * (R - len), cy + sy * (R - len), cx + sx * R, cy + sy * R, linePaint);
        }

        textPaint.setTypeface(typeface);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float ts = R * 0.2f;
        textPaint.setTextSize(ts);
        textPaint.setColor(main);
        applyGlow(textPaint, ts);
        textPaint.getTextBounds("8", 0, 1, tb);
        float cap = tb.height();
        for (int i = 1; i <= 12; i++) {
            double a = i * Math.PI / 6.0;
            float nx = cx + (float) Math.sin(a) * R * 0.74f;
            float ny = cy - (float) Math.cos(a) * R * 0.74f;
            cv.drawText(String.valueOf(i), nx, ny + cap / 2f, textPaint);
        }
        textPaint.clearShadowLayer();

        float sec = c.get(Calendar.SECOND);
        float min = c.get(Calendar.MINUTE) + sec / 60f;
        float hr = (c.get(Calendar.HOUR) % 12) + min / 60f;
        hand(cv, cx, cy, hr * 30f, R * 0.44f, R * 0.06f, main);
        hand(cv, cx, cy, min * 6f, R * 0.62f, R * 0.04f, main);
        if (showSec) hand(cv, cx, cy, sec * 6f, R * 0.84f, R * 0.014f, accent);
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setColor(accent);
        cv.drawCircle(cx, cy, R * 0.04f, linePaint);
    }

    private void hand(Canvas cv, float cx, float cy, float deg, float len, float width, int color) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(width);
        linePaint.setColor(color);
        cv.drawLine(cx - sx * len * 0.12f, cy - sy * len * 0.12f, cx + sx * len, cy + sy * len, linePaint);
    }
}
