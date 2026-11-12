package com.vipul.discipline;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_BASIC = 1;
    private static final int REQ_BG = 2;

    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean capturing = false;
    private boolean pageReady = false;
    private boolean pendingBlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.ensureChannels(this);
        Reminders.scheduleAll(this);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0D1117);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.setWebChromeClient(new WebChromeClient());
        pendingBlocked = getIntent() != null && getIntent().getBooleanExtra("blocked", false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingBlocked) { pendingBlocked = false; notifyBlocked(); }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Native");
        setContentView(web);
        try {
            getWindow().setStatusBarColor(0xFF0D1117);
            getWindow().setNavigationBarColor(0xFF0D1117);
        } catch (Exception ignored) {}
        web.loadUrl("file:///android_asset/index.html");
        askPermissions();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("blocked", false)) {
            if (pageReady) notifyBlocked(); else pendingBlocked = true;
        }
    }

    private void notifyBlocked() {
        callJs("onBlocked", "{\"count\":" + Util.prefs(this).getInt("blockedCount", 0) + "}");
    }

    @Override
    protected void onResume() {
        super.onResume();
        GuardService.silence(this);
        startGuardIfReady();
        pushStatus();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void askPermissions() {
        List<String> p = new ArrayList<>();
        if (!Util.hasLocation(this)) {
            p.add(Manifest.permission.ACCESS_FINE_LOCATION);
            p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && !Util.has(this, Manifest.permission.POST_NOTIFICATIONS)) {
            p.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ_BASIC);
        else afterPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BASIC) afterPermissions();
        startGuardIfReady();
        pushStatus();
    }

    private void afterPermissions() {
        // First launch: use where you are right now as the PG/home location.
        if (Util.hasLocation(this) && !Util.hasHome(this)) captureHome();
    }

    private void startGuardIfReady() {
        if (!Util.hasHome(this) || !Util.hasLocation(this)) return;
        try {
            startForegroundService(new Intent(this, GuardService.class).setAction(GuardService.ACTION_RELOAD));
        } catch (Exception ignored) {}
        main.postDelayed(this::pushStatus, 800);
    }

    private void captureHome() {
        if (capturing) return;
        if (!Util.hasLocation(this)) { askPermissions(); return; }
        capturing = true;
        final LocationManager lm = getSystemService(LocationManager.class);
        final Location[] best = {null};
        final boolean[] done = {false};
        final LocationListener[] ref = new LocationListener[1];

        final Runnable complete = () -> {
            if (done[0]) return;
            done[0] = true;
            capturing = false;
            try { lm.removeUpdates(ref[0]); } catch (Exception ignored) {}
            Location l = best[0];
            if (l == null) {
                try {
                    l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                } catch (SecurityException ignored) {}
            }
            JSONObject res = new JSONObject();
            try {
                if (l == null) {
                    res.put("ok", false);
                    res.put("msg", "Couldn't get your location. Turn on GPS/Location and try again.");
                } else {
                    Util.saveHome(this, l.getLatitude(), l.getLongitude());
                    res.put("ok", true);
                    res.put("accuracy", l.hasAccuracy() ? l.getAccuracy() : -1);
                }
            } catch (Exception ignored) {}
            callJs("onNativeHome", res.toString());
            startGuardIfReady();
            pushStatus();
        };

        ref[0] = new LocationListener() {
            @Override
            public void onLocationChanged(Location l) {
                if (best[0] == null || l.getAccuracy() < best[0].getAccuracy()) best[0] = l;
                if (l.hasAccuracy() && l.getAccuracy() <= 25) main.post(complete);
            }
            @Override public void onStatusChanged(String p, int s, Bundle b) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, ref[0], Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, ref[0], Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {}
        main.postDelayed(complete, 20000);
    }

    private void pushStatus() {
        callJs("onNativeStatus", Util.status(this).toString());
    }

    private void callJs(String fn, String json) {
        main.post(() -> {
            if (web != null) web.evaluateJavascript("window." + fn + "&&window." + fn + "(" + json + ")", null);
        });
    }

    /** Methods the web page can call as window.Native.xxx() */
    class Bridge {
        @JavascriptInterface
        public String getStatus() { return Util.status(MainActivity.this).toString(); }

        @JavascriptInterface
        public void setHome() { main.post(MainActivity.this::captureHome); }

        @JavascriptInterface
        public void setPending(String json) {
            Util.prefs(MainActivity.this).edit().putString("pending", json).apply();
        }

        @JavascriptInterface
        public void setRadius(int meters) {
            int m = Math.max(50, Math.min(1000, meters));
            Util.prefs(MainActivity.this).edit().putInt("radius", m).apply();
            main.post(MainActivity.this::pushStatus);
        }

        @JavascriptInterface
        public void requestBackground() {
            main.post(() -> {
                if (!Util.hasLocation(MainActivity.this)) { askPermissions(); return; }
                if (Build.VERSION.SDK_INT >= 29 && !Util.hasBackground(MainActivity.this)) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG);
                }
            });
        }

        @JavascriptInterface
        public void openSettings() {
            main.post(() -> {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public boolean testAlarm() {
            GuardService s = GuardService.instance;
            if (s == null) return false;
            main.post(() -> s.alertLeft(true));
            return true;
        }

        @JavascriptInterface
        public void openAccessibility() {
            main.post(() -> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void setBlockWords(String json) {
            Util.prefs(MainActivity.this).edit().putString("blockWords", json)
                    .putLong("wordsVersion", System.currentTimeMillis()).apply();
        }

        @JavascriptInterface
        public void setStreak(String startMs) {
            try {
                Util.prefs(MainActivity.this).edit().putLong("streakStart", Long.parseLong(startMs)).apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void stopAlarm() { GuardService.silence(MainActivity.this); }
    }
}
