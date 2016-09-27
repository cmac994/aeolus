package com.example.cmac.aeolus;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.Builder;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.grandcentrix.tray.AppPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import timber.log.Timber;

//Separate class to trigger significant motion sensor
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class TriggerListener extends TriggerEventListener {
    private AppPreferences appPreferences;
    TriggerListener(Context context) {
        appPreferences = new AppPreferences(context.getApplicationContext()); // this Preference comes for free from the library
        appPreferences.put("sigMotionDetected", false);
    }

    //Detect significant motion events
    @Override
    public void onTrigger(TriggerEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (event.values[0] == 1) {
                appPreferences.put("sigMotionDetected", true);
            }
        }
        // Sensor is auto disabled.
    }
}
//Pressure and Pressure Change Collection Class
public class PressureCollectionService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener, android.location.LocationListener, SensorEventListener {
    //Define essential variables for service
    public static final String TAG = "PressureService";
    private SensorManager mSensorManager = null;
    private WakeLock mWakeLock = null;
    long epochTime, millTime;
    int cnt = 0;
    Boolean Stationary = false;
    double ci_stationary;
    double ci_moved;
    double battery_pct;
    float latitude, longitude, press_diff, press_avg, accuracy, speed, gpsStartTime, startTime, startMeasure;
    float last_latitude = 0.0f, last_longitude = 0.0f, last_accuracy = 15.0f, dist;
    float last_tchange = 0;
    float total_time = 0;
    PendingIntent pendingIntent;
    boolean hasPlay = true;
    boolean isCharging = false;
    boolean acCharge = false;
    boolean usbCharge = false;
    boolean uploadData = true;
    boolean sigMotion = false;
    boolean stopped = false;
    boolean loc_called = false;
    boolean sigMotionDetected = false;
    boolean service_running;

    AppPreferences appPreferences;

    WifiManager.WifiLock wifiLock;

    //Initialize array lists for dP compute database
    ArrayList<Float> presslist = new ArrayList<>();
    ArrayList<Float> latlist = new ArrayList<>();
    ArrayList<Float> gpsacclist = new ArrayList<>();
    ArrayList<Float> lnglist = new ArrayList<>();
    ArrayList<Float> plist = new ArrayList<>();
    ArrayList<Integer> movelist = new ArrayList<>();

    //Initialize array lists for Location estimation
    ArrayList<Float> speed_list = new ArrayList<>();
    ArrayList<Float> latitude_list = new ArrayList<>();
    ArrayList<Float> longitude_list = new ArrayList<>();
    ArrayList<Float> accuracy_list = new ArrayList<>();

    //Define trigger event listener to detect significant motion (if sensor is available)
    TriggerEventListener triggerListener;
    ArrayList<Long> timlist = new ArrayList<>();
    float dp15min, dp30min, dp1hr, dp3hr, dp6hr, dp12hr;
    GoogleApiClient mGoogleApiClient;
    LocationManager locManager;
    LocationRequest mLocationRequest;
    Handler handler_orig = new Handler();
    private boolean shouldContinue = true;
    //Default observation frequency set to 15 min.
    int obfreq = 900;

    //Register Listener for Sensor Events from the Pressure and Significant Motion sensor.
    private void registerListener() {
        //Register SIG Motion sensor if available
        if (VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            if (sigMotion) {
                mSensorManager.registerListener(this,
                        mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION),
                        SensorManager.SENSOR_DELAY_NORMAL);
                Timber.d("SIG Motion registered");
                Timber.d("request trigger");
                mSensorManager.requestTriggerSensor(triggerListener, mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION));
            }
        }

        //Register Pressure sensor (GAME Delay ~60Hz, UI Delay ~20HZ)
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_GAME);
    }

    //Unregister Listener for Sensor Events
    private void unregisterListener() {
        mSensorManager.unregisterListener(this);
        if (VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            if (sigMotion) {
                mSensorManager.cancelTriggerSensor(triggerListener, mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION));
            }
        }
    }

    //Runnable to initiate extend sensor data collection for a few seconds to account for sensor delay at moderate-high speeds.
    private Runnable late_runnable = new Runnable() {
        @Override
        public void run() {
            computeDP();
        }
    };

    //Runnable to initialize location retrieval
    private Runnable base_runnable = new Runnable() {
        @Override
        public void run() {
            if (shouldContinue) {
                //Start Location updates
                Timber.d("start loc");
                startLocationUpdates();
            }
        }
    };

    //Loc Runnable runs after 45s as a backup to ensure location updates stop if they took too long.
    private Runnable loc_runnable = new Runnable() {
        @Override
        public void run() {
            Timber.d("run loc runnable");
            //Automatically stop location updates if location updates took longer than 45s.
            if (!stopped) {
                if (cnt == 0) {
                    //No location data retrieved
                    endService();
                } else {
                    //Get optimum location estimate by choosing the location retrieved with the greatest accuracy.
                    int best = 0;
                    for (int i = 0; i < cnt; i++) {
                        if ((accuracy_list.get(i)).equals(Collections.min(accuracy_list))) {
                            best = i;
                        }
                    }
                    latitude = latitude_list.get(best);
                    longitude = longitude_list.get(best);
                    accuracy = accuracy_list.get(best);
                    speed = Collections.max(speed_list);
                    //Stop Location Updates
                    stopLocationUpdates();
                }
            }
        }
    };

    //Define mandatory onAccuracyChanged function;
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Timber.d("onAccuracyChanged().");
    }

    //End service with call to stopSelf();
    public void endService() {stopSelf();}

    //Define function to calculate the average value from a float array list
    private float calculateAverage(List<Float> marks) {
        Float sum = 0.0f;
        if (!marks.isEmpty()) {
            for (Float mark : marks) {
                sum += mark;
            }
            return sum / marks.size();
        }
        return sum;
    }

    /*Define On sensorChanged function to log sensor measurements from the Pressure
    sensor and Sig Motion Sensor while the GPS location is being locked*/
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            //Read sensor data into arrays on a case by case basis (i.e. determine which sensor each event corresponsds to)
            case (Sensor.TYPE_PRESSURE):
                float press = event.values[0]; //Retrieve the pressure
                if (press > 500.0f && press < 1100.0f) {
                    presslist.add(press);
                }
                break;
        }
    }

    //Compute the distance between two points on the Earth
    public double haversine(Float lat1, Float lon1, Float lat2, Float lon2) {
        float R = 6371000.0f; // Radius of the earth in km
        Double dLat = Math.toRadians(lat2-lat1);  // deg2rad below
        Double dLon = Math.toRadians(lon2-lon1);
        Double a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon/2) * Math.sin(dLon/2);
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c; // Distance in meters
    }

    /*Define a function to evaluate the likelihood of phone movement.
     If a phone has not moved over a period of time a pressure change estimate can be made.
      */
    private void determine_ci() {
        /*Set count and float values
        Main idea is to estimate probability by defining 3 confidence levels
        hci = high confidence, mci = moderate confidence, lci = low confidence
        combining degrees of confidence numerically produces a probability estimate that when normalized ranges from 0-1.
         */
        int hci_cntS = 0; int mci_cntS = 0; int lci_cntS = 0;
        int hci_cntNS = 0; int mci_cntNS = 0; int lci_cntNS = 0;
        float hci = 0.68f; float mci = 0.34f; float lci = 0.17f;

        //Handle location estimate
        if (dist < 30) {
            hci_cntS += 2; //Phone stationarity is very likely
        } else if ((dist >= 30) && (dist < 60)) {
            hci_cntS += 1; //Phone stationarity is likely
        } else {
            hci_cntNS += 2; //Phone is unlikely to be stationary.
        }

        //Handle GPS Speed / Movement
        if (speed >= 3.5f && speed != 0.0f) {
            //Phone is moving at speed > 3 m/s and likely not stationary.
            hci_cntNS += 1;
            //Phone is moving at vehicular speeds it is very likely the phone is not stationary.
            if (speed >= 8.0f) {
                hci_cntNS += 2;
            }
        }

        //Handle significant motion events
        if (sigMotion) {
            //If significant motion detected may not be stationary
            if (sigMotionDetected) {
                mci_cntNS += 1;
            } else {
                lci_cntS += 1;
            }
        }

        //Handle Charging Status
        if (isCharging) {
            //If phone is charging (ac charger probably stationary, USB charger maybe stationary).
            if (acCharge) {
                hci_cntS += 2;
            } else if (usbCharge) {
                mci_cntS += 1;
            }
        }
        //Estimate a normalized measure of probability by aggregating statistics by confidence level.

        //Compute Stationary Confidence
        double Ts1 = Math.pow(hci,1.0/hci_cntS);
        double Ts2 = (Math.pow(mci,1.0/mci_cntS))*(1.0 - Ts1);
        double Ts3 = (Math.pow(lci,1.0/lci_cntS))*(1.0 - (Ts2+Ts1));
        ci_stationary = 100.0f*(Ts1 + Ts2 + Ts3);

        //Compute Non-Stationary Confidence
        double Tns1 = Math.pow(hci,1.0/hci_cntNS);
        double Tns2 = (Math.pow(mci,1.0/mci_cntNS))*(1.0 - Tns1);
        double Tns3 = (Math.pow(lci,1.0/lci_cntNS))*(1.0 - (Tns2+Tns1));
        ci_moved = 100.0f*(Tns1 + Tns2 + Tns3);

        /*Note that this algorithm is completely subjective and entirely arbitrary. The algorithm helps prevent
        pressure change estimation when phones have moved by leveraging information from a variety of sensors and recievers in an attempt to
        better estimate phone movement. This algorithm could likely be improved
        by incorporating more sensor available on the latest devices.
         */
    }

    //Function to collect and send sensor measurements to the DB
    public void computeDP() {
        Timber.d("Storing and Processing Sensor Data");
        //Get Current Time in (UTC)
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        //Get Current time in seconds since the start of the Unix Epoch
        millTime = System.currentTimeMillis();
        epochTime = System.currentTimeMillis() / 1000L;

        //Stop Sensor Measurements
        unregisterListener();

        //Retrieve last known pressure observation from temporary storage
        float lastpavg = appPreferences.getFloat("lastpavg",0.0f);
        Timber.d("pressure list size: %d",presslist.size());
        //Analyze the last ~50 pressure measurements (sensor takes time to respond to changes in pressure when first booted up - Hysteresis)
        if (presslist.size() > 51) {
            //Take the last ~50 measurements from the sensor
            List<Float> presslistLast = presslist.subList(presslist.size() - 51, presslist.size() - 1);
            //Compute the average pressure from these measurements
            press_avg = calculateAverage(presslistLast);
            //Get pressure change since last observation
            float temp_pchange = Math.abs(press_avg - lastpavg);
            //If the average pressure or pressure change is extreme / unphysical take the maximum of the last ~50 pressures
            if ((lastpavg != 0.0f) && (temp_pchange >= 200.0f)) {
                press_avg = Collections.max(presslistLast);
                //If issues still exist do not save data
                if (Math.abs(press_avg-lastpavg) >= 200.0f) {
                    uploadData = false;
                }
            }
        } else {
            //Insufficient pressure data to extract a quality observation, pass.
            uploadData=false;
        }

        Timber.d("Upload Data: %s",uploadData);
        //If data is of sufficient quality store/log data.
        if (uploadData) {

            //Determine if significant motion was detected
            sigMotionDetected = appPreferences.getBoolean("sigMotionDetected", false);

            //Set latest GPS lat,lng, and accuracy in addition to latest pressure (these will be used the next time the service runs)
            appPreferences.put("LatitudeLocal", latitude);
            appPreferences.put("LongitudeLocal", longitude);
            appPreferences.put("AccuracyLocal", accuracy);
            appPreferences.put("lastpavg", press_avg);

            //Determine CI for the first time.
            determine_ci();
            //Estimate stationarity from the previously described confidence estimation algorithm.
            if (ci_stationary >= ci_moved) {
                Stationary = Boolean.TRUE;
            } else {
                Stationary = Boolean.FALSE;
            }

            //Open the pressure storage database
            SQLiteDatabase db = openOrCreateDatabase("pressuredataDB", Context.MODE_PRIVATE, null);
            //Create TABLE to store lat,lng,time difference, and pressure change values over past 3-days
            db.execSQL("CREATE TABLE IF NOT EXISTS ptable(lat FLOAT(10,5), lng FLOAT(10,5), epochtime BIGINT, INTEGER Stationary, pavg FLOAT(8,3), gpsaccuracy FLOAT(8,3));");
            //Append the latest data to the table.
            int st;
            if (Stationary) {
                st = 1;
            } else {
                st = 0;
            }
            db.execSQL("INSERT INTO ptable VALUES('" + latitude + "','" + longitude + "','" + epochTime + "','" + st + "','" + press_avg + "','" + accuracy + "');");
            //Delete the oldest data in the table (any data older than 3-days)
            db.execSQL("DELETE FROM ptable WHERE epochtime < " + Long.toString(epochTime - 3 * 86400));
            //Retrieve values from table for pressure change estimation.
            Cursor ctmp = db.rawQuery("SELECT * FROM ptable", null);
            //Extract key variables from the archived table.
            while (ctmp.moveToNext()) {
                latlist.add(ctmp.getFloat(0));
                lnglist.add(ctmp.getFloat(1));
                timlist.add(ctmp.getLong(2));
                movelist.add(ctmp.getInt(3));
                plist.add(ctmp.getFloat(4));
                gpsacclist.add(ctmp.getFloat(5));
            }
            ctmp.close();
            db.close();

            //Notify the app that the pressure table now exists and data from it can be plotted.
            appPreferences.put("ptableExists",true);

            //Initialize lists of time intervals and pressure changes
            ArrayList<Long> tchange = new ArrayList<>();
            ArrayList<Float> dp = new ArrayList<>();
            //List of pressure change intervals (these are designed to match up with the measurement frequency - default 15min)
            ArrayList<Integer> timslist = new ArrayList<>();
            timslist.add(900); //15min
            timslist.add(1800); //30min
            timslist.add(3600); //1hr
            timslist.add(10800); //3hr
            timslist.add(21600); //6hr
            timslist.add(43200); //12hr
            int lastdbidx = latlist.size() - 1;
            /*Loop through the database and compute the distance between the current location (last row in DB)
            and the location of all measurements in ptable*/
            for (int a = 0; a < lastdbidx; a++) {
                //Compute the distance between the latest observation location and all previous observation locations.
                double distbetween = haversine(latlist.get(lastdbidx), lnglist.get(lastdbidx), latlist.get(a), lnglist.get(a));
                Timber.d("Dist between: %.2f",distbetween);
                float distmax, gpsmax;
                //Place a limit on the distance a phone has moved when estimating pressure change to avoid poor quality observations
                gpsmax = 30.0f;
                distmax = 2*gpsmax;
                //If the phone hasn't moved much in the horizontal and vertical and if it was determined to be stationary and its GPS accuracy was sufficient then compute pressure change if time difference is suitable
                if (((distbetween < distmax) && (gpsacclist.get(a) <= gpsmax) && (movelist.get(a) == 1))) {
                    Timber.d("a: %d, tdiff: %d, pdiff: %f", a, (epochTime - timlist.get(a)), (press_avg - plist.get(a)));
                    //Compute the time difference between observations
                    tchange.add(epochTime - timlist.get(a));
                    //compute the pressure tendency over the time period in which the two measurements were taken
                    dp.add(press_avg - plist.get(a));
                } else {
                    //Null value of -100 indicates pressure change coould not be computed
                    tchange.add(0L);
                    dp.add(-100.0f);
                }
            }

            //Initialize pressure change estimates with null value of -100;
            dp15min = -100;
            dp30min = -100;
            dp1hr = -100;
            dp3hr = -100;
            dp6hr = -100;
            dp12hr = -100;
            //Loop through the list pressure change estimates
            for (int t = 0; t < dp.size(); t++) {
                //Loop through the list of desired pressure change time-intervals.
                for (int i = 0; i < timslist.size(); i++) {
                    //If dP is <= 1hr allow observations within 5 minutes of acceptable window (to account for variability of Pending Intent)
                    if ((i < 3) && (tchange.get(t) >= timslist.get(i) - 300) && (tchange.get(t) <= timslist.get(i) + 300)) {
                        if (i == 0) {
                            dp15min = dp.get(t);
                        } else if (i == 1) {
                            dp30min = dp.get(t);
                        } else if (i == 2) {
                            dp1hr = dp.get(t);
                        }
                    }
                    //If dP is > 1hr allow observations within 15 minutes of acceptable window (to account for variablity of Pending Intent)
                    else if ((i >= 3) && (tchange.get(t) > timslist.get(i) - 900) && (tchange.get(t) < timslist.get(i) + 900)) {
                        if (i == 3) {
                            dp3hr = dp.get(t);
                        } else if (i == 4) {
                            dp6hr = dp.get(t);
                        } else if (i == 5) {
                            dp12hr = dp.get(t);
                        }
                    }
                }
            }

            //Reopen the pressure storage database
            SQLiteDatabase db2 = openOrCreateDatabase("pressuredataDB", Context.MODE_PRIVATE, null);
            //Create TABLE to store lat,lng,time difference, and pressure change values
            db2.execSQL("CREATE TABLE IF NOT EXISTS dptable(lat FLOAT(10,5), lng FLOAT(10,5), epochtime BIGINT, dp15min FLOAT(8,3), dp30min FLOAT(8,3), dp1hr FLOAT(8,3), dp3hr FLOAT(8,3), dp6hr FLOAT(8,3), dp12hr FLOAT(8,3));");
            db2.execSQL("INSERT INTO dptable VALUES('" + latitude + "','" + longitude + "','" + epochTime + "','" + dp15min + "','" + dp30min + "','" + dp1hr + "','" + dp3hr + "','" + dp6hr + "','" + dp12hr + "');");
            //Delete the oldest data in the table (any data older than 3-days)
            db2.execSQL("DELETE FROM dptable WHERE epochtime < " + Long.toString(epochTime - 3 * 86400));
            db2.close();
            //Notify the app that the pressure change table now exists and data from it can be logged.
            appPreferences.put("dptableExists",true);
        }
        endService();
    }

    //When GooglePlayLocation API connects initiate handler_orig which delays the onset of location updates
    public void onConnected(Bundle connectionHint) {
        Timber.d("OnConnected");
        if (checkLocPermission()) {
            //Listen to the sensor for a little while before querying the GPS and receiving location updates.
            handler_orig.postDelayed(base_runnable, 15000);
        } else {
            //Don't have appropriate permissions end service
            endService();
        }
    }

    //Build GoogleAPI Client for location retrieval
    protected synchronized void buildGoogleApiClient() {
        Timber.d("Build Google Api Client");
        mGoogleApiClient = new Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    //Specific location request settings/criteria
    protected void createLocationRequest() {
        Timber.d("Create Location Request: %f",startTime);
        mLocationRequest = new LocationRequest();
        //Set frequency and priority of location updates
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(3000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        //Set maximum number of updates to 3 (to limit battery usage).
        mLocationRequest.setNumUpdates(3);
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

    //Start Location updates
    protected void startLocationUpdates() {
        if (checkLocPermission()) {
            Timber.d("Start Location updates");
            loc_called = true;
            /*If location updates takes to long (> 35s after start of location query, 45s after start of service)
            then forcibly stop location updates in loc_runnable.*/
            handler_orig.postDelayed(loc_runnable, 35000);
            /*IMPORTANT: if possible use both the FusedLocationAPI and the Android Manager to Retrieve location updates.
            By using both providers improvements in gps accuracy can be obtained and deficiencies in either provider can be offset.
             */
            if (hasPlay) {
                //Formally request location updates from Google Play API
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
                //Request location updates from Android manager
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, this);
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, this);
            } else {
                //Request location updates from Android manager
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, this);
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, this);
            }
        } else {
            //Location permissions insufficient end service.
            Timber.d("Location Permissions insufficient");
            endService();
        }
    }

    //Stop Location Updates
    protected void stopLocationUpdates() {
        Timber.d("location updates stopped");
        stopped = true;
        //Formally stop location updates with request to GoogleAPI Client
        if (hasPlay) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
        //Remove GPS status listener
        if (checkLocPermission()) {
            locManager.removeUpdates(this);
        }

        /*If speed is non negligible allow sensor to run for a few more seconds
        (to account for the hysteresis of the pressure sensor which is more pronounced when a measurement is taken at speed)
         */
        if ((speed >= 5) && (speed < 10)) {
            //Set delay of 3s for moderate speeds (Bike -- Residential Street, City Driving)
            handler_orig.postDelayed(late_runnable,3000);
        } else if (speed > 10) {
            //Set delay of 5s at high speeds (CAR -- Boulevard, Highway, Rural Driving)
            handler_orig.postDelayed(late_runnable,5000);
        } else {
            //Analyze Sensor Time Series and initiate data Upload.
            computeDP();
        }
    }

    //Round value to 6th decimal place.
    public float roundloc(double v) {
        return (float) (Math.floor((v * 1000000.0) + 0.5d) / 1000000.0);
    }

    //Detect Location Change.
    public void onLocationChanged(Location location) {
        //Count Location Updates (not to exceed 2).
        cnt = cnt + 1;
        //Append GPS data to array lists (so best location data can be retrieved from series of GPS measurements)(
        latitude_list.add(roundloc(location.getLatitude()));
        longitude_list.add(roundloc(location.getLongitude()));
        speed_list.add(location.getSpeed());
        accuracy_list.add(location.getAccuracy());
        //Record the time of the first location estimate
        if (cnt == 1) {
            gpsStartTime = SystemClock.elapsedRealtime()/1000L;
        }

        //Compute the distance between the phone's current and previous locations using the haversine formula
        dist = (float) haversine(last_latitude, last_longitude, (float) location.getLatitude(), (float) location.getLongitude());
        //Estimate the time between GPS updates.
        if (last_tchange == 0) {
            last_tchange = (SystemClock.elapsedRealtime() - startTime)/1000L;
        } else {
            last_tchange = (SystemClock.elapsedRealtime()/1000L - last_tchange);
        }
        total_time = (SystemClock.elapsedRealtime()/1000L) - gpsStartTime;
        /* When to stop Location Estimation -- THIS IS IMPORTANT to ensure a balance between battery life and observation quality
          to Improve GPS efficiency only a single measurement is retrieved if the device hasn't moved (< 30m) and the GPS fix is accurate (< 30m).
          If two GPS locations have been retrieved and more than 12 seconds has passed end location retrieval to limit power consumption.
          For the first measurement since no previous location is known, take at least two GPS measurements to ensure a good initial location estimate.
         */
        if ( ((last_latitude == 0.0f) && (last_longitude == 0.0f) && (last_accuracy == 0.0f) && (cnt > 1)) || ((total_time > 12) && (cnt > 1)) || ((dist < 30.0) && (accuracy < 30.0))) {
            //Retrieve the best location estimate by extracting GPS data at the index for which the best GPS accuracy was achieved.
            int best = 0;
            for (int i=0; i < cnt; i++) {
                if ((accuracy_list.get(i)).equals(Collections.min(accuracy_list))) {
                    best = i;
                }
            }
            //Retrieve the most optimum location data.
            latitude = latitude_list.get(best);
            longitude = longitude_list.get(best);
            accuracy = accuracy_list.get(best);
            speed = Collections.max(speed_list);
            //Stop Location updates
            stopLocationUpdates();
        }
    }

    //Define standard calls for android location manager.
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
    @Override
    public void onProviderEnabled(String provider) {
    }
    @Override
    public void onProviderDisabled(String provider) {
    }

    //Handle Mandatory Google API Client Function Calls
    public void onConnectionSuspended(int i) {
        Timber.d("Connection Suspended");
        mGoogleApiClient.connect();
    }

    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Timber.d("Connection Failed");
        endService();
    }

    //Get Status of Battery to help modify observation frequency to conserve power and to aid in determining phone movement.
    public void getBatteryStatus() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent == null) {
            usbCharge = Boolean.FALSE;
            acCharge = Boolean.FALSE;
            isCharging = Boolean.FALSE;
        } else {
            //Get Battery status (Plugged in or not)
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int chargePlug = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            //Compute percentage of battery life that remains.
            battery_pct =  ((float)level / (float)scale) * 100.0f;

            // Determine charging status. (Battery being charged? via USB or AC?)
            if (status == -1 || chargePlug == -1) {
                usbCharge = Boolean.FALSE;
                acCharge = Boolean.FALSE;
                isCharging = Boolean.FALSE;
            } else {
                usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Set start time of the service.
        startTime = SystemClock.elapsedRealtime();
        //Reset alarm if calibrated.
        Timber.d("Service started");
        appPreferences = new AppPreferences(getApplicationContext()); // this Preference comes for free from the library
        //Get the start time of the service in epoch time.
        long service_start_time = System.currentTimeMillis() / 1000L;
        //Save the service start time (in sec since start of unix epoch).
        appPreferences.put("last_service_time", service_start_time);

        //Retrieve the last GPS location, longitude, and accuracy.
        last_latitude = appPreferences.getFloat("LatitudeLocal",0.0f);
        last_longitude = appPreferences.getFloat("LongitudeLocal",0.0f);
        last_accuracy = appPreferences.getFloat("AccuracyLocal",0.0f);

        //Determine if the service is already running
        service_running = appPreferences.getBoolean("ServiceOn", false);

        //get default submission frequency from pref
        obfreq = appPreferences.getInt("obfreq",900);
        Timber.d("obfreq: %s",obfreq);

        //Retrieve the Wake Lock object and from the power manager
        PowerManager manager =
                (PowerManager) getSystemService(Context.POWER_SERVICE);

        //If the service is already running let the service already in progress complete
        if (service_running) {
            Timber.d("Service is already Running Stop service");
            uploadData = false;
        } else {
            //Note that the service is running (to prevent multiple instances)
            appPreferences.put("ServiceOn", true);
        }

        //Get Battery Status info
        getBatteryStatus();

        if (uploadData) {
            //Get the sensor Manger from the Sensor Service
            Timber.d("Get Sensor Manager from Sensor Service");
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) != null) {
                sigMotion = true;
            }
            if (sigMotion) {
                triggerListener = new TriggerListener(this);
            }
            //Set partial wakelock
            mWakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            //Wifi Lock (may help Google Play API Location services get GPS fix quicker by triggering WiFi when asleep).
            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifiLock= wm.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);

            //Determine GoogleAPIAvailablity (requires Google Play Services)
            GoogleApiAvailability api = GoogleApiAvailability.getInstance();
            int resp = api.isGooglePlayServicesAvailable(this);
            locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (resp == ConnectionResult.SUCCESS) {
                buildGoogleApiClient();
                hasPlay = true;
            } else {
                //Google Play API inaccessible use android location manager instead
                hasPlay = false;
                //Define the location manager and network manager.
                //locManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            }
            Timber.d("Check the Network Connection");
        } else {
            endService();
        }
    }

    //Release the Listeners and wakelock on destory
    @Override
    public void onDestroy() {
        super.onDestroy();
        //Note the endTime of the service
        appPreferences.put("endTime", System.currentTimeMillis() / 1000L);
        //Note that the Service is no longer on.
        appPreferences.put("ServiceOn", false);
        shouldContinue = false;
        //Remove handler call backs (if handler was called)
        handler_orig.removeCallbacks(base_runnable);
        if (loc_called) {
            handler_orig.removeCallbacks(loc_runnable);
        }
        //Remove sensor listeners
        Timber.d("OnDestroy Called");
        if (mSensorManager != null) {
            //unregister receivers
            unregisterListener();
        }
        //release WifiLock
        if ((wifiLock != null) && (wifiLock.isHeld())) {
            try {
                wifiLock.release();
            } catch (Exception we) {
                we.printStackTrace();
            }
        }
        //Release wakeLock
        if ((mWakeLock != null) && (mWakeLock.isHeld())) {
            mWakeLock.release();
            //Convert foreground service to background service
            stopForeground(true);
        }

        /*If battery level drops below 25% and submission frequency is less than or equal to 15 min
        begin to reduce the submission frequency*/
        if ((battery_pct <= 25.0) && (battery_pct > 15)) {
            if (obfreq <= 900) {
                //Cancel current alarm
                cancelAlarm();
                //Reset alarm
                startRepeatAlarm(2 * obfreq * 1000);
            }
        }
        //If battery level drops below 15% (Low battery warning issued) reduce submission frequency for all phones.
        else if (battery_pct <= 15.0) {
            //Cancel current alarm
            cancelAlarm();
            //Reset alarm
            startRepeatAlarm(2 * obfreq * 1000);
        }
    }

    //Initialize a reepeating pressure collection alarm.
    public void startRepeatAlarm(int interval) {
        //Set inexact repeating alarm to run at the specified interval.
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, alarmIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + interval, interval, pendingIntent);
    }

    //Cancel existing pressure collection alarm.
    public void cancelAlarm() {
        //Get Alarm intent / pending intent.
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        //Cancel alarm and pending intent
        am.cancel(pendingIntent);
        if (pendingIntent != null) {
            pendingIntent.cancel();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    //On Start (which is after onCreate)
    @Override
    public int onStartCommand(Intent intent,int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (uploadData) {
            String s1 = "Retreving pressure measurements from barometer";
            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("Crowdsourcing Pressure")
                    .setContentText(s1)
                    .setOngoing(true).build();
            //Initialize foreground notification
            startForeground(1137, notification);
            //Connect to GoogleApiClient
            if (hasPlay) {
                try {
                    mGoogleApiClient.connect();
                } catch (NullPointerException ne) {
                    hasPlay = false;
                }
            }
            //Acquire wakelock
            mWakeLock.acquire();
            //Acquire wifiLock
            try {
                wifiLock.acquire();
            } catch (Exception we) {
                we.printStackTrace();
            }
            Timber.d("Wakelock aquired");
            //Register Sensor Listeners
            registerListener();
            //Get measurement start time.
            startMeasure = SystemClock.elapsedRealtime();
        } else {
            endService();
        }
        return START_NOT_STICKY;
    }
}