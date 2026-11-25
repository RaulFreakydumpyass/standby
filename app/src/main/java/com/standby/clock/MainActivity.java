package com.standby.clock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

public class MainActivity extends Activity implements StandbyView.Host {

    private StandbyView view;
    private ImageButton gear;
    private Prefs prefs;
    private GestureDetector detector;
    private ToneGenerator tone;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean night;
    private boolean scrolling, verticalScroll;
    private float brightPct = 60f;

    private final Runnable hideGear = new Runnable() {
        @Override public void run() {
            gear.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> gear.setVisibility(View.INVISIBLE)).start();
        }
    };

    private final BroadcastReceiver battery = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { handleBattery(i); }
    };

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        view = new StandbyView(this);
        view.host = this;
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        gear = new ImageButton(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(0xCCFFFFFF);
        gear.setBackgroundColor(0x66000000);
        gear.setPadding(dp(14), dp(14), dp(14), dp(14));
        gear.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        gear.setVisibility(View.INVISIBLE);
        gear.setAlpha(0f);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, dp(16), dp(16));
        root.addView(gear, lp);

        setContentView(root);

        detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { scrolling = false; verticalScroll = false; return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { showGear(); return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (!scrolling) { scrolling = true; verticalScroll = Math.abs(dy) > Math.abs(dx); }
                if (verticalScroll) adjustBrightness(dy / Math.max(1, view.getHeight()) * 120f);
                return true;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > 900f) {
                    cycleStyle(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        view.setOnTouchListener((v, e) -> detector.onTouchEvent(e));
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyWindowSettings();
        view.start();
        night = view.isNight();
        applyBrightness();
        handleBattery(registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        hideBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.stop();
        try { unregisterReceiver(battery); } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tone != null) { tone.release(); tone = null; }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // ---- settings that belong to the window

    private void applyWindowSettings() {
        Window w = getWindow();
        if (prefs.b("keepOn")) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        boolean lock = prefs.b("lockscreen");
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(lock);
        } else if (lock) {
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        int o = prefs.i("orient");
        setRequestedOrientation(o == 1 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : o == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        brightPct = prefs.i("bright");
    }

    private void applyBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (night) lp.screenBrightness = 0.04f;
        else if (prefs.b("brightAuto")) lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        else lp.screenBrightness = Math.max(0.02f, brightPct / 100f);
        getWindow().setAttributes(lp);
    }

    private void adjustBrightness(float delta) {
        if (prefs.b("brightAuto")) {
            try {
                brightPct = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f;
            } catch (Exception e) {
                brightPct = 60f;
            }
            prefs.put("brightAuto", false);
        }
        brightPct = Math.max(2f, Math.min(100f, brightPct + delta));
        prefs.put("bright", Math.round(brightPct));
        applyBrightness();
        view.flash("Brightness " + Math.round(brightPct) + "%");
    }

    private void cycleStyle(int dir) {
        int s = (prefs.i("style") + dir + 3) % 3;
        prefs.put("style", s);
        view.applySettings();
        view.flash(Prefs.STYLE_NAMES[s]);
    }

    private void showGear() {
        gear.setVisibility(View.VISIBLE);
        gear.animate().alpha(1f).setDuration(200).start();
        handler.removeCallbacks(hideGear);
        handler.postDelayed(hideGear, 4000);
    }

    private void handleBattery(Intent i) {
        if (i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        boolean plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        if (level >= 0 && scale > 0) view.setBattery(level * 100 / scale, plugged);
    }

    // ---- StandbyView.Host

    @Override
    public void onChime() {
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 350);
        } catch (Exception ignored) { }
    }

    @Override
    public void onNightChanged(boolean n) {
        night = n;
        applyBrightness();
    }
}
