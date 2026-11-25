package com.standby.clock;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Built-in system fonts + fonts imported by the user (copied into app storage). */
public final class FontStore {
    private FontStore() {}

    public static final String DEFAULT = "sys:sans-serif-medium";
    public static final String[] SYS = {"sans-serif", "sans-serif-light", "sans-serif-thin",
            "sans-serif-condensed", "sans-serif-medium", "sans-serif-black", "serif",
            "monospace", "casual", "cursive"};
    public static final String[] SYS_LABELS = {"Sans", "Sans Light", "Sans Thin",
            "Sans Condensed", "Sans Medium", "Sans Black", "Serif", "Monospace", "Casual", "Cursive"};

    private static final Map<String, Typeface> CACHE = new HashMap<>();

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "fonts");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static boolean isFontName(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".ttf") || l.endsWith(".otf") || l.endsWith(".ttc");
    }

    public static List<File> imported(Context c) {
        List<File> out = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) if (isFontName(f.getName())) out.add(f);
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    /** Copies a font file from any Uri (USB, SD, downloads...) into app storage. Returns the font id. */
    public static String importFont(Context c, Uri uri) throws IOException {
        String name = Io.displayName(c, uri);
        if (name == null) name = "font_" + System.currentTimeMillis() + ".ttf";
        name = Io.safe(name);
        if (!isFontName(name)) throw new IOException("Not a .ttf/.otf font");
        File out = new File(dir(c), name);
        Io.copy(c, uri, out);
        try {
            Typeface t = Typeface.createFromFile(out);
            if (t == null) throw new IOException("bad font");
        } catch (Exception e) {
            out.delete();
            throw new IOException("Invalid font file");
        }
        synchronized (CACHE) { CACHE.clear(); }
        return "file:" + name;
    }

    public static void delete(Context c, String fileName) {
        new File(dir(c), fileName).delete();
        synchronized (CACHE) { CACHE.clear(); }
    }

    public static Typeface load(Context c, String id, boolean bold) {
        String key = id + (bold ? "|b" : "");
        synchronized (CACHE) {
            Typeface hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        Typeface t;
        try {
            if (id.startsWith("file:")) {
                File f = new File(dir(c), id.substring(5));
                Typeface base = f.exists() ? Typeface.createFromFile(f) : Typeface.DEFAULT;
                t = bold ? Typeface.create(base, Typeface.BOLD) : base;
            } else {
                String fam = id.startsWith("sys:") ? id.substring(4) : "sans-serif";
                t = Typeface.create(fam, bold ? Typeface.BOLD : Typeface.NORMAL);
            }
        } catch (Exception e) {
            t = Typeface.DEFAULT;
        }
        synchronized (CACHE) { CACHE.put(key, t); }
        return t;
    }

    public static String label(String id) {
        if (id.startsWith("sys:")) {
            String fam = id.substring(4);
            for (int i = 0; i < SYS.length; i++) if (SYS[i].equals(fam)) return SYS_LABELS[i];
            return fam;
        }
        String n = id.startsWith("file:") ? id.substring(5) : id;
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
