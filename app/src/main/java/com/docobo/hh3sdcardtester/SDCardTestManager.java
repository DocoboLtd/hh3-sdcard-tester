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
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.*;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
    
    private final Object        lockObject = new Object();
    private final AtomicBoolean mStarted   = new AtomicBoolean();
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
        File dataHashRecordFileDir = new File("/sysctl/sdcardtest-2");
        if (!dataHashRecordFileDir.exists() && !dataHashRecordFileDir.mkdirs())
        {
            dataHashRecordFileDir = new File(Environment.getExternalStorageDirectory(), "SDCardTest");
        }
        mDataHashRecordFile = new File(dataHashRecordFileDir, "data_hash_record.txt");
    }
    
    public boolean isStarted()
    {
        return mStarted.get();
    }
    
    public boolean start(Context context, TestType testType, TestInterval testInterval, int testCycle)
    {
        if (mStarted.compareAndSet(false, true))
        {
            mTesterThread = new TesterThread(context, testType, testInterval, mDataHashRecordFile, testCycle, internal);
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
        private final File         mLogFile;
        private final int          mTestCycle;
    
        public TesterThread(Context context, TestType testType, TestInterval testInterval, File dataHashRecordFile, int testCycle, Logger logger)
        {
            super("SDCardTester");
            this.mApplicationContext = context.getApplicationContext();
            this.mTestType = testType;
            this.mTestInterval = testInterval;
            this.mTestCycle = testCycle;
            this.mLogger = logger;
            
            this.mTestFile = new File(context.getDir("GeneratedData", Context.MODE_PRIVATE), "DataFile.dat");
            this.mDataHashRecordFile = dataHashRecordFile;
    
            this.mLogFile = new File(Environment.getExternalStorageDirectory(), "SDCardTest/Log.txt");
            Log.d("SDTest", "Log file: " + this.mLogFile);
        }
        
        @Override
        public void run()
        {
            int iteration = 1;
            boolean interrupted = false;
            while (!interrupted)
            {
                if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
                {
                    mLogger.onLog("Waiting for external storage mount...");
                    while (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
                    {
                        try
                        {
                            Thread.sleep(500);
                        }
                        catch (InterruptedException e)
                        {
                            return;
                        }
                    }
                    mLogger.onLog("External storage mounting complete...");
                }
                
                if (mTestCycle <= 1)
                {
                    if (mLogFile.exists())
                    {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("'Log_'yyyyMMdd_HHmmss'.txt'", Locale.US);
                        File archiveFile = new File(mLogFile.getParentFile(), simpleDateFormat.format(new Date()));
                        while (archiveFile.exists())
                        {
                            try
                            {
                                Thread.sleep(200);
                            }
                            catch (InterruptedException e)
                            {
                            }
    
                            archiveFile = new File(mLogFile.getParentFile(), simpleDateFormat.format(new Date()));
                        }
                        if (!mLogFile.renameTo(archiveFile))
                        {
                            mLogger.onLog("Failed to archive old log file");
                        }
                    }
                    
                    // Delete all files and restart.
                    mLogFile.delete();
                    mDataHashRecordFile.delete();
                    mTestFile.delete();
                }
                else
                {
                    // Add this line as a log cycle break to the the log file.
                    writeToFile(this.mLogFile, true, "**********************************************************\r\n");
                }
                
                if (interrupted) break;
                
                log("+++ Starting Test " + iteration + " +++");
                boolean shutdown = false;
                try
                {
                    long timeToWaitAfterBoot = (1 * 60000) - SystemClock.elapsedRealtime();
                    if (timeToWaitAfterBoot > 0)
                    {
                        log("Sleep before test start: " + toTimeSpanString(timeToWaitAfterBoot));
                        Thread.sleep(timeToWaitAfterBoot);
                    }
                    log("Time since device boot: " + toTimeSpanString(SystemClock.elapsedRealtime()));
    
                    log(String.format("Test Cycle: %s", mTestCycle));
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
                    
                    {
                        log("Initialise Data Hash Record file");
                        boolean initialisationFailed = false;
                        File dataHashRecordParent = mDataHashRecordFile.getParentFile();
                        if (!dataHashRecordParent.exists() && !dataHashRecordParent.mkdirs())
                        {
                            log("*** ERROR: FAILED TO CREATE DIRS - " + dataHashRecordParent);
                            initialisationFailed = true;
                        }
                        if (dataHashRecordParent.exists())
                        {
                            if (!dataHashRecordParent.canRead() && !dataHashRecordParent.setReadable(true, false))
                            {
                                log("*** ERROR: FAILED TO SET READABLE - " + dataHashRecordParent);
                                initialisationFailed = true;
                            }
                            if (!dataHashRecordParent.canWrite() && !dataHashRecordParent.setWritable(true, false))
                            {
                                log("*** ERROR: FAILED TO SET WRITABLE - " + dataHashRecordParent);
                                initialisationFailed = true;
                            }
                        }
                        
                        if (mDataHashRecordFile.exists() || mDataHashRecordFile.createNewFile())
                        {
                            if (!mDataHashRecordFile.canRead() && !mDataHashRecordFile.setReadable(true, false))
                            {
                                log("*** ERROR: FAILED TO SET READABLE - " + mDataHashRecordFile);
                                initialisationFailed = true;
                            }
                            if (!mDataHashRecordFile.canWrite() && !mDataHashRecordFile.setWritable(true, false))
                            {
                                log("*** ERROR: FAILED TO SET WRITABLE - " + mDataHashRecordFile);
                                initialisationFailed = true;
                            }
                        }
                        
                        if (initialisationFailed)
                        {
                            break;
                        }
                    }
                    
                    /*
                     * Generate random data
                     */
                    long generateByteCount = mTestType.getByteCount();
                    String testDataHash = generateTestData(mTestFile, generateByteCount);
                    log(String.format("Test Data Hash: %s (ByteCount: %s)", testDataHash, generateByteCount));
                    
                    /*
                     * Record the data hash.
                     */
                    boolean newDataHashRecorded = writeToFile(mDataHashRecordFile, false, testDataHash);
                    if (!newDataHashRecorded)
                    {
                        log("*** Failed to record data hash ***");
                        break;
                    }
                    shutdown = true;
                }
                catch (InterruptedException e)
                {
                    // Prevent the device from rebooting.
                    interrupted = true;
                    shutdown = false;
                }
                catch (Exception e)
                {
                    log("Exception: " + Log.getStackTraceString(e));
                    shutdown = false;
                }
                
                if (!interrupted)
                {
                    try
                    {
                        log("Wait for test interval: " + mTestInterval);
                        Thread.sleep(mTestInterval.getIntervalMilliseconds());
                    }
                    catch (InterruptedException e)
                    {
                        interrupted = true;
                        shutdown = false;
                    }
                }
                
                log("+++ Ending Test " + iteration + " +++");
                
                if (shutdown && PlatformManager.getInstance().isDocoboDevice())
                {
                    log("Initiating reboot...");
                    PlatformManager.getInstance().rebootDevice(mApplicationContext, "SDCardTest-Reboot");
                    break;
                }
                iteration++;
            }
        }
    
        private void deleteAll(File... filesToDelete)
        {
            if (filesToDelete == null) return;
            
            for (File file : filesToDelete)
            {
                if (file.isDirectory())
                {
                    deleteAll(file.listFiles());
                }
                file.delete();
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
        
        private boolean writeToFile(@NonNull File file, boolean append, @NonNull String value)
        {
            boolean result = false;
            
            Sink sink = null;
            try
            {
                sink = append ? Okio.appendingSink(file) : Okio.sink(file);
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
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            String logEntry = "[" + sdf.format(new Date()) + "] " + message;
            
            writeToFile(this.mLogFile, true, logEntry + "\r\n");
            
            this.mLogger.onLog(logEntry);
        }
        
        private static String toHexString(byte[] bytes)
        {
            char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
            char[] hexChars = new char[bytes.length * 2];
            int v;
            for (int j = 0; j < bytes.length; j++)
            {
                v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v / 16];
                hexChars[j * 2 + 1] = hexArray[v % 16];
            }
            return new String(hexChars);
        }
        
        private static String toTimeSpanString(long timeInMilliseconds)
        {
            long hours = timeInMilliseconds / 3600000;
            long minutes = (timeInMilliseconds % 3600000) / 60000;
            long seconds = (timeInMilliseconds % 60000) / 1000;
            
            return String.format(Locale.US, "%1$02d:%2$02d:%3$02d", hours, minutes, seconds);
        }
        
        private void executeCommand(String command)
        {
            try
            {
                String[] executeCommand = new String[] { "/system/bin/sh", "-c", command };
                Process process = Runtime.getRuntime().exec(executeCommand);
                
                BufferedSource source = Okio.buffer(Okio.source(process.getInputStream()));
                while (!source.exhausted())
                {
                    log(" > " + source.readUtf8());
                }
            }
            catch (IOException e)
            {
                log(String.format("Exception: executeCommand [%s] : %s", command, Log.getStackTraceString(e)));
            }
        }
    }
    
    private static class Helper
    {
        private Helper() {}
    }
}
