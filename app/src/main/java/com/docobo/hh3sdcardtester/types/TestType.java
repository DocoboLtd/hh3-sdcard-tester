/*
 *  Copyright (C) 2010 Docobo Ltd - All Rights Reserved
 *
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 */

package com.docobo.hh3sdcardtester.types;

import java.security.SecureRandom;

public enum TestType
{
    RebootOnly,
    Random,
    SmallFileTest,
    LargeFileTest;
    
    public int getByteCount()
    {
        switch (this)
        {
            case RebootOnly:
                return 0;
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
