/*
 *  Copyright (C) 2010 Docobo Ltd - All Rights Reserved
 *
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 */

package com.docobo.hh3sdcardtester.types;

public enum TestInterval
{
    MINUTES_1(1),
    MINUTES_5(5),
    MINUTES_10(10),
    MINUTES_15(15);
    
    private final int value;
    TestInterval(int value)
    {
        this.value = value;
    }
    
    public long getIntervalMilliseconds() {
        return value * 60000;
    }
}
