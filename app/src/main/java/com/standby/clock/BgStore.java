package com.standby.clock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Background pictures. The ORIGINAL file is copied byte-for-byte (no recompression, no resizing),
 * and decoded at (at least) the screen's native resolution in 32-bit colour, so nothing is blurry.
 */
public final class BgStore {
    private BgStore() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "backgrounds");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static List<File> list(Context c) {
        List<File> out = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) if (!f.getName().endsWith(".part")) out.add(f);
        Collections.sort(out, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    public static void importImage(Context c, Uri uri) throws IOException {
        String name = Io.displayName(c, uri);
        if (name == null) name = "image.jpg";
        File out = new File(dir(c), Long.toString(System.currentTimeMillis(), 36) + "_" + Io.safe(name));
        Io.copy(c, uri, out);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(out.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            out.delete();
            throw new IOException("Not an image");
        }
    }

    public static void delete(File f) { f.delete(); }

    public static String dims(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        return o.outWidth + " x " + o.outHeight + " px";
    }

    private static int orientation(File f) {
        try {
            return new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Decodes the picture so that, once scaled to the target (rw x rh), it is never up-scaled from a
     * smaller bitmap: we only sub-sample while the decoded size is still >= what the screen needs.
     */
    public static Bitmap decode(File f, int rw, int rh, boolean fit) {
        return decode(f, rw, rh, fit, 0);
    }

    public static Bitmap decode(File f, int rw, int rh, boolean fit, int blurPct) {
        try {
            int orient = orientation(f);
            boolean swap = orient == 5 || orient == 6 || orient == 7 || orient == 8;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int ow = swap ? o.outHeight : o.outWidth;
            int oh = swap ? o.outWidth : o.outHeight;
            if (ow <= 0 || oh <= 0 || rw <= 0 || rh <= 0) return null;

            float need = fit ? Math.min((float) rw / ow, (float) rh / oh)
                             : Math.max((float) rw / ow, (float) rh / oh);
            int sample = 1;
            while (need <= 1f / (sample * 2)) sample *= 2;
            while (ow / sample > 4096 || oh / sample > 4096) sample *= 2;

            Bitmap b = null;
            for (int attempt = 0; attempt < 3 && b == null; attempt++) {
                try {
                    BitmapFactory.Options d = new BitmapFactory.Options();
                    d.inSampleSize = sample;
                    d.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    b = BitmapFactory.decodeFile(f.getPath(), d);
                } catch (OutOfMemoryError oom) {
                    sample *= 2;
                }
            }
            if (b == null) return null;
            if (orient > 1) {
                Matrix m = new Matrix();
                switch (orient) {
                    case 2: m.postScale(-1, 1); break;
                    case 3: m.postRotate(180); break;
                    case 4: m.postScale(1, -1); break;
                    case 5: m.postRotate(90); m.postScale(-1, 1); break;
                    case 6: m.postRotate(90); break;
                    case 7: m.postRotate(-90); m.postScale(-1, 1); break;
                    case 8: m.postRotate(-90); break;
                    default: break;
                }
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b = r;
            }
            return blurPct > 0 ? blur(b, blurPct) : b;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Cheap, smooth blur for slow tablets: shrink the picture, box-blur the small copy twice, and let the
     * GPU scale it back up with filtering (a blurred picture needs no extra pixels). pct = 1..100.
     */
    static Bitmap blur(Bitmap src, int pct) {
        float f = 1f / (1f + pct / 8f);
        int w = Math.max(8, Math.round(src.getWidth() * f));
        int h = Math.max(8, Math.round(src.getHeight() * f));
        Bitmap sm = Bitmap.createScaledBitmap(src, w, h, true);
        int r = Math.max(1, Math.round(pct / 100f * 6f));
        int[] px = new int[w * h];
        sm.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < 2; i++) {
            boxPass(px, w, h, r, true);
            boxPass(px, w, h, r, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static int cl(int v, int len) { return v < 0 ? 0 : (v >= len ? len - 1 : v); }

    private static void boxPass(int[] px, int w, int h, int r, boolean horiz) {
        int len = horiz ? w : h, lines = horiz ? h : w, n = 2 * r + 1;
        int[] line = new int[len], outl = new int[len];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) line[i] = horiz ? px[l * w + i] : px[i * w + l];
            int sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int c = line[cl(k, len)];
                sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                outl[i] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                int add = line[cl(i + r + 1, len)], rem = line[cl(i - r, len)];
                sr += ((add >> 16) & 0xFF) - ((rem >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((rem >> 8) & 0xFF);
                sb += (add & 0xFF) - (rem & 0xFF);
            }
            for (int i = 0; i < len; i++) { if (horiz) px[l * w + i] = outl[i]; else px[i * w + l] = outl[i]; }
        }
    }
}
