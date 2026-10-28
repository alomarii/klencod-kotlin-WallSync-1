package com.wallsync.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class DrawActivity extends Activity {
    DrawView dv;
    LinearLayout bar;
    static final int[] COLORS = {Color.BLACK, Color.WHITE, Color.RED, Color.rgb(255, 140, 0), Color.YELLOW,
            Color.rgb(0, 170, 0), Color.rgb(0, 150, 255), Color.BLUE, Color.rgb(160, 32, 240), Color.rgb(255, 105, 180)};
    static final String[] EMOJIS = {"😀", "😍", "😂", "🥰", "😎", "🤩", "😘", "🥳", "😇", "🤗",
            "❤️", "💖", "💙", "💚", "🔥", "⭐", "🌟", "✨", "🌹", "🌸",
            "🌈", "☀️", "🌙", "🦋", "🐱", "🐶", "🎉", "🎈", "👑", "💎", "👍", "🙏"};

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1));
        return b;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    LinearLayout colorRow(final boolean background) {
        LinearLayout r = row();
        for (final int c : COLORS) {
            View v = new View(this);
            v.setBackgroundColor(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(28), 1);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            v.setLayoutParams(lp);
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    if (background) { dv.bgColor = c; dv.bg = null; } else { dv.color = c; dv.emojiMode = false; }
                    dv.invalidate();
                }
            });
            r.addView(v);
        }
        return r;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        dv = new DrawView(this);
        root.addView(dv, new FrameLayout.LayoutParams(-1, -1));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xDD202020);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));

        TextView l1 = new TextView(this); l1.setText("لون القلم"); l1.setTextColor(Color.WHITE); l1.setTextSize(11);
        bar.addView(l1);
        bar.addView(colorRow(false));
        TextView l2 = new TextView(this); l2.setText("لون الخلفية"); l2.setTextColor(Color.WHITE); l2.setTextSize(11);
        bar.addView(l2);
        bar.addView(colorRow(true));

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(20);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                dv.width = 4 + p * 0.6f;
                dv.emoSize = 60 + p * 3f;
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        bar.addView(sb);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout er = row();
        for (final String e : EMOJIS) {
            TextView tv = new TextView(this);
            tv.setText(e);
            tv.setTextSize(28);
            tv.setPadding(dp(8), dp(2), dp(8), dp(2));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dv.emoji = e; dv.emojiMode = true; }
            });
            er.addView(tv);
        }
        hs.addView(er);
        bar.addView(hs);

        LinearLayout r1 = row();
        r1.addView(btn("✏️ قلم", new View.OnClickListener() { @Override public void onClick(View v) { dv.emojiMode = false; } }));
        r1.addView(btn("🖼️ خلفية", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, 3);
            }
        }));
        r1.addView(btn("↩️ تراجع", new View.OnClickListener() { @Override public void onClick(View v) { dv.undo(); } }));
        r1.addView(btn("🗑️ مسح", new View.OnClickListener() { @Override public void onClick(View v) { dv.clearAll(); } }));
        bar.addView(r1);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.BOTTOM;
        root.addView(bar, lp);

        Button tg = new Button(this);
        tg.setText("☰");
        tg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                bar.setVisibility(bar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(dp(56), dp(44));
        tl.gravity = Gravity.TOP | Gravity.START;
        tl.topMargin = dp(24);
        root.addView(tg, tl);
        Button sd = new Button(this);
        sd.setText("✅ إرسال");
        sd.setAllCaps(false);
        sd.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(); } });
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(dp(110), dp(44));
        sl.gravity = Gravity.TOP | Gravity.END;
        sl.topMargin = dp(24);
        sl.rightMargin = dp(8);
        root.addView(sd, sl);
        setContentView(root);
    }

    void send() {
        final Bitmap bm = dv.export();
        Toast.makeText(this, "جارٍ الإرسال...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try { ok = Sync.submit(DrawActivity.this, "image", Sync.saveJpeg(DrawActivity.this, bm)); } catch (Exception ignored) { }
                final boolean sent = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(DrawActivity.this, sent ? "تم الإرسال للهاتف الآخر ✅" : "سيتم الإرسال عند توفر الإنترنت", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        }).start();
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == 3 && res == RESULT_OK && d != null && d.getData() != null) {
            try {
                dv.bg = Sync.load(this, d.getData(), 2000);
                dv.invalidate();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر فتح الصورة", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
