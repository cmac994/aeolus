package com.example.cmac.aeolus;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import net.grandcentrix.tray.AppPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import timber.log.Timber;

/**
 * Created by cmac on 9/27/16.
 */

public class PressureLog extends AppCompatActivity {
    private Context context;

    AppPreferences appPreferences;
    String pscale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pressure_log);
        context = this;

        //Define shared preferences and get current location info
        appPreferences = new AppPreferences(this.getApplicationContext());
        pscale = appPreferences.getString("pscale", "hPa");

        // Setup toolbar to replace the action bar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //Disable toolbar title in place of our own (defined in xml layout file)
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setTitle("");
        toolbar.setSubtitle("");

        // Reference to TableLayout
        TableLayout tableLayout = (TableLayout) findViewById(R.id.tablelayout);
        // Add header row
        TableRow rowHeader = new TableRow(context);
        rowHeader.setBackgroundColor(Color.parseColor("#c0c0c0"));
        rowHeader.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));
        String[] headerText = {"LAT ", " LNG ", " TIME ", " STATIONARY ", " PRESSURE ", " LOC ACCURACY (m)"};
        for (String c : headerText) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(14);
            tv.setPadding(5, 5, 5, 5);
            tv.setText(c);
            rowHeader.addView(tv);
        }
        tableLayout.addView(rowHeader);

        //Set chart titles
        TextView ch1 = (TextView) findViewById(R.id.dbtitle1);
        ch1.setText(getString(R.string.pressure_log_title, pscale));

        //Determine if tables exists
        Boolean ptableExists = appPreferences.getBoolean("ptableExists",false);

        if (ptableExists) {
            SQLiteDatabase db2 = openOrCreateDatabase("pressuredataDB", Context.MODE_PRIVATE, null);
            db2.beginTransaction();
            try {
                Cursor c02;
                c02 = db2.rawQuery("SELECT * FROM ptable", null);
                if (c02.getCount() > 0) {
                    while (c02.moveToNext()) {
                        //In all variables note the timestamp of the data in the second index of the pchartobj.
                        float lat = c02.getFloat(0);
                        float lng = c02.getFloat(1);
                        long time = c02.getLong(2);
                        int stationary = c02.getInt(3);
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
                        pres = round3rd(pres);
                        lat = round4th(lat);
                        lng = round4th(lng);
                        float gpsacc = c02.getFloat(5);
                        String nomove, datetime;
                        if (stationary == 1) {
                            nomove = "True";
                        } else {
                            nomove = "False";
                        }
                        SimpleDateFormat sdf2 = new SimpleDateFormat("EEE, h:mm a", Locale.US);
                        datetime = sdf2.format(new Date(time * 1000L));

                        // dara rows
                        TableRow row = new TableRow(context);
                        row.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                                TableLayout.LayoutParams.WRAP_CONTENT));
                        String[] colText = {lat + " ", lng + " ", datetime + " ", nomove + " ", pres + " ", gpsacc + ""};
                        for (String text : colText) {
                            TextView tv = new TextView(this);
                            tv.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                                    TableRow.LayoutParams.WRAP_CONTENT));
                            tv.setGravity(Gravity.CENTER);
                            tv.setTextSize(16);
                            tv.setPadding(5, 5, 5, 5);
                            tv.setText(text);
                            row.addView(tv);
                        }
                        tableLayout.addView(row);
                    }

                }
                db2.setTransactionSuccessful();
            } catch (SQLiteException e) {
                e.printStackTrace();

            } finally {
                db2.endTransaction();
                // End the transaction.
                db2.close();
                // Close database
            }
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

    //Round value to 4th decimal place.
    public float round4th(double v) {
        return (float) (Math.floor((v * 10000.0) + 0.5d) / 10000.0);
    }
    //Round value to 3rd decimal place.
    public float round3rd(double v) {
        return (float) (Math.floor((v * 1000.0) + 0.5d) / 1000.0);
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume called");
    }

    @Override
    public void onPause() {
        Timber.d("onPause");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Timber.d("onDestroy");
        super.onDestroy();
    }
}