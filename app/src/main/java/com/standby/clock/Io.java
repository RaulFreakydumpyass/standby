package com.standby.clock;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class Io {
    private Io() {}

    static String displayName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        String p = uri.getLastPathSegment();
        if (p != null) {
            int s = Math.max(p.lastIndexOf('/'), p.lastIndexOf(':'));
            return s >= 0 ? p.substring(s + 1) : p;
        }
        return null;
    }

    /** Copies the content behind a Uri into app-private storage (so the USB can be unplugged). */
    static void copy(Context c, Uri uri, File out) throws IOException {
        File tmp = new File(out.getPath() + ".part");
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Cannot open file");
        try {
            OutputStream os = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                os.flush();
            } finally {
                os.close();
            }
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) throw new IOException("Cannot save file");
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } finally {
            in.close();
        }
    }

    static String safe(String n) {
        return n.replaceAll("[^A-Za-z0-9._ -]", "_");
    }
}
