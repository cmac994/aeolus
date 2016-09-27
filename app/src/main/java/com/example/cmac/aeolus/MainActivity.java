package com.example.cmac.aeolus;
import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.widget.TextView;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;
import com.github.mikephil.charting.utils.Utils;
import com.greysonparrelli.permiso.Permiso;
import net.grandcentrix.tray.AppPreferences;
import java.text.DecimalFormat;
import java.util.Locale;
import timber.log.Timber;

//Main Activity. This is the Home Page for the App. It displays real-time sensor data from the phone barometer.
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //Define variables for Welcome.java
    Boolean locPermissionsGranted;
    int pcnt = 0; String versionName;

    //Initialize global variables
    PendingIntent pendingIntent;
    //Limit observational history in realtime plot to 150 obs.
    int HISTORY_SIZE;
    boolean shiftingEntry = false;
    private Sensor pSensor = null;
    SensorManager mSensorManager = null;
    private LineChart mChart; YAxis lAxis;
    long epochtime; float maxval, minval;
    TextView phonePressure;
    int labelnum = 11;

    String pscale;
    AppPreferences appPreferences;

    //Set default observation frequency (15min)
    int obfreq = 900;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Determine if device has necessary sensors
        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_PRESSURE)) {
            if (sensor.getType() == Sensor.TYPE_PRESSURE) {
                pSensor = sensor;
            }
        }

        //Load Sad Cloud image if phone does not have barometer.
        if (pSensor == null) {
            setContentView(R.layout.error_message);
        } else {
            setContentView(R.layout.activity_main);
        }

        Permiso.getInstance().setActivity(this);
        appPreferences = new AppPreferences(getApplicationContext()); // this Preference comes for free from the library

        //Get pressure scale
        pscale = appPreferences.getString("pscale", "hPa");
        HISTORY_SIZE = appPreferences.getInt("historysize",150);

        locPermissionsGranted = appPreferences.getBoolean("locgranted", false);
        versionName = "v" + BuildConfig.VERSION_NAME;

        Timber.d("Version name: %s",versionName);

        // Set a toolbar to replace the action bar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        //Disable default toolbar title
        toolbar.setTitle("");
        toolbar.setSubtitle("");

        //Determine if location permissions have been granted at runtime. If not request them.
        if ((!locPermissionsGranted) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                locPermissionRequest();
                Timber.d("Location Request");
        } else {

            //get default submission frequency from shared pref
            obfreq = appPreferences.getInt("obfreq",900);
            //Modify default submission frequency by taking into account battery capacity
            if (obfreq == 900) {
                double bcapacity = getBatteryCapacity();
                //Phones with limited battery capacity should submit obs at reduced frequency to conserve battery.
                if (bcapacity < 2000) {
                    appPreferences.put("obfreq",3600);
                    obfreq = 3600;
                } else if ((bcapacity >= 2000) && (bcapacity < 2700)) {
                    appPreferences.put("obfreq",1800);
                    obfreq = 1800;
                }
            }

            //Prevent sensor collection if sensors don't exist in device
            if (pSensor == null) {
                appPreferences.put("collectpressure",false);
            }

            //Get current time,forecast update frequency, and google play status to determine if forecast data should be updated
            epochtime = System.currentTimeMillis() / 1000L;

            //Determine if alarm is up and running.
            Boolean alarmUp = appPreferences.getBoolean("alarm_set",false);
            Boolean collectswitch = appPreferences.getBoolean("collectswitch",false);

            Timber.d("AlarmUP: %s",alarmUp);
            /*Set initial alarms ifpressure collection has been turned back on
            or if the app has just been installed and opened for the first time*/
            if ((!alarmUp) && (pSensor != null)) {
                Timber.d("Alarm not yet set, start alarm");
                appPreferences.put("StartTime", System.currentTimeMillis());
                appPreferences.put("alarm_set", true);
                if (collectswitch) {
                    appPreferences.put("collectswitch", false);
                }
                startRepeatAlarm(obfreq*1000);
            } else {
                Timber.d("Alarm already set no need to start it");
            }
            //Initialize textview for real-time pressure.
            phonePressure = (TextView) findViewById(R.id.sensorval);
        }
    }

    //Create y-axis value formatter to ensure appropriate precision is provided for real-time chart.
    public class MyYAxisValueFormatter3 implements YAxisValueFormatter {
        private DecimalFormat mFormat;
        public MyYAxisValueFormatter3 () {
            mFormat = new DecimalFormat("###,###,##0.000"); // use one decimal
        }
        @Override
        public String getFormattedValue(float value, YAxis yAxis) {
            // access the YAxis object to get more information
            return mFormat.format(value); // e.g. append a dollar-sign
        }
    }

    //Set decimal places for Yaxis labels
    public class MyYAxisValueFormatter implements YAxisValueFormatter {
        private DecimalFormat mFormat;
        public MyYAxisValueFormatter () {
            mFormat = new DecimalFormat("###,###,##0.00"); // use two decimals
        }
        @Override
        public String getFormattedValue(float value, YAxis yAxis) {
            // write your logic here
            // access the YAxis object to get more information
            return mFormat.format(value); // e.g. append a dollar-sign
        }
    }

    //Start Repeat Alarm
    public void startRepeatAlarm(int interval) {
        Timber.d("Alarm started");
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, alarmIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        //Set inexact repeating alarm to run at the specified interval.
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), interval, pendingIntent);
    }

    /*Define class to determine the battery capacity of the device so that the observation frequency
    can be tailored to the individual phone*/
    public double getBatteryCapacity() {
        Object mPowerProfile_ = null;
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";
        try {
            mPowerProfile_ = Class.forName(POWER_PROFILE_CLASS)
                    .getConstructor(Context.class).newInstance(this.getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        double batteryCapacity = 0;
        try {
            batteryCapacity = (Double) Class
                    .forName(POWER_PROFILE_CLASS)
                    .getMethod("getAveragePower", java.lang.String.class)
                    .invoke(mPowerProfile_, "battery.capacity");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return batteryCapacity;
    }

    // Called whenever a new sensor reading is taken.
    public synchronized void onSensorChanged(SensorEvent sensorEvent) {
        float minlimit = 650; float maxlimit = 1100;
        //Process Sensor data for live plotting.
        if ((sensorEvent.sensor.getType() == Sensor.TYPE_PRESSURE) && (sensorEvent.values[0] >= minlimit) && (sensorEvent.values[0] <= maxlimit)) {
            float pres = sensorEvent.values[0];
            //Convert pressure to appropriate units.
            switch (pscale) {
                case ("inHg"):
                    pres = pres / 33.8638866667f;
                    break;
                case ("mmHg"):
                    pres = pres / 1.3332239f;
                    break;
                case ("psi"):
                    pres = pres * 0.0145038f;
                    break;
                case ("kPa"):
                    pres = pres * 0.1f;
                    break;
            }
            //Set textview for live pressure
            phonePressure.setText(getString(R.string.live_pressure, String.format(Locale.getDefault(), "%.2f", pres), pscale));
            //Scale graph axis based on choosen pressure units.
            switch (pscale) {
                case ("hPa"):
                    minval = roundToTenth(mChart.getYMin() - 0.1f);
                    maxval = roundToTenth(mChart.getYMax() + 0.1f);
                    break;
                case ("mmHg"):
                    minval = roundToTenth(mChart.getYMin() - 0.1f);
                    maxval = roundToTenth(mChart.getYMax() + 0.1f);
                    break;
                case ("kPa"):
                    minval = roundToTwenty(mChart.getYMin() - 0.025f);
                    maxval = roundToTwenty(mChart.getYMax() + 0.025f);
                    break;
                default:
                    minval = roundToFifty(mChart.getYMin() - 0.01f);
                    maxval = roundToFifty(mChart.getYMax() + 0.01f);
                    break;
            }
            //Redefine the y-axis dynamically.
            lAxis.setAxisMinValue(minval);
            lAxis.setAxisMaxValue(maxval);

            float mrange = Math.abs(maxval - minval);
            labelnum = Math.round(setLabels(mrange));

            lAxis.setLabelCount(labelnum+1,true);

            if (!shiftingEntry) {
                //Adjust bounds at start of graphing and if pressure change is significant enough (so users may zoom, etc.)
                addEntry(pres);
            }
        }
    }

    //Define rounding functions
    public float roundToTwenty(float val) {
        return Math.round(val * 20f) / 20.0f;
    }
    public float roundToFifty(float val) {
        return Math.round(val * 50f) / 50.0f;
    }
    //Define function to round to nearest tenths place
    public float roundToTenth(float val) {
        return (float) (Math.floor(val*10 + 0.5)/10.0);
    }
    public void onAccuracyChanged(Sensor sensor, int i) {
        // Not interested in this event
    }

    //Add chart entry onSensorChanged
    private void addEntry(float pval) {
        //Retrieve line data from existing chart data
        LineData data = mChart.getData();
        if (data != null) {
            //If line dataset doesn't yet exist create it.
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            // Make rolling window
            if (data.getXValCount() > HISTORY_SIZE) {
                //Remove first value in XVal array
                data.getXVals().remove(0);
                set.removeEntry(0);

                //Add XVal entry
                data.getXVals().add(Integer.toString(set.getEntryCount()));
                data.addEntry(new Entry(pval, HISTORY_SIZE), 0);

                shiftingEntry=false;
                //Shift all entries down one index (after removing 0th index)
                for (int i=0; i < set.getEntryCount(); i++) {
                    Entry e = set.getEntryForXIndex(i);
                    if (e==null) continue;
                    e.setXIndex(e.getXIndex() - 1);
                    shiftingEntry=true;
                }
                shiftingEntry=false;
            }
            else{
                //Add entry
                data.getXVals().add(Integer.toString(set.getEntryCount()));
                data.addEntry(new Entry(pval, data.getXValCount()-1), 0);
            }

            // update the Chart UI.
            mChart.notifyDataSetChanged();
            mChart.invalidate();
        }
    }

    //Define options for real-time line chart
    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Realtime Pressure");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.TRANSPARENT);
        set.setDrawCircles(false);
        set.setDrawCircleHole(false);
        set.setLineWidth(2.5f);
        set.setDrawCubic(false);
        set.setDrawValues(false);

        if (Utils.getSDKInt() >= 18) {
            // fill drawable only supported on api level 18 and above
            Drawable drawable = ContextCompat.getDrawable(this, R.drawable.fade_blue);
            set.setFillDrawable(drawable);
        } else {
            set.setFillColor(ContextCompat.getColor(this, R.color.colorPrimary));
        }

        set.setDrawFilled(true);

        return set;
    }

    //Check location permissions (primarily for API > 22)
    private boolean checkLocPermission() {
        int res;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            res = 0;
        } else {
            res = 1;
        }
        return res == 0;
    }

    //Use Permisio Library to request permissions. If permissions request is denied remind user why you're asking for said permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permiso.getInstance().onRequestPermissionResult(requestCode, permissions, grantResults);
        if (permissions[0].contains("LOCATION")) {
            pcnt += 1;
            if (grantResults[0] == -1) {
                if (pcnt == 1) {
                    locPermissionRequest();
                } else {
                    getAlertDialog().show();
                }
            }
        }
    }

    //Define function to set y-tick label count and interval
    public float setLabels(float mrange) {
        float mdiff;
        if ((mrange > 500f) && (mrange <= 1000f)) {
            mdiff = 100f;
        } else if ((mrange > 200f) && (mrange <= 500f)) {
            mdiff = 50f;
        } else if ((mrange > 100f) && (mrange <= 200f)) {
            mdiff = 20f;
        } else if ((mrange > 50f) && (mrange <= 100f)) {
            mdiff = 10f;
        } else if ((mrange > 20f) && (mrange <= 50f)) {
            mdiff = 5f;
        } else if ((mrange > 10f) && (mrange <= 20f)) {
            mdiff = 2f;
        } else if ((mrange > 5f) && (mrange <= 10f)) {
            mdiff = 1f;
        } else if ((mrange > 2f) && (mrange <= 5f)) {
            mdiff = 0.5f;
        } else if ((mrange > 1f) && (mrange <= 2)) {
            mdiff = 0.2f;
        } else if ((mrange > 0.5f) && (mrange <= 1)) {
            mdiff = 0.1f;
        } else if ((mrange > 0.2f) && (mrange <= 0.5f)) {
            mdiff = 0.05f;
        } else if ((mrange > 0.1f) && (mrange <= 0.2f)) {
            mdiff = 0.02f;
        } else if ((mrange > 0.05f) && (mrange <= 0.1f)) {
            mdiff = 0.01f;
        } else if ((mrange > 0.02f) && (mrange <= 0.05f)) {
            mdiff = 0.005f;
        } else if ((mrange > 0.01f) && (mrange <= 0.02f)) {
            mdiff = 0.002f;
        } else if ((mrange > 0.005f) && (mrange <= 0.01f)) {
            mdiff = 0.001f;
        } else if ((mrange > 0.002f) && (mrange <= 0.005f)) {
            mdiff = 0.0005f;
        } else if ((mrange > 0.001f) && (mrange <= 0.002f)) {
            mdiff = 0.0002f;
        } else if ((mrange > 0.0005f) && (mrange <= 0.001f)) {
            mdiff = 0.0001f;
        } else if ((mrange > 0.0002f) && (mrange <= 0.0005f)) {
            mdiff = 0.00005f;
        } else if ((mrange > 0.0001f) && (mrange <= 0.0002f)) {
            mdiff = 0.00002f;
        } else {
            mdiff = 0.00001f;
        }
        return (mrange/mdiff) + mdiff;
    }

    //Formally Request location permissions with Permisio.
    public void locPermissionRequest() {
        Permiso.getInstance().requestPermissions(new Permiso.IOnPermissionResult() {
            @Override
            public void onPermissionResult(Permiso.ResultSet resultSet) {
                Timber.d("resultSet: %s",resultSet.areAllPermissionsGranted());
                Timber.d("check loc permission: %s",checkLocPermission());
                if (resultSet.areAllPermissionsGranted()) {
                    //Notify the app that location permissions have been granted so they won't be requested again unecessairly
                    appPreferences.put("locgranted", true);
                    //Restart the Main Activity
                    Intent intent2 = new Intent(MainActivity.this, MainActivity.class);
                    intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplicationContext().startActivity(intent2);
                }
            }
            //Provide rationale for user's that initially reject granting location permissions
            @Override
            public void onRationaleRequested(Permiso.IOnRationaleProvided callback, String... permissions) {
                Permiso.getInstance().showRationaleInDialog("ACCESS_FINE_LOCATION", getString(R.string.rationale_location), null, callback);
            }
        }, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    //Build Alert Dialog to notify users why location permissions are required if they have denied all permissions.
    AlertDialog builder;
    private static final int REQUEST_APP_SETTINGS = 168;
    public AlertDialog getAlertDialog() {

        if (builder == null) {
            builder = new AlertDialog.Builder(this)
                    .setTitle("Exit uWx").create();
        }
        builder.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent myAppSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                myAppSettings.addCategory(Intent.CATEGORY_DEFAULT);
                myAppSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(myAppSettings, REQUEST_APP_SETTINGS);
            }
        });
        builder.setButton(DialogInterface.BUTTON_NEGATIVE, "EXIT", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                closeApp();
            }
        });
        builder.setMessage("This app cannot run without location permissions. If you'd like to use this app select OK to go to the android App Settings page where you can enable location permissions.");
        return builder;
    }

    public void closeApp() {
        this.finishAffinity();
    }

    @Override
    public void onResume() {
        super.onResume();

        Timber.d("OnResume");
        //Define LineChart for realtime sensor display
        mChart = (LineChart) findViewById(R.id.pres1);

        // no description text
        mChart.setDescription("");
        Paint p = mChart.getPaint(Chart.PAINT_INFO);
        p.setColor(Color.BLACK);
        mChart.setNoDataText("Chart data is not available.");
        mChart.setNoDataTextDescription("Your phone does not have a barometer.");

        if (pSensor != null) {

            //Register sensor listener.
            mSensorManager.registerListener(this, pSensor, SensorManager.SENSOR_DELAY_GAME);

            // enable touch gestures
            mChart.setTouchEnabled(true);

            // enable scaling and dragging
            mChart.setDragEnabled(true);
            mChart.setScaleEnabled(true);
            mChart.setDrawGridBackground(false);

            // if disabled, scaling can be done on x- and y-axis separately
            mChart.setPinchZoom(true);
            // set an alternative background color
            mChart.setBackgroundColor(Color.WHITE); //Color.argb(35, 211, 211, 211));
            LineData data = new LineData();
            data.setValueTextColor(Color.BLACK);
            mChart.setHighlightPerTapEnabled(false);
            mChart.setHighlightPerDragEnabled(false);
            // add empty data
            mChart.setData(data);

            // get the legend (only possible after setting data)
            Legend l = mChart.getLegend();

            // modify the legend ...
            l.setForm(Legend.LegendForm.CIRCLE);
            l.setTextColor(Color.BLACK);
            l.setEnabled(false);

            //Set X-Axis parameters
            XAxis xl = mChart.getXAxis();
            xl.setTextColor(Color.BLACK);
            xl.setDrawGridLines(false);
            xl.setAvoidFirstLastClipping(false);
            xl.setSpaceBetweenLabels(2);
            xl.setAxisLineWidth(2);
            xl.setEnabled(false);

            //Set Y-Axis parameters
            lAxis = mChart.getAxisLeft();
            lAxis.setTextColor(Color.BLACK);
            lAxis.setDrawGridLines(true);
            lAxis.setAxisLineWidth(2);
            lAxis.setGridLineWidth(2);
            lAxis.setTextSize(getResources().getInteger(R.integer.ltextsize));
            //Add more labels if phone is in portrait configuration (longer y-axis).
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                labelnum=6;
            } else {
                labelnum=11;
            }
            lAxis.setLabelCount(labelnum,true);

            //Adjust scaling
            if (pscale.equals("psi") || pscale.equals("inHg") || pscale.equals("kPa")) {
                lAxis.setValueFormatter(new MyYAxisValueFormatter3());
            } else {
                lAxis.setValueFormatter(new MyYAxisValueFormatter());
            }
            //Disable right axis.
            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setEnabled(false);
        }
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

    @Override
    public void onPause() {
        Timber.d("onPause");
        super.onPause();
        if (pSensor != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onDestroy() {
        Timber.d("onDestroy");
        super.onDestroy();
        if (pSensor != null) {
            mSensorManager.unregisterListener(this);
        }
    }
}
