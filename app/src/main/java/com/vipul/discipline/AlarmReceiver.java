package com.vipul.discipline;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int i = intent.getIntExtra("i", -1);
        if (i < 0 || i >= Reminders.TIMES.length) return;
        Util.ensureChannels(c);
        Notification n = new Notification.Builder(c, Util.CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(Reminders.TITLES[i])
                .setContentText(Reminders.MSGS[i])
                .setStyle(new Notification.BigTextStyle().bigText(Reminders.MSGS[i]))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(Util.openApp(c, 300 + i))
                .setAutoCancel(true)
                .build();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        try { if (nm != null) nm.notify(200 + i, n); } catch (SecurityException ignored) {}
        Reminders.schedule(c, i);
    }
}
