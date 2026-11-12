package com.vipul.discipline;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;

import java.util.List;

/** Watches the phone's location and rings when you leave your PG/home with compulsory tasks pending. */
public class GuardService extends Service implements LocationListener {
    static final String ACTION_RELOAD = "com.vipul.discipline.RELOAD";
    static final String ACTION_STOP = "com.vipul.discipline.STOP_ALARM";
    static final int NOTIF_GUARD = 1;
    static final int NOTIF_LEAVE = 2;

    static volatile GuardService instance;

    private LocationManager lm;
    private Boolean inside = null;
    private int outsideHits = 0;
    private boolean registered = false;
    private Ringtone ring;
    private Vibrator vib;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::stopRinging;

    static void silence(Context c) {
        GuardService s = instance;
        if (s != null) s.stopRinging();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Util.ensureChannels(this);
        lm = getSystemService(LocationManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopRinging();
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(NOTIF_LEAVE);
            return START_STICKY;
        }
        if (!Util.hasHome(this) || !Util.hasLocation(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RELOAD.equals(action)) {
            inside = null;
            outsideHits = 0;
        }
        startUpdates();
        return START_STICKY;
    }

    private void goForeground() {
        Notification n = new Notification.Builder(this, Util.CH_GUARD)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Watching your PG location")
                .setContentText("Alarm rings if you leave with compulsory tasks pending")
                .setOngoing(true)
                .setContentIntent(Util.openApp(this, 10))
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_GUARD, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIF_GUARD, n);
            }
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void startUpdates() {
        if (registered || lm == null) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 15, this, Looper.getMainLooper());
            registered = true;
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 15, this, Looper.getMainLooper());
            registered = true;
        } catch (SecurityException | IllegalArgumentException ignored) {}
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!Util.hasHome(this)) return;
        float acc = l.hasAccuracy() ? l.getAccuracy() : 100f;
        if (acc > 200) return;
        float[] r = new float[1];
        Location.distanceBetween(Util.lat(this), Util.lng(this), l.getLatitude(), l.getLongitude(), r);
        float d = r[0];
        int rad = Util.radius(this);
        Util.prefs(this).edit().putFloat("lastDist", d).putLong("lastFix", System.currentTimeMillis()).apply();

        if (d <= rad) {
            inside = true;
            outsideHits = 0;
            return;
        }
        if (d <= rad + Math.min(acc, 50f)) return; // too close to the edge to be sure
        outsideHits++;
        if (outsideHits >= 2 || acc <= 40) {
            if (Boolean.TRUE.equals(inside)) alertLeft(false);
            inside = false;
        }
    }

    void alertLeft(boolean test) {
        List<String> pending = Util.pending(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (pending.isEmpty()) {
            Notification ok = new Notification.Builder(this, Util.CH_LEAVE)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(test ? "Test: all tasks done ✅" : "All compulsory tasks done ✅")
                    .setContentText("Have a great day. Carry your sunscreen!")
                    .setAutoCancel(true)
                    .build();
            try { nm.notify(NOTIF_LEAVE, ok); } catch (SecurityException ignored) {}
            return;
        }
        Intent stop = new Intent(this, GuardService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 3, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = (test ? "Test — " : "") + "🚨 You left without " + pending.size() + " task" + (pending.size() > 1 ? "s" : "");
        String body = "Go back and do these now:\n• " + TextUtils.join("\n• ", pending);
        Notification n = new Notification.Builder(this, Util.CH_LEAVE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(TextUtils.join(", ", pending))
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(Util.openApp(this, 4))
                .setFullScreenIntent(Util.openApp(this, 5), true)
                .addAction(new Notification.Action.Builder((Icon) null, "Stop alarm", stopPi).build())
                .setAutoCancel(true)
                .build();
        try { nm.notify(NOTIF_LEAVE, n); } catch (SecurityException ignored) {}
        startRinging();
    }

    private void startRinging() {
        stopRinging();
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ring = RingtoneManager.getRingtone(this, u);
            if (ring != null) {
                ring.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                if (Build.VERSION.SDK_INT >= 28) ring.setLooping(true);
                ring.play();
            }
        } catch (Exception ignored) {}
        try {
            vib = getSystemService(Vibrator.class);
            if (vib != null) vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 400}, 0));
        } catch (Exception ignored) {}
        handler.postDelayed(autoStop, 60000);
    }

    void stopRinging() {
        handler.removeCallbacks(autoStop);
        try { if (ring != null) ring.stop(); } catch (Exception ignored) {}
        ring = null;
        try { if (vib != null) vib.cancel(); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        instance = null;
        stopRinging();
        try { if (lm != null) lm.removeUpdates(this); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // Older Android versions require these to be implemented.
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
}
