package com.vipul.discipline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Reminders.scheduleAll(c);
        if (Util.hasHome(c) && Util.hasLocation(c)) {
            try {
                c.startForegroundService(new Intent(c, GuardService.class).setAction(GuardService.ACTION_RELOAD));
            } catch (Exception ignored) {
                // Android may refuse to start location watching after boot; it restarts when the app is opened.
            }
        }
    }
}
