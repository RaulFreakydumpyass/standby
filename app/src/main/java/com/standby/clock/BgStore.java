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
            return b;
        } catch (Throwable t) {
            return null;
        }
    }
}
