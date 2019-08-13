/*
 * *
 *  * Copyright (C) 2002 to Present, Docobo Ltd.
 *  * <br><br>
 *  * <b>Class ${NAME}</b>
 *  * <br><br>
 *  * TODO - Add class definition here.
 *
 */

package com.docobo.hh3sdcardtester;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.*;
import android.support.v4.os.EnvironmentCompat;
import android.text.TextUtils;
import android.util.Log;

import com.docobo.hh3sdcardtester.platform.PlatformManager;
import com.docobo.hh3sdcardtester.types.TestInterval;
import com.docobo.hh3sdcardtester.types.TestType;
import com.docobo.hh3sdcardtester.ui.MainActivity;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class SDCardTestManager
{
    private final static SDCardTestManager instance = new SDCardTestManager();
    
    public static SDCardTestManager getInstance()
    {
        return instance;
    }
    
    private final Object        lockObject          = new Object();
    private final AtomicBoolean mStarted            = new AtomicBoolean();
    private final File          mDataHashRecordFile;
    
    private TesterThread mTesterThread = null;
    
    private WeakReference<Logger> mLogger  = null;
    private Logger                internal = new Logger()
    {
        @Override
        public void onLog(String message)
        {
            synchronized (lockObject)
            {
                Logger logger = mLogger.get();
                if (logger != null)
                {
                    logger.onLog(message);
                }
                Log.d("SDTest", message);
            }
        }
    };
    
    private SDCardTestManager()
    {
        File dataHashRecordFileDir = new File("/sysctl/sdcardtest");
        if (!dataHashRecordFileDir.exists() && !dataHashRecordFileDir.mkdirs())
        {
            dataHashRecordFileDir = new File(Environment.getExternalStorageDirectory(), "sdcardtest");
        }
        mDataHashRecordFile = new File(dataHashRecordFileDir, "test_data_hash.txt");
    }
    
    public boolean isStarted()
    {
        return mStarted.get();
    }
    
    public boolean start(Context context, TestType testType, TestInterval testInterval)
    {
        if (mStarted.compareAndSet(false, true))
        {
            mTesterThread = new TesterThread(context, testType, testInterval, mDataHashRecordFile, internal);
            mTesterThread.start();
            
            automaticLaunch(true, context);
        }
        
        return mStarted.get();
    }
    
    public boolean stop(Context context)
    {
        if (mStarted.compareAndSet(true, false))
        {
            mTesterThread.interrupt();
            mTesterThread = null;
    
            mDataHashRecordFile.delete();
            automaticLaunch(false, context);
        }
        
        return !mStarted.get();
    }
    
    private static void automaticLaunch(boolean enable, Context context)
    {
        final String DOCOBO_PACKAGE_NAME = "docobo_package_name";
        final String DOCOBO_PACKAGE_CLASSNAME = "docobo_package_classname";
        
        try
        {
            ContentResolver contentResolver = context.getContentResolver();
            String packageName = Settings.Secure.getString(contentResolver, DOCOBO_PACKAGE_NAME);
            String className = Settings.Secure.getString(contentResolver, DOCOBO_PACKAGE_CLASSNAME);
            String expectedPackageName, expectedClassName;
            if (enable == false)
            {
                expectedPackageName = "";
                expectedClassName = "";
                Log.d("SDTest", "Alpha Package/ClassName cleared for development mode");
            }
            else
            {
                Intent startupIntent = new Intent(context, MainActivity.class);
                expectedPackageName = startupIntent.getComponent().getPackageName();
                expectedClassName = startupIntent.getComponent().getClassName();
            }
            
            if (!TextUtils.equals(packageName, expectedPackageName))
                Settings.Secure.putString(contentResolver, DOCOBO_PACKAGE_NAME, expectedPackageName);
            if (!TextUtils.equals(className, expectedClassName))
                Settings.Secure.putString(contentResolver, DOCOBO_PACKAGE_CLASSNAME, expectedClassName);
        }
        catch (Exception e)
        {
            Log.e("SDTest", "Error attempting to write alpha package/classname flags", e);
        }
    }
    
    public void setLogger(Logger logger)
    {
        this.mLogger = new WeakReference<>(logger);
    }
    
    private static class TesterThread extends Thread
    {
        private final Context      mApplicationContext;
        private final TestType     mTestType;
        private final TestInterval mTestInterval;
        private final File         mTestFile;
        private final File         mDataHashRecordFile;
        private final Logger       mLogger;
        
        public TesterThread(Context context, TestType testType, TestInterval testInterval, File dataHashRecordFile, Logger logger)
        {
            super("SDCardTester");
            this.mApplicationContext = context.getApplicationContext();
            this.mTestType = testType;
            this.mTestInterval = testInterval;
            this.mLogger = logger;
            
            this.mTestFile = new File(context.getDir("GeneratedData", Context.MODE_PRIVATE), "DataFile.dat");
            this.mDataHashRecordFile = dataHashRecordFile;
        }
        
        @Override
        public void run()
        {
            mDataHashRecordFile.getParentFile().mkdirs();
            int iteration = 1;
            while (iteration > 0)
            {
                log("+++ Starting Test " + iteration + " +++");
                try
                {
                    long sleepSeconds = 30;
                    log("Sleep for " + sleepSeconds + " secs before test");
                    Thread.sleep(sleepSeconds * 1000);
    
                    log(String.format("Test Type: %s", mTestType));
                    log(String.format("Test Interval: %s", mTestInterval));
                    log(String.format("Generated Data File: %s (Exists: %s)", mTestFile, mTestFile.exists()));
                    log(String.format("Generated Data Hash: %s (Exists: %s)", mDataHashRecordFile, mDataHashRecordFile.exists()));
    
                    String lastGeneratedDataHash = calculateHash(mTestFile);
                    log("Last generated data hash: " + lastGeneratedDataHash);
                    String lastDataHashValue = readFile(mDataHashRecordFile);
                    log("Last recorded data hash: " + lastDataHashValue);
    
                    if (lastDataHashValue == null)
                    {
                        log(" *** TEST: Initialising test... No Validation");
                    }
                    else if (lastGeneratedDataHash != null)
                    {
                        if (lastGeneratedDataHash.equals(lastDataHashValue))
                        {
                            log(" *** TEST: Validation Success");
                        }
                        else
                        {
                            log(" *** TEST: Validation Failed");
                            log("*** ERROR: DATA FILES OUT OF SYNC");
                            // Stop the test so the failure can be seen.
                            break;
                        }
                    }
                    else
                    {
                        log("*** TEST: Generated data has been wiped");
                        log("*** ERROR: FACTORY RESET DETECTED");
                        break;
                    }
                    
                    /*
                     * Generate random data
                     */
                    String testDataHash = generateTestData(mTestFile, mTestType.getByteCount());
                    log("Test Data Hash: " + testDataHash);
        
                    /*
                     * Record the data hash.
                     */
                    boolean newDataHashRecorded = writeToFile(mDataHashRecordFile, testDataHash);
                    if (!newDataHashRecorded)
                    {
                        log("*** Failed to record data hash ***");
                        break;
                    }
        
                    log("Wait for test interval: " + mTestInterval);
                    Thread.sleep(mTestInterval.getIntervalMilliseconds());
                }
                catch (InterruptedException e)
                {
                    // Prevent the device from rebooting.
                    break;
                }
                catch (Exception e)
                {
                    log("Exception: " + Log.getStackTraceString(e));
                }
    
                log("+++ Ending Test " + iteration + "+++");
    
                if (PlatformManager.getInstance().isDocoboDevice())
                {
                    PlatformManager.getInstance().rebootDevice(mApplicationContext, "SDCardTest-Reboot");
                    break;
                }
                iteration++;
            }
        }
    
        private String calculateHash(File file)
        {
            if (!file.exists())
            {
                return null;
            }
            
            String md5Hash = null;
    
            byte[] dataBuffer = new byte[20 * 1024];
            BufferedSource bufferedSource = null;
            MessageDigest messageDigest = null;
            try
            {
                messageDigest = MessageDigest.getInstance("MD5");
                
                bufferedSource = Okio.buffer(Okio.source(file));
                int readLength = -1;
                while ((readLength = bufferedSource.read(dataBuffer)) > 0)
                {
                    messageDigest.update(dataBuffer, 0, readLength);
                }
    
                md5Hash = toHexString(messageDigest.digest());
            }
            catch (Exception e)
            {
                log(String.format("Exception: calculateHash(%s): %s", file, Log.getStackTraceString(e)));
            }
            finally
            {
                close(bufferedSource);
            }
            
            return md5Hash;
        }
    
        private String generateTestData(File outputFile, long targetDataLength) throws NoSuchAlgorithmException, IOException
        {
            long generatedDataCount = 0;
            SecureRandom secureRandom = new SecureRandom();
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            
            outputFile.delete();
            if (!outputFile.exists())
            {
                outputFile.createNewFile();
            }
    
            BufferedSink bufferedSink = Okio.buffer(Okio.sink(outputFile));
            try
            {
                byte[] buffer = new byte[20 * 1024];
                while (generatedDataCount < targetDataLength)
                {
                    secureRandom.nextBytes(buffer);
                    // Write to file.
                    bufferedSink.write(buffer);
                    // Update MD5 digest.
                    messageDigest.update(buffer);
                    
                    generatedDataCount += buffer.length;
                }
            }
            finally
            {
                close(bufferedSink);
            }
            return toHexString(messageDigest.digest());
        }
        
        private boolean writeToFile(@NonNull File file, @NonNull String value)
        {
            boolean result = false;
            
            Sink sink = null;
            try
            {
                sink = Okio.sink(file);
                Okio.buffer(sink)
                        .writeUtf8(value)
                        .flush();
                
                result = true;
            }
            catch (java.io.IOException e)
            {
                log(String.format("Exception: writeToFile(%s): %s", file, Log.getStackTraceString(e)));
            }
            finally
            {
                close(sink);
            }
            
            return result;
        }
        
        private @Nullable
        String readFile(@NonNull File sourceFile)
        {
            String result = null;
            
            if (sourceFile.exists())
            {
                Source source = null;
                try
                {
                    source = Okio.source(sourceFile);
                    result = Okio.buffer(source).readUtf8();
                }
                catch (java.io.IOException e)
                {
                    log(String.format("Exception: readFile(%s): %s", sourceFile, Log.getStackTraceString(e)));
                }
                finally
                {
                    close(source);
                }
            }
            
            return result;
        }
        
        private void close(Closeable closeable)
        {
            if (closeable == null) return;
            try
            {
                closeable.close();
            }
            catch (Exception e)
            {
                log("Exception: close: " + Log.getStackTraceString(e));
            }
        }
    
        private void log(String message)
        {
            this.mLogger.onLog(message);
        }
        
        private static String toHexString(byte[] bytes) {
            char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
            char[] hexChars = new char[bytes.length * 2];
            int v;
            for ( int j = 0; j < bytes.length; j++ ) {
                v = bytes[j] & 0xFF;
                hexChars[j*2] = hexArray[v/16];
                hexChars[j*2 + 1] = hexArray[v%16];
            }
            return new String(hexChars);
        }
        
    }
    
    private static class Helper
    {
        private Helper() {}
    }
}
