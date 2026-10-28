package com.wallsync.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SyncService extends Service {
    private volatile boolean run;
    private volatile HttpURLConnection cur;
    private Thread t;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Sync.channel(this, "svc", "المزامنة", NotificationManager.IMPORTANCE_LOW);
        Notification n = Sync.builder(this, "svc")
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("مزامنة الخلفية تعمل")
                .setContentText("بانتظار تغيير الخلفية من الهاتف الآخر").build();
        startForeground(1, n);
        if (t == null || !t.isAlive()) {
            run = true;
            t = new Thread(new Runnable() { @Override public void run() { loop(); } });
            t.start();
        }
        return START_STICKY;
    }

    private void loop() {
        while (run) {
            try {
                if (Prefs.ready(this)) {
                    Sync.flushPending(this);
                    Sync.check(this);
                    stream();
                }
            } catch (Exception ignored) { }
            try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
        }
    }

    /** Firebase server-sent events: fires instantly when the other phone changes the wallpaper. */
    private void stream() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(Sync.base(this) + "/ver.json").openConnection();
        cur = c;
        c.setRequestProperty("Accept", "text/event-stream");
        c.setConnectTimeout(15000);
        c.setReadTimeout(75000);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        String ev = "", line;
        while (run && (line = r.readLine()) != null) {
            if (line.startsWith("event:")) {
                ev = line.substring(6).trim();
            } else if (line.startsWith("data:") && (ev.equals("put") || ev.equals("patch"))) {
                try { Sync.check(this); } catch (Exception ignored) { }
                Sync.flushPending(this);
            }
        }
        c.disconnect();
    }

    @Override public void onDestroy() {
        run = false;
        final HttpURLConnection c = cur;
        if (c != null) new Thread(new Runnable() { @Override public void run() { try { c.disconnect(); } catch (Exception e) { } } }).start();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
