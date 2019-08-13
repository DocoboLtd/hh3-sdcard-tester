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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
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
    private TextView mLogTextView;
    private Spinner  mTestTypeSelector;
    private Spinner  mTestIntervalSelector;
    
    private static final int               LOG_BUFFER_SIZE   = 20;
    private static final ArrayList<String> sLogMessageBuffer = new ArrayList<>(LOG_BUFFER_SIZE);
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mLogTextView = (TextView) findViewById(R.id.log_text_view);
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
        
        if (savedInstanceState == null)
        {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int testType = sharedPreferences.getInt("test_type", 0);
            mTestTypeSelector.setSelection(testType);
            
            int testInterval = sharedPreferences.getInt("test_interval", 0);
            mTestIntervalSelector.setSelection(testInterval);
    
            if (sharedPreferences.getBoolean("test_initiated", false))
            {
                findViewById(R.id.button_start).performClick();
            }
        }
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
                    for (int index = sLogMessageBuffer.size() - 1; index >= 0; index--)
                    {
                        displayString.append(sLogMessageBuffer.get(index)).append("\n");
                    }
                }
                
                mLogTextView.setText(displayString);
            }
        });
    }
    
    @Override
    public void onClick(View view)
    {
        TestType testType = TestType.values()[mTestTypeSelector.getSelectedItemPosition()];
        TestInterval interval = TestInterval.values()[mTestIntervalSelector.getSelectedItemPosition()];
        
        if (view.getId() == R.id.button_start)
        {
            SDCardTestManager.getInstance().start(getApplicationContext(), testType, interval);
            saveTestParams(testType, interval, true);
        }
        else if (view.getId() == R.id.button_stop)
        {
            SDCardTestManager.getInstance().stop(getApplicationContext());
            saveTestParams(testType, interval, false);
        }
    }
    
    private void saveTestParams(TestType testType, TestInterval testInterval, boolean testInitiated)
    {
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit()
                .putInt("test_type", testType.ordinal())
                .putInt("test_interval", testInterval.ordinal())
                .putBoolean("test_initiated", testInitiated)
                .apply();
    }
}
