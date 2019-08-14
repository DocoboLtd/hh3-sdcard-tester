/*
 * *
 *  * Copyright (C) 2002 to Present, Docobo Ltd.
 *  * <br><br>
 *  * <b>Class ${NAME}</b>
 *  * <br><br>
 *  * TODO - Add class definition here.
 *
 */

package com.docobo.hh3sdcardtester.ui;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.docobo.hh3sdcardtester.Logger;
import com.docobo.hh3sdcardtester.R;
import com.docobo.hh3sdcardtester.SDCardTestManager;
import com.docobo.hh3sdcardtester.types.TestInterval;
import com.docobo.hh3sdcardtester.types.TestType;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, Logger
{
    private ScrollView mScrollView;
    private TextView mLogTextView;
    private TextView mTestNumber;
    private TextView mDeviceID;
    private Spinner  mTestTypeSelector;
    private Spinner  mTestIntervalSelector;
    
    private static final int               LOG_BUFFER_SIZE   = 20;
    private static final ArrayList<String> sLogMessageBuffer = new ArrayList<>(LOG_BUFFER_SIZE);
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(buildTitleText());
    
        mScrollView = (ScrollView) findViewById(R.id.scroll_view);
        mLogTextView = (TextView) findViewById(R.id.log_text_view);
        mTestNumber = (TextView) findViewById(R.id.test_number_textview);
        findViewById(R.id.button_start).setOnClickListener(this);
        findViewById(R.id.button_stop).setOnClickListener(this);
        
        mTestTypeSelector = (Spinner) findViewById(R.id.selector_test_type);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.test_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTestTypeSelector.setAdapter(adapter);
        
        mTestIntervalSelector = (Spinner) findViewById(R.id.selector_interval);
        adapter = ArrayAdapter.createFromResource(this, R.array.test_interval, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTestIntervalSelector.setAdapter(adapter);
        
        SDCardTestManager.getInstance().setLogger(this);
        updateLogTextView();
        initialiseUiFromPreferences();
        
        if (savedInstanceState != null)
        {
            mTestTypeSelector.setSelection(savedInstanceState.getInt("mTestTypeSelector", 0));
            mTestIntervalSelector.setSelection(savedInstanceState.getInt("mTestIntervalSelector", 0));
            mTestNumber.setText(savedInstanceState.getString("mTestNumber"));
        }
    }
    
    private void initialiseUiFromPreferences()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int testType = sharedPreferences.getInt("test_type", 0);
        mTestTypeSelector.setSelection(testType);
    
        int testInterval = sharedPreferences.getInt("test_interval", 0);
        mTestIntervalSelector.setSelection(testInterval);
    
        int testNumber = sharedPreferences.getInt("test_number", 0);
        mTestNumber.setText("" + testNumber);
        if (sharedPreferences.getBoolean("test_initiated", false))
        {
            findViewById(R.id.button_start).performClick();
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putInt("mTestTypeSelector", mTestTypeSelector.getSelectedItemPosition());
        outState.putInt("mTestIntervalSelector", mTestIntervalSelector.getSelectedItemPosition());
        outState.putString("mTestNumber", mTestNumber.getText().toString());
        super.onSaveInstanceState(outState);
    }
    
    private CharSequence buildTitleText()
    {
        StringBuilder titleBuilder = new StringBuilder(getString(R.string.app_name));
        titleBuilder.append(" - V");
        try
        {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            titleBuilder.append(packageInfo.versionName);
        }
        catch (Exception e)
        {
            titleBuilder.append(Build.UNKNOWN);
        }
        titleBuilder.append(" [ Device ID: ").append(Build.SERIAL).append(" ]");
    
        return titleBuilder;
    }
    
    @Override
    public void onBackPressed()
    {
        findViewById(R.id.button_stop).performClick();
        setResult(1);
        finish();
    }
    
    @Override
    public void onLog(String message)
    {
        synchronized (sLogMessageBuffer)
        {
            if (sLogMessageBuffer.size() == LOG_BUFFER_SIZE)
            {
                sLogMessageBuffer.remove(0);
            }
            sLogMessageBuffer.add(message);
        }
        updateLogTextView();
    }
    
    
    private void updateLogTextView()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                StringBuilder displayString = new StringBuilder();
                synchronized (sLogMessageBuffer)
                {
                    for (int index = 0; index < sLogMessageBuffer.size(); index++)
                    {
                        displayString.append(sLogMessageBuffer.get(index)).append("\n");
                    }
                }
                
                mLogTextView.setText(displayString);
                mScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }
    
    @Override
    public void onClick(View view)
    {
        TestType testType = TestType.values()[mTestTypeSelector.getSelectedItemPosition()];
        TestInterval interval = TestInterval.values()[mTestIntervalSelector.getSelectedItemPosition()];
        int testNumber = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getInt("test_number", 0);
        
        if (view.getId() == R.id.button_start)
        {
            if (!SDCardTestManager.getInstance().isStarted())
            {
                testNumber = testNumber + 1;
                
                SDCardTestManager.getInstance().start(getApplicationContext(), testType, interval, testNumber == 1);
                saveTestParams(testType, interval, testNumber, true);
            }
        }
        else if (view.getId() == R.id.button_stop)
        {
            testNumber = 0;
            
            SDCardTestManager.getInstance().stop(getApplicationContext());
            saveTestParams(testType, interval, testNumber, false);
        }
        
        mTestNumber.setText("" + testNumber);
    }
    
    private void saveTestParams(TestType testType, TestInterval testInterval, int testNumber, boolean testInitiated)
    {
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit()
                .putInt("test_type", testType.ordinal())
                .putInt("test_interval", testInterval.ordinal())
                .putInt("test_number", testNumber)
                .putBoolean("test_initiated", testInitiated)
                .apply();
    }
}
