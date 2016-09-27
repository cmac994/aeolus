package com.example.cmac.aeolus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import timber.log.Timber;

//Alarm Reciever class extends a broadcast receiver to start the pressure collection service when the alarm is triggered.
public class AlarmReceiver extends BroadcastReceiver {
    //Define variables for Welcome.java
    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("Alarm Recieved");
        Intent i = new Intent(context, PressureCollectionService.class);
        context.startService(i);
    }
}
