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

import java.security.SecureRandom;

public enum TestType
{
    Random,
    SmallFileTest,
    LargeFileTest;
    
    public int getByteCount()
    {
        switch (this)
        {
            case Random:
                return new SecureRandom().nextInt(50) * 10 * 1024; // Multiples of 10kb up to 500kb
            case LargeFileTest:
                return 500 * 1024;
            default:
            case SmallFileTest:
                return 10 * 1024;
        }
    }
}
