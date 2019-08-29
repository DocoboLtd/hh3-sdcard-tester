/*
 *  Copyright (C) 2010 Docobo Ltd - All Rights Reserved
 *
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 */

package com.docobo.hh3sdcardtester.platform;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

class HH3PlatformManager extends PlatformManager
{
    public HH3PlatformManager()
    {
        super("HH3PlatformManager");
    }
    
    @Override
    public boolean isDocoboDevice()
    {
        return true;
    }
    
    @Override
    public void rebootDevice(Context context, String reason)
    {
        try
        {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            powerManager.reboot(reason);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to request reboot", e);
        }
    }
}
