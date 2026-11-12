package com.vipul.discipline;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class Util {
    static final String CH_REMIND = "reminders";
    static final String CH_LEAVE = "leave_alarm";
    static final String CH_GUARD = "guard";

    static final List<String> DEFAULT_MUST = Arrays.asList(
            "Morning walk", "Gym", "Face wash", "Ice roller", "Gua sha", "Apply serum", "Sunscreen", "Lip cream");

    private Util() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("discipline", Context.MODE_PRIVATE);
    }

    static boolean hasHome(Context c) { return prefs(c).contains("lat"); }

    static double lat(Context c) { return Double.parseDouble(prefs(c).getString("lat", "0")); }

    static double lng(Context c) { return Double.parseDouble(prefs(c).getString("lng", "0")); }

    static int radius(Context c) { return prefs(c).getInt("radius", 150); }

    static void saveHome(Context c, double lat, double lng) {
        prefs(c).edit().putString("lat", String.valueOf(lat)).putString("lng", String.valueOf(lng))
                .remove("lastDist").apply();
    }

    static void clearHome(Context c) {
        prefs(c).edit().remove("lat").remove("lng").remove("lastDist").apply();
    }

    static boolean has(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasLocation(Context c) {
        return has(c, Manifest.permission.ACCESS_FINE_LOCATION) || has(c, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    static boolean hasBackground(Context c) {
        return Build.VERSION.SDK_INT < 29 || has(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Compulsory tasks still pending today, as last reported by the app UI. */
    static List<String> pending(Context c) {
        String s = prefs(c).getString("pending", null);
        List<String> out = new ArrayList<>();
        if (s == null) return new ArrayList<>(DEFAULT_MUST);
        try {
            JSONObject o = new JSONObject(s);
            JSONArray a = today().equals(o.optString("date")) ? o.optJSONArray("pending") : o.optJSONArray("all");
            if (a == null) return new ArrayList<>(DEFAULT_MUST);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception e) {
            return new ArrayList<>(DEFAULT_MUST);
        }
        return out;
    }

    static JSONObject status(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("home", hasHome(c));
            o.put("radius", radius(c));
            o.put("loc", hasLocation(c));
            o.put("bg", hasBackground(c));
            o.put("notif", Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS));
            o.put("watching", GuardService.instance != null);
            o.put("lastDist", (double) prefs(c).getFloat("lastDist", -1f));
        } catch (Exception ignored) {}
        return o;
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        AudioAttributes alarmAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm == null) alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationChannel remind = new NotificationChannel(CH_REMIND, "Routine reminders", NotificationManager.IMPORTANCE_HIGH);
        remind.setDescription("Sunscreen, study, dinner and freelancing reminders");
        remind.setSound(alarm, alarmAttrs);
        remind.enableVibration(true);
        remind.setVibrationPattern(new long[]{0, 500, 300, 500});
        nm.createNotificationChannel(remind);

        NotificationChannel leave = new NotificationChannel(CH_LEAVE, "Left PG with tasks pending", NotificationManager.IMPORTANCE_HIGH);
        leave.setDescription("Rings when you leave your PG/home before finishing compulsory tasks");
        leave.setSound(null, null);
        leave.enableVibration(true);
        nm.createNotificationChannel(leave);

        NotificationChannel guard = new NotificationChannel(CH_GUARD, "Location watch", NotificationManager.IMPORTANCE_LOW);
        guard.setDescription("Keeps the PG location check running");
        nm.createNotificationChannel(guard);
    }

    static PendingIntent openApp(Context c, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
