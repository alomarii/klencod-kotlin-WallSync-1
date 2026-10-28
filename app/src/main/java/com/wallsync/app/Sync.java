package com.wallsync.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Sync {
    static final int CH = 900000;           // chunk size (chars of base64)
    static final long MAX_VIDEO = 8L * 1024 * 1024;

    static String base(Context c) { return Prefs.url(c) + "/wp/" + Prefs.code(c); }

    static byte[] readAll(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toByteArray();
    }

    static void copy(File a, File b) throws IOException {
        FileInputStream in = new FileInputStream(a);
        FileOutputStream o = new FileOutputStream(b);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        o.close();
    }

    /** Uploads a file; the "ver" node is written last so the other phone never sees half data. */
    static void send(Context c, String type, File f) throws Exception {
        String base = base(c);
        String b64 = Base64.encodeToString(readAll(f), Base64.NO_WRAP);
        int n = (b64.length() + CH - 1) / CH;
        Net.req("DELETE", base + "/data.json", null);
        for (int i = 0; i < n; i++) {
            String part = b64.substring(i * CH, Math.min(b64.length(), (i + 1) * CH));
            Net.req("PUT", base + "/data/" + i + ".json", "\"" + part + "\"");
        }
        long id = System.currentTimeMillis();
        Net.req("PUT", base + "/ver.json", "{\"id\":" + id + ",\"type\":\"" + type + "\",\"n\":" + n
                + ",\"by\":\"" + Prefs.dev(c) + "\"}");
        Prefs.p(c).edit().putLong("applied", id).apply();
    }

    /** Saves as pending, applies locally, tries to send now. Returns true if sent. */
    static boolean submit(Context c, String type, File src) {
        try {
            File pend = new File(c.getFilesDir(), "pending.bin");
            copy(src, pend);
            Prefs.p(c).edit().putString("pt", type).apply();
        } catch (Exception e) { return false; }
        return flushPending(c);
    }

    static synchronized boolean flushPending(Context c) {
        String t = Prefs.p(c).getString("pt", null);
        if (t == null) return true;
        File f = new File(c.getFilesDir(), "pending.bin");
        if (!f.exists()) { Prefs.p(c).edit().remove("pt").apply(); return true; }
        if (!Prefs.ready(c)) return false;
        try {
            send(c, t, f);
            Prefs.p(c).edit().remove("pt").apply();
            return true;
        } catch (Exception e) { return false; }
    }

    static String unq(String s) {
        s = s.trim();
        if (s.equals("null")) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.replace("\\/", "/");
    }

    /** Checks the server for a new wallpaper and applies it. */
    static synchronized void check(Context c) throws Exception {
        if (!Prefs.ready(c)) return;
        String base = base(c);
        String v = Net.req("GET", base + "/ver.json", null).trim();
        if (v.isEmpty() || v.equals("null")) return;
        JSONObject j = new JSONObject(v);
        long id = j.getLong("id");
        if (id == Prefs.p(c).getLong("applied", -1)) return;
        String type = j.getString("type");
        int n = j.getInt("n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String s = unq(Net.req("GET", base + "/data/" + i + ".json", null));
            if (s.isEmpty()) throw new IOException("missing chunk");
            sb.append(s);
        }
        byte[] bytes = Base64.decode(sb.toString(), Base64.DEFAULT);
        File f = new File(c.getFilesDir(), type.equals("video") ? "wall.mp4" : "wall.jpg");
        FileOutputStream o = new FileOutputStream(f);
        o.write(bytes);
        o.close();
        Prefs.p(c).edit().putLong("applied", id).apply();
        apply(c, type, f, false);
    }

    static void apply(Context c, String type, File f, boolean fromUi) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(c.getApplicationContext());
            if (type.equals("image")) {
                Bitmap bm = BitmapFactory.decodeFile(f.getPath());
                if (bm == null) return;
                wm.setBitmap(bm, null, true, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            } else {
                Prefs.p(c).edit().putString("video", f.getPath()).apply();
                WallpaperInfo wi = wm.getWallpaperInfo();
                if (wi != null && wi.getPackageName().equals(c.getPackageName())) {
                    c.sendBroadcast(new Intent(VideoWallpaperService.RELOAD).setPackage(c.getPackageName()));
                } else if (fromUi) {
                    Intent i = liveIntent(c);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(i);
                } else {
                    notifyVideo(c, liveIntent(c));
                }
            }
        } catch (Exception ignored) { }
    }

    static Intent liveIntent(Context c) {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(c, VideoWallpaperService.class));
        return i;
    }

    static void channel(Context c, String id, String name, int imp) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(id, name, imp));
        }
    }

    static Notification.Builder builder(Context c, String ch) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
    }

    static void notifyVideo(Context c, Intent i) {
        channel(c, "wp", "خلفيات جديدة", NotificationManager.IMPORTANCE_HIGH);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = builder(c, "wp")
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("وصلت خلفية متحركة جديدة")
                .setContentText("اضغط هنا لتفعيلها (مرة واحدة فقط)")
                .setContentIntent(pi).setAutoCancel(true).build();
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(2, n);
    }

    static Bitmap load(Context c, Uri u, int max) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / s > max) s *= 2;
        BitmapFactory.Options p = new BitmapFactory.Options();
        p.inSampleSize = s;
        in = c.getContentResolver().openInputStream(u);
        Bitmap b = BitmapFactory.decodeStream(in, null, p);
        in.close();
        if (b == null) throw new IOException("bad image");
        int ori = 1;
        try {
            in = c.getContentResolver().openInputStream(u);
            ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            in.close();
        } catch (Exception ignored) { }
        int deg = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
        if (deg != 0) {
            Matrix m = new Matrix();
            m.postRotate(deg);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        }
        return b;
    }

    static File saveJpeg(Context c, Bitmap b) throws IOException {
        File f = new File(c.getCacheDir(), "up.jpg");
        for (int q = 92; q >= 50; q -= 10) {
            FileOutputStream o = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.JPEG, q, o);
            o.close();
            if (f.length() <= 6000000) break;
        }
        return f;
    }
}
