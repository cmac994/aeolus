package com.example.cmac.aeolus;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import net.grandcentrix.tray.AppPreferences;
import timber.log.Timber;
import static android.graphics.Color.WHITE;

public class PrefActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pref_with_toolbar);

        //Initialize toolbar for preference activity
        android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) findViewById(com.example.cmac.aeolus.R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        //Disable default title
        toolbar.setTitle("");
        toolbar.setSubtitle("");

        Timber.d("Settings Opened");
        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(R.id.content_frame, new PrefsFragment()).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_realtime:
                Intent intent1 = new Intent(this,MainActivity.class);
                this.startActivity(intent1);
                return true;
            case R.id.action_collected:
                Intent intent2 = new Intent(this,PressureCharts.class);
                this.startActivity(intent2);
                return true;
            case R.id.action_settings:
                Intent intent3 = new Intent(this,PrefActivity.class);
                this.startActivity(intent3);
                return true;
            case R.id.action_preslog:
                Intent intent4 = new Intent(this,PressureLog.class);
                this.startActivity(intent4);
                return true;
            case R.id.action_dplog:
                Intent intent5 = new Intent(this,PressureChangeLog.class);
                this.startActivity(intent5);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static class PrefsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        public PrefsFragment(){}
        PendingIntent pendingIntent;
        int obfreq;
        CheckBoxPreference checkBoxPref;
        AppPreferences appPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings);
            PreferenceManager.setDefaultValues(getActivity(), R.xml.settings, false);
            // Create the new ListPref
            checkBoxPref = new CheckBoxPreference(getActivity());
            // create a preference accessor. This is for global app preferences.
            appPreferences = new AppPreferences(getActivity().getApplicationContext());
            Timber.d("Preferences loaded");
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            if (view != null) {
                view.setBackgroundColor(WHITE); // or whatever color value you want
            }
            return view;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            //Handle pressure units scale change.
            if (key.equals("pscale")) {
                String scalestr = sharedPreferences.getString("pscale","hPa");
                appPreferences.put("pscale",scalestr);
            }
            //Handle change in observation frequency
            if (key.equals("obfreqint")) {
                obfreq = sharedPreferences.getInt("obfreqint", 15);
                obfreq = obfreq * 60;
                appPreferences.put("obfreq", Integer.toString(obfreq));
                cancelAlarm();
                startRepeatAlarm(60 * 1000);
            }
            //Handle change in pressure collection
            if (key.equals("collectpressure")) {
                boolean pcollect = sharedPreferences.getBoolean("collectpressure", true);
                appPreferences.put("collectpressure", pcollect);
                boolean pcollect_orig = appPreferences.getBoolean("origcollect", true);
                Timber.d("pcollect: %s, pcollect_orig: %s", pcollect, pcollect_orig);
                if ((!pcollect_orig) && (pcollect)) {
                    appPreferences.put("collectswitch", true);
                }
                if (!pcollect) {
                    cancelAlarm();
                }
                appPreferences.put("origcollect", pcollect);
            }
        }

        // Set Repeat Alarm
        public void startRepeatAlarm(int interval) {
            Timber.d("Start Repeat Alarm");
            Intent alarmIntent = new Intent(getActivity().getApplicationContext(), AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(getActivity().getApplicationContext(), 12, alarmIntent,PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getActivity().getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            Timber.d("Set inexact repeating alarm to run at an interval of %d",interval);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 100, interval, pendingIntent);
        }

        //Cancel repeating alarm.
        public void cancelAlarm() {
            Timber.d("cancel current");
            Intent alarmIntent = new Intent(getActivity().getApplicationContext(), AlarmReceiver.class);
            pendingIntent = PendingIntent.getBroadcast(getActivity().getApplicationContext(), 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager am = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
            am.cancel(pendingIntent);
            if (pendingIntent != null) {
                pendingIntent.cancel();
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            Timber.d("onResume called");
        }

        @Override
        public void onPause() {
            Timber.d("onPause");
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onDestroy() {
            Timber.d("onDestroy");
            super.onDestroy();
        }
    }
}