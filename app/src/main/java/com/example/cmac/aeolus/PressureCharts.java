package com.example.cmac.aeolus;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;
import net.grandcentrix.tray.AppPreferences;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import timber.log.Timber;

//Class displays time series of pressure and pressure change measurements
public class PressureCharts extends AppCompatActivity  {

    //Define ArrayList variables to be plotted.
    ArrayList<Float> smart_dp = new ArrayList<>();
    ArrayList<Float> smart_pres = new ArrayList<>();

    //Define time arrays for x-axis labels.
    ArrayList<Long> smart_times = new ArrayList<>();
    ArrayList<String> local_pres_times = new ArrayList<>();

    private CombinedChart fig1;
    String pscale; int obfreq;
    SensorManager mSensorManager;
    Sensor mSensor = null;
    AppPreferences appPreferences;
    Button button1, button2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Initialize sensor manager and determine availabity of pressure sensor
        mSensorManager = (SensorManager) this.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        for (Sensor sensor : mSensorManager.getSensorList(Sensor.TYPE_PRESSURE)) {
            if (sensor.getType() == Sensor.TYPE_PRESSURE) {
                mSensor = sensor;
            }
        }

        // if no barometer is present display sadcloud image.
        if (mSensor == null) {
            setContentView(R.layout.error_message);
        } else {
            //Initialize layout and define charview buttons
            setContentView(R.layout.activity_pcharts);
            button1 = (Button) findViewById(R.id.latestpressure);
            button2 = (Button) findViewById(R.id.allpressure);
            // Setup toolbar to replace the action bar.
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            //Disable toolbar title in place of our own (defined in xml layout file)
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            toolbar.setTitle("");
            toolbar.setSubtitle("");
        }
    }

    //Set y-axis label precision to one decimal place.
    public class MyYAxisValueFormatter implements YAxisValueFormatter {
        private DecimalFormat mFormat;
        public MyYAxisValueFormatter () {
            mFormat = new DecimalFormat("###,###,##0.0"); // use one decimal
        }
        @Override
        public String getFormattedValue(float value, YAxis yAxis) {
            // write your logic here
            // access the YAxis object to get more information
            return mFormat.format(value); // e.g. append a dollar-sign
        }
    }

    //Define round functions to make y-axis limits more amenable.
    public float ceilToX(float val, float X) {
        return (float) Math.ceil(val*X)/X;
    }
    public float floorToX(float val, float X) {
        return (float) Math.floor(val*X)/X;
    }

    //Define function to determine the tick interval and tick count on the y-axis.
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
        return mdiff;
    }


    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume");
        if (mSensor != null) {
            //Define shared preferences and get current location info
            appPreferences = new AppPreferences(this.getApplicationContext());
            pscale = appPreferences.getString("pscale", "hPa");

            //Clear global array lists
            smart_pres.clear(); smart_dp.clear();
            smart_times.clear(); local_pres_times.clear();

            //Determine if tables exists
            Boolean ptableExists = appPreferences.getBoolean("ptableExists",false);

            //Set chart titles
            TextView ch1 = (TextView) findViewById(R.id.pres1Title);
            ch1.setText(getString(R.string.pres1_title, pscale));

            if (ptableExists) {
                //Open database
                SQLiteDatabase db2 = openOrCreateDatabase("pressuredataDB", Context.MODE_PRIVATE, null);
                Cursor c02;
                c02 = db2.rawQuery("SELECT * FROM ptable", null);
                if (c02.getCount() == 0) {
                    Timber.d("No records found");
                }
                while (c02.moveToNext()) {
                    //Extract pressure and ob time from database
                    smart_times.add(c02.getLong(2));
                    float pres = c02.getFloat(4);
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
                    smart_pres.add(pres);
                }
                c02.close();
                db2.close();
            }

            //Format dates for readability.
            SimpleDateFormat sdf2 = new SimpleDateFormat("EEE, h:mm a", Locale.US);

            //Compute pressure change.
            for (int i = 0; i < smart_pres.size(); i++) {
                if (i == 0) {
                    smart_dp.add(0.0f);
                } else {
                    smart_dp.add(smart_pres.get(i)-smart_pres.get(i-1));
                }
                //Define time vector for first chart (pressure)
                local_pres_times.add(sdf2.format(new Date(smart_times.get(i) * 1000L)));
            }

            //Initialize first chart. Displays pressure and pressure change time series
            fig1 = (CombinedChart) findViewById(R.id.pres1);
            fig1.setDescription("");
            fig1.setNoDataTextDescription("Smartphone Pressures Have Not Yet Been Logged");
            // enable touch gestures
            fig1.setDrawGridBackground(false);
            // if disabled, scaling can be done on x- and y-axis separately
            // set an alternative background color
            fig1.setBackgroundColor(Color.WHITE);
            fig1.setDrawBarShadow(false);
            fig1.setHighlightPerDragEnabled(false);
            fig1.setHighlightPerTapEnabled(false);
            fig1.animateX(1500);
            ArrayList<Integer> m1;
            ArrayList<Float> m1s;
            ArrayList<Integer> m11;
            ArrayList<Float> m11s;

            //Initialize floats and label num
            float maxpres0=0,maxpres=0,minpres0=0,minpres=0,mrange0=0,mrange=0,mdiff0=0,mdiff=0;
            float maxdp0=0,maxdp=0,mindp0=0,mindp=0,mrangedp0=0,mrangedp=0,mdiffdp0=0,mdiffdp=0;
            int labelnum=5;
            int labelnumdp=5;
            if (smart_pres.size() > 0) {
                //Get initial limits of data.
                maxpres0 = Collections.max(smart_pres);
                minpres0 = Collections.min(smart_pres);
                mrange0 = Math.abs(maxpres0-minpres0);
                if (mrange0 == 0) {
                    maxpres0 = Math.round(maxpres0+1);
                    minpres0 = Math.round(minpres0-1);
                    mrange0 = Math.abs(maxpres0-minpres0);
                }
                mdiff0 = setLabels(mrange0);
                Timber.d("mrange: %.2f",mrange0);
                Timber.d("mdiff: %.2f",mdiff0);
                //Extends limits to prevent data from being cutoff.
                maxpres= maxpres0 + mdiff0;
                minpres = minpres0 - mdiff0;
                //Redefine limits of data
                mrange = Math.abs(maxpres-minpres);
                mdiff = setLabels(mrange);
                Timber.d("mrange: %.2f",mrange);
                Timber.d("mdiff: %.2f",mdiff);

                //Smooth limits.
                maxpres = ceilToX(maxpres,1.0f/mdiff);
                minpres = floorToX(minpres,1.0f/mdiff);
                Timber.d("maxpres: %.2f",minpres);
                Timber.d("minpres: %.2f",maxpres);
                //Determine an appropriate number of y-tick labels
                labelnum = Math.round((mrange/mdiff) + mdiff);
            }
            // get the legend (only possible after setting data)
            Legend l1 = fig1.getLegend();

            // modify the legend ...
            l1.setForm(Legend.LegendForm.CIRCLE);
            l1.setTextSize(getResources().getInteger(R.integer.ltextsize) - 1);
            l1.setFormSize(getResources().getInteger(R.integer.ltextsize));
            l1.setTextColor(Color.BLACK);
            l1.setPosition(Legend.LegendPosition.BELOW_CHART_LEFT);

            //Define X/Y axis labels.
            XAxis xAxis1 = fig1.getXAxis();
            xAxis1.setTextSize(getResources().getInteger(R.integer.ltextsize) - 1);
            xAxis1.setTextColor(Color.BLACK);
            xAxis1.setDrawAxisLine(true);
            xAxis1.setLabelRotationAngle(-45);
            xAxis1.setPosition(XAxis.XAxisPosition.BOTTOM);

            YAxis leftAxis1 = fig1.getAxisLeft();
            leftAxis1.setTextColor(Color.BLACK);
            leftAxis1.setDrawGridLines(true);
            //if data exists set y-axis parameters
            if (smart_pres.size() > 0) {
                leftAxis1.setAxisMaxValue(maxpres);
                leftAxis1.setAxisMinValue(minpres);
                leftAxis1.setLabelCount(labelnum + 1, true);
                //Modify decimal format depending on scale and range.
                if (pscale.equals("psi") || pscale.equals("inHg") || pscale.equals("kPa") || (mrange <= 0.5)) {
                    leftAxis1.setValueFormatter(new MyYAxisValueFormatter3());
                } else {
                    leftAxis1.setValueFormatter(new MyYAxisValueFormatter());
                }
            }

            if (smart_dp.size() > 0) {
                //Get initial limits of data.
                maxdp0 = Collections.max(smart_dp);
                mindp0 = Collections.min(smart_dp);
                mrangedp0 = Math.abs(maxdp0 - mindp0);
                if (mrangedp0 == 0) {
                    maxdp0 = Math.round(maxdp0+1);
                    mindp0 = Math.round(mindp0-1);
                    mrangedp0 = Math.abs(maxdp0-mindp0);
                }
                mdiffdp0 = setLabels(mrangedp0);

                //Extends limits to prevent data from being cutoff.
                maxdp= maxdp0 + mdiffdp0;
                mindp = mindp0 - mdiffdp0;

                //Redefine limits
                mrangedp = Math.abs(maxdp - mindp);
                mdiffdp = setLabels(mrangedp);
                //Smooth limits
                maxdp = ceilToX(maxdp,1.0f/mdiffdp);
                mindp = floorToX(mindp,1.0f/mdiffdp);
                //Determine an appropriate number of y-tick labels
                labelnumdp = Math.round((mrangedp / mdiffdp) + mdiffdp);
            }
            //Set 2nd Y-axis
            YAxis rightAxis1 = fig1.getAxisRight();
            rightAxis1.setDrawAxisLine(false);
            rightAxis1.setEnabled(true);
            rightAxis1.setTextColor(Color.BLACK);
            rightAxis1.setDrawGridLines(false);
            //If pressure change data exists set right-axis parameters
            if (smart_dp.size() > 0) {
                rightAxis1.setLabelCount(labelnumdp + 1, true);
                rightAxis1.setAxisMaxValue(maxdp);
                rightAxis1.setAxisMinValue(mindp);
                //Modify decimal precision based on pressure scale and data range.
                if (pscale.equals("psi") || pscale.equals("inHg") || pscale.equals("kPa") || (mrangedp <= 0.5)) {
                    rightAxis1.setValueFormatter(new MyYAxisValueFormatter3());
                } else {
                    rightAxis1.setValueFormatter(new MyYAxisValueFormatter());
                }
            }
            //Set custom colors for pressure/pressure change
            int c11 = Color.rgb(207, 55, 33);
            int c12 = Color.rgb(245, 184, 65);
            //If data exists plot it.
            if (smart_pres.size() > 0) {
                //Set draw order so bars are behind lines
                fig1.setDrawOrder(new CombinedChart.DrawOrder[]{CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE});

                //Define x-axis labels
                String[] xs = new String[local_pres_times.size()];
                xs = local_pres_times.toArray(xs);

                //Generate line and bar data for chart.
                CombinedData data = new CombinedData(xs);
                data.setData(generateLineData(smart_pres, c11, "Pressure", local_pres_times.size()));
                data.setData(generateBarData(smart_dp, c12, "Pressure Change", local_pres_times.size()));
                fig1.setData(data);
                //If enough data exists zoom and animate to latest pressures.
                if (smart_pres.size() > 10) {
                    float scale1 = smart_pres.size()/10.0f;
                    fig1.zoomAndCenterAnimated(scale1, 1f, fig1.getXValCount(), smart_pres.get(smart_pres.size() - 1), YAxis.AxisDependency.LEFT, 1500);
                }
            }

            //Listen for zoom button press
            button1.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    //Zoom to latest data if sufficient data exists.
                    if (smart_pres.size() > 10) {
                        float scale11 = smart_pres.size()/10.0f;
                        fig1.zoomAndCenterAnimated(scale11, 1f, fig1.getXValCount(), smart_pres.get(smart_pres.size() - 1), YAxis.AxisDependency.LEFT, 1500);
                    }
                }
            });
            //If All Pressures button is clicked zoom out to view all chart data.
            button2.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    fig1.fitScreen();
                }
            });

        }
    }

    @Override
    public void onPause() {
        Timber.d("onPause");
        super.onPause();
    }


    @Override
    public void onDestroy() {
        Timber.d("onDestroy");
        //Clear all global array lists and charts.
        smart_pres.clear(); smart_dp.clear();
        smart_times.clear();
        local_pres_times.clear();
        if (fig1!=null){fig1.clear();}
        super.onDestroy();
    }

    //Set Yaxis Formatter to ensure appropriate precision for y-axis labels
    public class MyYAxisValueFormatter3 implements YAxisValueFormatter {
        private DecimalFormat mFormat;

        public MyYAxisValueFormatter3 () {
            mFormat = new DecimalFormat("########0.000"); // use one decimal
        }

        @Override
        public String getFormattedValue(float value, YAxis yAxis) {
            // write your logic here
            // access the YAxis object to get more information
            return mFormat.format(value); // e.g. append a dollar-sign
        }
    }

    //Generate single Line dataset
    private LineData generateLineData(ArrayList<Float>vec1, int c1, String s1, int siz) {
        LineData d = new LineData();
        ArrayList<Entry> entries = new ArrayList<>();

        //Initialize y-val entries
        for (int index = 0; index < siz; index++)
            entries.add(new Entry(vec1.get(index), index));

        //Set line dataset properties
        LineDataSet set = new LineDataSet(entries, s1);
        set.setColor(c1);
        set.setLineWidth(3f);
        set.setCircleColor(c1);
        set.setCircleRadius(3f);
        set.setFillColor(c1);
        set.setDrawCubic(false);
        set.setDrawValues(false);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        d.addDataSet(set);

        return d;
    }

    //Generate bar dataset
    private BarData generateBarData(ArrayList<Float> vec2, int c2, String s2, int siz) {

        BarData d = new BarData();

        ArrayList<BarEntry> entries = new ArrayList<>();

        //Initialize bar dataset y-val entries
        for (int index = 0; index < siz; index++)
            entries.add(new BarEntry(vec2.get(index), index));

        //Set bar dataset properties
        BarDataSet set = new BarDataSet(entries,s2);
        set.setColor(c2);
        set.setDrawValues(false);

        d.addDataSet(set);
        //Set axis depenency to RIGHT since all bar chart data labels are displayed on the right y-axis.
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);

        return d;
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
}