package com.example.cmac.aeolus;

import android.app.Application;
import timber.log.Timber;

//Main App Class
public class MainApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //Set log statements to display only iff is compiled in debug mode.
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}