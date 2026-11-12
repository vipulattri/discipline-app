package com.vipul.discipline;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

final class Reminders {
    static final int[][] TIMES = {
            {6, 0}, {6, 1}, {9, 0}, {12, 0}, {15, 0}, {18, 0}, {19, 30}, {21, 0}, {21, 30}, {23, 0}
    };
    static final String[] TITLES = {
            "Good morning ☀️", "Shampoo day 🧼", "Sunscreen 🧴", "Sunscreen 🧴", "Sunscreen 🧴", "Sunscreen 🧴",
            "Face wash + study 📚", "Dinner 🍽️", "Freelancing 💻", "Day done ✅"
    };
    static final String[] MSGS = {
            "Start your routine: walk → gym → face wash → ice roller → gua sha → serum → sunscreen → lip cream → lookmaxxing.",
            "It's Wednesday — wash your hair with shampoo after the walk.",
            "Time to reapply sunscreen on your face.",
            "Time to reapply sunscreen on your face.",
            "Time to reapply sunscreen on your face.",
            "Time to reapply sunscreen on your face.",
            "Wash your face, then personal study (7:30–9:00 PM). Open the app and start the timer.",
            "Dinner break (9:00–9:30 PM).",
            "Freelancing work starts now (9:30–11:00 PM). Start the timer.",
            "Mark your tasks in the app and wind down. No phone in bed."
    };
    static final boolean[] WEDNESDAY_ONLY = {false, true, false, false, false, false, false, false, false, false};

    private Reminders() {}

    static void scheduleAll(Context c) {
        for (int i = 0; i < TIMES.length; i++) schedule(c, i);
    }

    static void schedule(Context c, int i) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, TIMES[i][0]);
        cal.set(Calendar.MINUTE, TIMES[i][1]);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        if (WEDNESDAY_ONLY[i]) {
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.WEDNESDAY) cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        Intent it = new Intent(c, AlarmReceiver.class).putExtra("i", i);
        PendingIntent pi = PendingIntent.getBroadcast(c, 100 + i, it,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        long t = cal.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }
}
