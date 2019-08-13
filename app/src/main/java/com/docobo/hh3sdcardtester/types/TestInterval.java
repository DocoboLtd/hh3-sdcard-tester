/*
 * *
 *  * Copyright (C) 2002 to Present, Docobo Ltd.
 *  * <br><br>
 *  * <b>Class ${NAME}</b>
 *  * <br><br>
 *  * TODO - Add class definition here.
 *
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
