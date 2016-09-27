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

public class PressureChangeLog extends AppCompatActivity {
    private Context context;

    AppPreferences appPreferences;
    String pscale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pressure_change_log);
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
        TableLayout tableLayout = (TableLayout) findViewById(R.id.tablelayout2);
        // Add header row
        TableRow rowHeader = new TableRow(context);
        rowHeader.setBackgroundColor(Color.parseColor("#c0c0c0"));
        rowHeader.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));
        String[] headerText = {"LAT ", " LNG ", " TIME ", " 15 min ", " 30 min ", " 1 hour ", " 3 hour ", " 6 hour ", " 12 hour"};
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
        TextView ch1 = (TextView) findViewById(R.id.dbtitle2);
        ch1.setText(getString(R.string.dp_log_title, pscale));

        //Determine if tables exists
        Boolean dptableExists = appPreferences.getBoolean("dptableExists",false);

        if (dptableExists) {
            SQLiteDatabase db2 = openOrCreateDatabase("pressuredataDB", Context.MODE_PRIVATE, null);
            db2.beginTransaction();
            try {
                Cursor c02;
                c02 = db2.rawQuery("SELECT * FROM dptable", null);
                Timber.d("Cursor size: %d", c02.getCount());
                Timber.d("Cursor column count: %d", c02.getColumnCount());
                if (c02.getCount() > 0) {
                    while (c02.moveToNext()) {
                        //In all variables note the timestamp of the data in the second index of the pchartobj.
                        float lat = c02.getFloat(0);
                        float lng = c02.getFloat(1);
                        long time = c02.getLong(2);
                        float dp15min = c02.getFloat(3);
                        float dp30min = c02.getFloat(4);
                        float dp1hr = c02.getFloat(5);
                        float dp3hr = c02.getFloat(6);
                        float dp6hr = c02.getFloat(7);
                        float dp12hr = c02.getFloat(8);

                        //Format pressures as strings (to allow for N/A).
                        String m15,m30,h1,h3,h6,h12;
                        m15 = dp_to_str(dp15min);
                        m30 = dp_to_str(dp30min);
                        h1 = dp_to_str(dp1hr);
                        h3 = dp_to_str(dp3hr);
                        h6 = dp_to_str(dp6hr);
                        h12 = dp_to_str(dp12hr);

                        //Roun position to nearest ~10m.
                        lat = round4th(lat);
                        lng = round4th(lng);

                        //Format times
                        String datetime;
                        SimpleDateFormat sdf2 = new SimpleDateFormat("EEE, h:mm a", Locale.US);
                        datetime = sdf2.format(new Date(time * 1000L));

                        // dara rows
                        TableRow row = new TableRow(context);
                        row.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                                TableLayout.LayoutParams.WRAP_CONTENT));
                        String[] colText = {lat + " ", lng + " ", datetime + " ", m15 + " ", m30 + " ", h1 + " ", h3 + " ", h6 + " ", h12 + ""};
                        for (String text : colText) {
                            TextView tv = new TextView(this);
                            tv.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                                    TableRow.LayoutParams.WRAP_CONTENT));
                            tv.setGravity(Gravity.CENTER);
                            tv.setTextSize(14);
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

    public String dp_to_str(float dp) {
        String dpstr;
        if (dp == -100.00f) {
            dpstr = "N/A";
        } else {
            dp = convert_dp(dp);
            dpstr = String.format(Locale.getDefault(),"%.2f",dp);
        }
        return dpstr;
    }

    public float convert_dp(float dp) {
        switch (pscale) {
            case ("inHg"):
                dp = dp / 33.8638866667f;
                break;
            case ("mmHg"):
                dp = dp / 1.3332239f;
                break;
            case ("psi"):
                dp = dp * 0.0145038f;
                break;
            case ("kPa"):
                dp = dp * 0.1f;
                break;
        }
        return dp;
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