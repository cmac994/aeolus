package com.example.cmac.aeolus;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

//Class for Preference Activity that provides a nice slidebar interface with which observation frequency can easily be modified
public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {

    private static final String TAG = SeekBarPreference.class.getSimpleName();

    protected int padding;
    protected int maxValue;
    protected int defaultVal;
    protected int minValue;
    protected int step;
    protected String unit;
    protected String explain;

    protected int currentValue;
    protected SeekBar seekBar;
    protected TextView explainText;
    protected TextView valueText;
    protected TextView unitText;
    protected LinearLayout valueLayout;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SeekBarPreference(Context context) {
        this(context, null);
    }

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        configure(context, attrs);
    }

    public SeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        configure(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        configure(context, attrs);
    }

    protected void configure(Context context, AttributeSet attrs) {
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference);
        try {
            maxValue = attributes.getInt(R.styleable.SeekBarPreference_maxValue, 100);
            minValue = attributes.getInt(R.styleable.SeekBarPreference_minValue, 2);
            defaultVal = attributes.getInt(R.styleable.SeekBarPreference_defaultValue, minValue);
            currentValue = defaultVal;
            unit = attributes.getString(R.styleable.SeekBarPreference_unit);
            explain = attributes.getString(R.styleable.SeekBarPreference_explain);
            padding = attributes.getInt(R.styleable.SeekBarPreference_padding, 0);
            step = attributes.getInt(R.styleable.SeekBarPreference_step,1);
        } finally {
            attributes.recycle();
        }
    }

    /**
     * @return The unit
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Set currentValue.
     *
     * @param currentValue The value to set
     */
    public void setCurrentValue(int currentValue, int minval) {
        if (currentValue < minval) {
            currentValue = minval;
        }
        if (currentValue > seekBar.getMax()) {
            currentValue = seekBar.getMax();
        }

        this.currentValue = currentValue;
        this.valueText.setText(String.valueOf(currentValue));
    }

    /**
     * Save currentValue to the {@link android.content.SharedPreferences}.
     */
    public void saveCurrentValue() {
        final boolean wasBlocking = shouldDisableDependents();

        if (shouldPersist()) {
            persistInt(currentValue);
            Log.d(TAG, "Persist value " + currentValue);
        }

        final boolean isBlocking = shouldDisableDependents();
        if (isBlocking != wasBlocking) {
            notifyDependencyChange(isBlocking);
        }
    }

    @Override
    protected View onCreateDialogView() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        float scale = getContext().getResources().getDisplayMetrics().density;
        int dpAsPixels = (int) (padding*scale + 0.5f);
        layout.setPadding(dpAsPixels, dpAsPixels, dpAsPixels, dpAsPixels);

        explainText = new TextView(getContext());
        explainText.setText(explain);
        explainText.setGravity(Gravity.CENTER);

        seekBar = new SeekBar(getContext());
        seekBar.setMax(maxValue);
        seekBar.setOnSeekBarChangeListener(this);

        valueText = new TextView(getContext());
        valueText.setText(String.valueOf(currentValue));

        unitText = new TextView(getContext());
        unitText.setText(unit);

        valueLayout = new LinearLayout(getContext());
        valueLayout.setOrientation(LinearLayout.HORIZONTAL);
        valueLayout.setGravity(Gravity.CENTER);
        valueLayout.addView(valueText);
        valueLayout.addView(unitText);

        return layout;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        Log.d(TAG, "onBindDialogView");

        clearParents();

        LinearLayout layout = (LinearLayout) view;

        layout.addView(explainText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(valueLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        layout.addView(seekBar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        updateView();
    }

    protected void clearParents() {
        ViewParent oldParent = seekBar.getParent();
        if (oldParent != null) {
            ((ViewGroup) oldParent).removeAllViews();
        }
    }

    protected void updateView() {
        seekBar.setProgress(currentValue);
        valueText.setText(String.valueOf(currentValue));
        notifyChanged();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            if (callChangeListener(currentValue)) {
                saveCurrentValue();
            }
        } else {
            resetToPersisted();
        }
    }

    protected void resetToPersisted() {
        int persisted = getPersistedInt(currentValue);
        setCurrentValue(persisted,minValue);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            currentValue = getPersistedInt(currentValue);
        } else {
            currentValue = defaultVal;
            saveCurrentValue();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        progress = progress / step;
        progress = progress * step;
        setCurrentValue(progress,minValue);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Do Nothing
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Do Nothing
    }
}
