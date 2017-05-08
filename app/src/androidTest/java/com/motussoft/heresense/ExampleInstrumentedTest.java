package com.motussoft.heresense;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.*;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith( AndroidJUnit4.class )
public class ExampleInstrumentedTest {

    private static final String TAG = "ExampleInstrumentedTest";

    public static SimpleDateFormat SDF_HHMMSS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals( "com.motussoft.heresense", appContext.getPackageName() );

        Log.i( TAG, "date: " + SDF_HHMMSS.format( System.currentTimeMillis() ) );

    }
}
