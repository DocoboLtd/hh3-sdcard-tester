/*
 * *
 *  * Copyright (C) 2002 to Present, Docobo Ltd.
 *  * <br><br>
 *  * <b>Class ${NAME}</b>
 *  * <br><br>
 *  * TODO - Add class definition here.
 *
 */

package com.docobo.hh3sdcardtester.platform;

import android.content.Context;
import android.os.Build;

public class PlatformManager
{
    private static final PlatformManager instance = createInstance();
    
    public static PlatformManager getInstance()
    {
        return instance;
    }
    
    private static PlatformManager createInstance()
    {
        if ("Docobo".equalsIgnoreCase(Build.MANUFACTURER))
        {
            return new HH3PlatformManager();
        }
        return new PlatformManager();
    }
    
    protected final String TAG;
    
    PlatformManager(String tag)
    {
        TAG = tag;
    }
    
    private PlatformManager()
    {
        this("PlatformManager");
    }
    
    public boolean isDocoboDevice()
    {
        return false;
    }
    
    public void rebootDevice(Context context, String reason)
    {
        throw new UnsupportedOperationException("Unable to perform reboot on commercial Android devices");
    }
}
