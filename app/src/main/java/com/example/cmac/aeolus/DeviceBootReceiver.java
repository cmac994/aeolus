package com.example.cmac.aeolus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import net.grandcentrix.tray.AppPreferences;

//Class designed to restart alarms when the device restarts.
public class DeviceBootReceiver extends BroadcastReceiver {

    int obfreq;
    PendingIntent pendingIntent;
    AppPreferences appPreferences;
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            //Retrieve app preferences (temporary storage)
            appPreferences = new AppPreferences(context.getApplicationContext());
            //get default observation frequency from app preferences
            String defaultfreq = appPreferences.getString("obfreq", null);
            if (defaultfreq != null) {
                obfreq = Integer.parseInt(defaultfreq);
            } else {
                obfreq = 900;
            }
            //Determine if pressure collection is enabled, if so restart alarms when the device boots up.
            boolean pcollect = appPreferences.getBoolean("collectpressure",true);
            if (pcollect) {
                startRepeatAlarm(obfreq*1000,context);
            }
        }
    }
    //Define a function to initialize pressure collection via an inexact repeating alarm.
    public void startRepeatAlarm(int interval, Context context) {
        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), interval, pendingIntent);
    }
}