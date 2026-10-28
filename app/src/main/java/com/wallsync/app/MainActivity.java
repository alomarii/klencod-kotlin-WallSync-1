package com.wallsync.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;

public class MainActivity extends Activity {
    EditText code;
    LinearLayout l;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView text(String t, int size) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setPadding(0, dp(8), 0, dp(4));
        l.addView(v);
        return v;
    }

    Button btn(String t, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(c);
        l.addView(b);
        return b;
    }

    void toast(final String s) {
        runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, s, Toast.LENGTH_LONG).show(); } });
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(36), dp(16), dp(24));
        sv.addView(l);

        text("مزامنة الخلفية بين هاتفين", 22);
        text("رمز الربط (نفسه على الهاتفين، 6 أحرف أو أرقام فأكثر):", 14);
        code = new EditText(this);
        code.setHint("مثال: K7M2QX9A");
        code.setSingleLine(true);
        code.setTextDirection(View.TEXT_DIRECTION_LTR);
        code.setText(Prefs.p(this).getString("code", ""));
        l.addView(code);

        btn("🎲 توليد رمز عشوائي", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
                SecureRandom r = new SecureRandom();
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < 10; i++) s.append(a.charAt(r.nextInt(a.length())));
                code.setText(s.toString());
            }
        });
        btn("💾 حفظ وتشغيل المزامنة", new View.OnClickListener() {
            @Override public void onClick(View v) { saveAndStart(); }
        });

        text("إرسال خلفية للهاتف الآخر:", 16);
        btn("🖼️ اختيار صورة", new View.OnClickListener() {
            @Override public void onClick(View v) { if (check()) pick("image/*", 1); }
        });
        btn("✍️ كتابة بخط اليد + إيموجي", new View.OnClickListener() {
            @Override public void onClick(View v) { if (check()) startActivity(new Intent(MainActivity.this, DrawActivity.class)); }
        });
        btn("🎬 فيديو / خلفية متحركة (حتى 8 ميجابايت)", new View.OnClickListener() {
            @Override public void onClick(View v) { if (check()) pick("video/*", 2); }
        });
        text("الخلفية المتحركة: يطلب الأندرويد تأكيدها بضغطة واحدة فقط عند أول مرة على كل هاتف، وبعدها تتغير الفيديوهات تلقائياً.", 12);
        btn("تفعيل الخلفية المتحركة يدوياً على هذا الهاتف", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(Sync.liveIntent(MainActivity.this)); }
                catch (Exception e) { toast("غير مدعوم على هذا الجهاز"); }
            }
        });
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (Prefs.ready(this)) startSync(false);
    }

    boolean check() {
        if (!Prefs.ready(this)) { toast("احفظ رمز الربط أولاً"); return false; }
        return true;
    }

    void saveAndStart() {
        String c = code.getText().toString().trim().replaceAll("[^A-Za-z0-9]", "");
        if (c.length() < 6) { toast("الرمز يجب ألا يقل عن 6 أحرف أو أرقام"); return; }
        code.setText(c);
        Prefs.p(this).edit().putString("code", c).apply();
        startSync(true);
        toast("تم الحفظ ✅ المزامنة تعمل");
    }

    void startSync(boolean restart) {
        try {
            Intent i = new Intent(this, SyncService.class);
            if (restart) stopService(i);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception ignored) { }
    }

    void pick(String type, int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(type);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, req);
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (res != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        final boolean video = req == 2;
        toast("جارٍ التجهيز والإرسال...");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File f;
                    if (video) {
                        f = new File(getCacheDir(), "up.mp4");
                        InputStream in = getContentResolver().openInputStream(u);
                        FileOutputStream o = new FileOutputStream(f);
                        byte[] buf = new byte[16384];
                        long total = 0;
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            total += n;
                            if (total > Sync.MAX_VIDEO) { in.close(); o.close(); toast("الفيديو أكبر من 8 ميجابايت، اختر فيديو أقصر"); return; }
                            o.write(buf, 0, n);
                        }
                        in.close();
                        o.close();
                    } else {
                        Bitmap bm = Sync.load(MainActivity.this, u, 2200);
                        f = Sync.saveJpeg(MainActivity.this, bm);
                    }
                    boolean sent = Sync.submit(MainActivity.this, video ? "video" : "image", f);
                    toast(sent ? "تم الإرسال للهاتف الآخر ✅" : "سيتم الإرسال تلقائياً عند توفر الإنترنت");
                } catch (Exception e) {
                    toast("حدث خطأ في قراءة الملف");
                }
            }
        }).start();
    }
}
