package com.vipul.discipline;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Self-control blocker. Watches what you type and the address bar of browsers;
 * if it sees an adult keyword or site, it backs out to the home screen.
 */
public class BlockerService extends AccessibilityService {

    /** Matched anywhere, after removing spaces/symbols (catches "p o r n", "porn-hub"). */
    static final List<String> SUBSTRINGS = Arrays.asList(
            "porn", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "brazzers", "onlyfans",
            "hentai", "chaturbate", "spankbang", "eporner", "stripchat", "rule34", "nsfw", "fapello",
            "beeg", "tube8", "motherless", "camsoda", "livejasmin", "bongacams", "chudai", "antarvasna",
            "indianxxx", "desixxx", "sexvideo", "sexyvideo", "nudevideo", "nangivideo", "masturbat");

    /** Matched as whole words only (so "Essex" or "nudge" don't trigger). */
    static final List<String> WORDS = Arrays.asList(
            "sex", "sexy", "xxx", "nude", "nudes", "naked", "boobs", "tits", "milf", "nangi", "bf video");

    static final String[] URL_BAR_IDS = {
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.chrome.beta:id/url_bar",
            "com.brave.browser:id/url_bar",
            "com.microsoft.emmx:id/url_bar",
            "com.kiwibrowser.browser:id/url_bar",
            "com.vivaldi.browser:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/url_bar_title",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser:id/url_field",
            "com.opera.mini.native:id/url_field",
            "com.UCMobile.intl:id/titlebar_url",
            "com.mi.globalbrowser:id/url",
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.google.android.googlequicksearchbox:id/googleapp_search_box",
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBlock = 0;
    private long lastScan = 0;
    private Pattern wordPattern;
    private List<String> extraSub = new ArrayList<>();
    private long wordsVersion = -1;

    static boolean isEnabled(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(c.getPackageName() + "/") && s.contains("BlockerService");
        } catch (Exception e) {
            return false;
        }
    }

    private void loadWords() {
        long v = Util.prefs(this).getLong("wordsVersion", 0);
        if (v == wordsVersion && wordPattern != null) return;
        wordsVersion = v;
        List<String> words = new ArrayList<>(WORDS);
        extraSub = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Util.prefs(this).getString("blockWords", "[]"));
            for (int i = 0; i < a.length(); i++) {
                String w = a.getString(i).toLowerCase(Locale.ROOT).trim();
                if (w.length() < 2) continue;
                if (w.length() >= 5) extraSub.add(w.replaceAll("[^a-z0-9]", ""));
                else words.add(w);
            }
        } catch (Exception ignored) {}
        StringBuilder sb = new StringBuilder("(?<![a-z])(");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(Pattern.quote(words.get(i)));
        }
        sb.append(")(?![a-z])");
        wordPattern = Pattern.compile(sb.toString());
    }

    boolean matches(CharSequence cs) {
        if (cs == null || cs.length() < 2) return false;
        String t = cs.toString().toLowerCase(Locale.ROOT);
        String compact = t.replaceAll("[^a-z0-9]", "");
        for (String s : SUBSTRINGS) if (compact.contains(s)) return true;
        for (String s : extraSub) if (!s.isEmpty() && compact.contains(s)) return true;
        return wordPattern.matcher(t).find();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        if (!Util.prefs(this).getBoolean("blockerOn", true)) return;
        loadWords();

        int type = e.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence c : e.getText()) sb.append(c).append(' ');
            if (matches(sb)) { block(); return; }
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence c : e.getText()) sb.append(c).append(' ');
            if (matches(sb)) { block(); return; }
        }
        // Browser address bars (throttled)
        long now = System.currentTimeMillis();
        if (now - lastScan < 600) return;
        lastScan = now;
        AccessibilityNodeInfo root;
        try { root = getRootInActiveWindow(); } catch (Exception ex) { return; }
        if (root == null) return;
        try {
            for (String id : URL_BAR_IDS) {
                if (!id.startsWith(pkg + ":")) continue;
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes == null) continue;
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null && matches(n.getText())) { block(); return; }
                }
            }
        } catch (Exception ignored) {}
    }

    private void block() {
        long now = System.currentTimeMillis();
        if (now - lastBlock < 2500) return;
        lastBlock = now;
        int count = Util.prefs(this).getInt("blockedCount", 0) + 1;
        Util.prefs(this).edit().putInt("blockedCount", count).putLong("lastBlocked", now).apply();

        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 150);
        handler.postDelayed(() -> {
            long start = Util.prefs(this).getLong("streakStart", 0);
            String msg = "🚫 Blocked. Stay strong";
            if (start > 0) msg += " — day " + ((now - start) / 86400000L) + " of your streak";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            try {
                Intent i = new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("blocked", true);
                startActivity(i);
            } catch (Exception ignored) {}
        }, 450);
    }

    @Override
    public void onInterrupt() {}
}
