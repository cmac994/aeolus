package com.example.cmac.aeolus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import timber.log.Timber;

//Alarm Reciever class extends a broadcast receiver to start the pressure collection service when the alarm is triggered.
public class AlarmReceiver extends BroadcastReceiver {
    //Define variables for Welcome.java
    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("Alarm Recieved");
        Intent i = new Intent(context, PressureCollectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }
}