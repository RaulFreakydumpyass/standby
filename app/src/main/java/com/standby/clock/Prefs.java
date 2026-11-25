package com.standby.clock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/** All settings live here, with their defaults in one place. */
public class Prefs {
    public static final String[] STYLE_NAMES = {"Digital", "Analog", "Digital + Analog"};
    public static final int[] PALETTE = {
            0xFFFFFFFF, 0xFFFF9500, 0xFFFF3B30, 0xFFFFCC00, 0xFF34C759, 0xFF30D5C8,
            0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF2D92, 0xFFAEAEB2, 0xFFFFD6A5};

    private static final Map<String, Object> DEF = new HashMap<>();
    static {
        // clock
        DEF.put("style", 0);          // 0 digital, 1 analog, 2 both
        DEF.put("h24", true);
        DEF.put("seconds", false);
        DEF.put("blink", true);
        DEF.put("bold", false);
        DEF.put("glow", false);
        DEF.put("size", 100);
        DEF.put("pos", 1);            // 0 top, 1 center, 2 bottom
        DEF.put("color", 0);
        DEF.put("accent", 1);
        DEF.put("font", FontStore.DEFAULT);
        // date / text / status
        DEF.put("date", true);
        DEF.put("dateFmt", 0);
        DEF.put("custom", "");
        DEF.put("battery", true);
        DEF.put("alarm", true);
        // background
        DEF.put("bgOn", true);
        DEF.put("bgCurrent", "");
        DEF.put("slide", 2);          // index into StandbyView.SLIDE_SECS
        DEF.put("fit", false);
        DEF.put("dim", 30);
        DEF.put("blur", 0);           // 0..100 background blur
        // screen
        DEF.put("keepOn", true);
        DEF.put("lockscreen", false);
        DEF.put("brightAuto", true);
        DEF.put("bright", 60);
        DEF.put("orient", 0);         // 0 auto, 1 portrait, 2 landscape
        DEF.put("nightMode", 1);      // 0 off, 1 schedule, 2 always
        DEF.put("nightStart", 23);
        DEF.put("nightEnd", 6);
        DEF.put("shift", true);
        DEF.put("chime", false);
    }

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("standby", Context.MODE_PRIVATE);
    }

    public int i(String k) { return sp.getInt(k, (Integer) DEF.get(k)); }
    public boolean b(String k) { return sp.getBoolean(k, (Boolean) DEF.get(k)); }
    public String s(String k) { return sp.getString(k, (String) DEF.get(k)); }

    public void put(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    public void put(String k, String v) { sp.edit().putString(k, v).apply(); }

    public void reset() { sp.edit().clear().apply(); }
}
