package com.vipul.discipline;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

final class Reminders {
    static final int[][] TIMES = {
            {6, 0}, {6, 1}, {9, 0}, {12, 0}, {15, 0}, {18, 0}, {19, 30}, {21, 0}, {21, 30}, {23, 0},
            {6, 2}, {7, 48}, {17, 0}
    };
    static final String[] TITLES = {
            "Good morning ☀️", "Shampoo day 🧼", "Sunscreen 🧴", "Sunscreen 🧴", "Sunscreen 🧴", "Sunscreen 🧴",
            "Face wash + serum + study 📚", "Dinner 🍽️", "Freelancing 💻", "Day done ✅",
            "Monthly weigh-in ⚖️", "Protein after gym 🥛", "Protein snack 🫘"
    };
    static final String[] MSGS = {
            "Start your routine: walk → gym → face wash → ice roller → gua sha → serum → sunscreen → lip cream → lookmaxxing.",
            "It's Wednesday — wash your hair with shampoo after the walk.",
            "Time to reapply sunscreen on your face.",
            "Time to reapply sunscreen on your face.",
            "Time to reapply sunscreen on your face.",
            "Time to reapply sunscreen on your face.",
            "Wash your face, apply serum, then personal study (7:30–9:00 PM). Open the app and start the timer.",
            "Dinner break (9:00–9:30 PM).",
            "Freelancing work starts now (9:30–11:00 PM). Start the timer.",
            "Mark your tasks in the app and wind down. No phone in bed.",
            "It's been a month. Weigh yourself now, before eating, and log it in ARG → Body.",
            "Have your whey shake (1 scoop) now.",
            "Soya chunks, sprouts or roasted chana — keep protein up and skip the junk."
    };
    /** 0 = every day, Calendar.WEDNESDAY = Wednesdays only, -1 = Monday to Saturday (gym days). */
    static final int[] DAY = {0, java.util.Calendar.WEDNESDAY, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0};
    static final int WEIGH = 10;

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
        if (DAY[i] > 0) {
            while (cal.get(Calendar.DAY_OF_WEEK) != DAY[i]) cal.add(Calendar.DAY_OF_YEAR, 1);
        } else if (DAY[i] < 0 && cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
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
