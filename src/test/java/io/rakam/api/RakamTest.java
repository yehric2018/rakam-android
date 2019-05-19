package io.rakam.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RakamTest extends BaseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetInstance() {
        RakamClient a = Rakam.getInstance();
        RakamClient b = Rakam.getInstance("");
        RakamClient c = Rakam.getInstance(null);
        RakamClient d = Rakam.getInstance(Constants.DEFAULT_INSTANCE);
        RakamClient e = Rakam.getInstance("app1");
        RakamClient f = Rakam.getInstance("app2");

        assertSame(a, b);
        assertSame(b, c);
        assertSame(c, d);
        assertSame(d, Rakam.getInstance());
        assertNotSame(d, e);
        assertSame(e, Rakam.getInstance("app1"));
        assertNotSame(e, f);
        assertSame(f, Rakam.getInstance("app2"));

        // test for instance name case insensitivity
        assertSame(e, Rakam.getInstance("APP1"));
        assertSame(e, Rakam.getInstance("App1"));
        assertSame(e, Rakam.getInstance("aPP1"));
        assertSame(e, Rakam.getInstance("apP1"));

        assertTrue(Rakam.instances.size() == 3);
        assertTrue(Rakam.instances.containsKey(Constants.DEFAULT_INSTANCE));
        assertTrue(Rakam.instances.containsKey("app1"));
        assertTrue(Rakam.instances.containsKey("app2"));
    }

    @Test
    public void testSeparateInstancesLogEventsSeparately() throws MalformedURLException {
        Rakam.instances.clear();
        DatabaseHelper.instances.clear();

        String newInstance1 = "newApp1";
        String newApiKey1 = "1234567890";
        String newInstance2 = "newApp2";
        String newApiKey2 = "0987654321";

        DatabaseHelper oldDbHelper = DatabaseHelper.getDatabaseHelper(context);
        DatabaseHelper newDbHelper1 = DatabaseHelper.getDatabaseHelper(context, newInstance1);
        DatabaseHelper newDbHelper2 = DatabaseHelper.getDatabaseHelper(context, newInstance2);

        // Setup existing Databasefile
        oldDbHelper.insertOrReplaceKeyValue("device_id", "oldDeviceId");
        oldDbHelper.insertOrReplaceKeyLongValue("sequence_number", 1000L);
        oldDbHelper.addEvent("oldEvent1");
        oldDbHelper.addIdentify("oldIdentify1");
        oldDbHelper.addIdentify("oldIdentify2");

        // Verify persistence of old database file in default instance
        Rakam.getInstance().initialize(context, new URL("test.com"), apiKey);
        Shadows.shadowOf(Rakam.getInstance().logThread.getLooper()).runToEndOfTasks();
        assertEquals(Rakam.getInstance().getDeviceId(), "oldDeviceId");
        assertTrue(oldDbHelper.dbFileExists());
        assertFalse(newDbHelper1.dbFileExists());
        assertFalse(newDbHelper2.dbFileExists());

        // init first new app and verify separate database file
        Rakam.getInstance(newInstance1).initialize(context, new URL("test.com"), newApiKey1);
        Shadows.shadowOf(
            Rakam.getInstance(newInstance1).logThread.getLooper()
        ).runToEndOfTasks();
        assertTrue(newDbHelper1.dbFileExists()); // db file is created after deviceId initialization

        assertFalse(newDbHelper1.getValue("device_id").equals("oldDeviceId"));
        assertEquals(
            newDbHelper1.getValue("device_id"), Rakam.getInstance(newInstance1).getDeviceId()
        );
        assertEquals(newDbHelper1.getEventCount(), 0);
        assertEquals(newDbHelper1.getIdentifyCount(), 0);

        // init second new app and verify separate database file
        Rakam.getInstance(newInstance2).initialize(context, new URL("test.com"), newApiKey2);
        Shadows.shadowOf(
            Rakam.getInstance(newInstance2).logThread.getLooper()
        ).runToEndOfTasks();
        assertTrue(newDbHelper2.dbFileExists()); // db file is created after deviceId initialization

        assertFalse(newDbHelper2.getValue("device_id").equals("oldDeviceId"));
        assertEquals(
            newDbHelper2.getValue("device_id"), Rakam.getInstance(newInstance2).getDeviceId()
        );
        assertEquals(newDbHelper2.getEventCount(), 0);
        assertEquals(newDbHelper2.getIdentifyCount(), 0);

        // verify existing database still intact
        assertTrue(oldDbHelper.dbFileExists());
        assertEquals(oldDbHelper.getValue("device_id"), "oldDeviceId");
        assertEquals(oldDbHelper.getLongValue("sequence_number").longValue(), 1001L);
        assertEquals(oldDbHelper.getEventCount(), 1);
        assertEquals(oldDbHelper.getIdentifyCount(), 2);

        // verify both apps can modify their database independently and not affect old database
        newDbHelper1.insertOrReplaceKeyValue("device_id", "fakeDeviceId");
        assertEquals(newDbHelper1.getValue("device_id"), "fakeDeviceId");
        assertFalse(newDbHelper2.getValue("device_id").equals("fakeDeviceId"));
        assertEquals(oldDbHelper.getValue("device_id"), "oldDeviceId");
        newDbHelper1.addIdentify("testIdentify3");
        assertEquals(newDbHelper1.getIdentifyCount(), 1);
        assertEquals(newDbHelper2.getIdentifyCount(), 0);
        assertEquals(oldDbHelper.getIdentifyCount(), 2);

        newDbHelper2.insertOrReplaceKeyValue("device_id", "brandNewDeviceId");
        assertEquals(newDbHelper1.getValue("device_id"), "fakeDeviceId");
        assertEquals(newDbHelper2.getValue("device_id"), "brandNewDeviceId");
        assertEquals(oldDbHelper.getValue("device_id"), "oldDeviceId");
        newDbHelper2.addEvent("testEvent2");
        newDbHelper2.addEvent("testEvent3");
        assertEquals(newDbHelper1.getEventCount(), 0);
        assertEquals(newDbHelper2.getEventCount(), 2);
        assertEquals(oldDbHelper.getEventCount(), 1);
    }

    @Test
    public void testSeparateInstancesSeparateSharedPreferences() throws MalformedURLException {
        // set up existing preferences values for default instance
        long timestamp = System.currentTimeMillis();
        String prefName = Constants.SHARED_PREFERENCES_NAME_PREFIX + "." + context.getPackageName();
        SharedPreferences preferences = context.getSharedPreferences(
            prefName, Context.MODE_PRIVATE);
        preferences.edit().putLong(Constants.PREFKEY_LAST_EVENT_ID, 1000L).commit();
        preferences.edit().putLong(Constants.PREFKEY_LAST_EVENT_TIME, timestamp).commit();
        preferences.edit().putLong(Constants.PREFKEY_LAST_IDENTIFY_ID, 2000L).commit();
        preferences.edit().putLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, timestamp).commit();

        // init default instance, which should load preferences values
        Rakam.getInstance().initialize(context, new URL("test.com"), apiKey);
        Shadows.shadowOf(Rakam.getInstance().logThread.getLooper()).runToEndOfTasks();
        assertEquals(Rakam.getInstance().lastEventId, 1000L);
        assertEquals(Rakam.getInstance().lastEventTime, timestamp);
        assertEquals(Rakam.getInstance().lastIdentifyId, 2000L);
        assertEquals(Rakam.getInstance().previousSessionId, timestamp);

        // init new instance, should have blank slate
        Rakam.getInstance("new_app").initialize(context, new URL("test.com"), "1234567890");
        Shadows.shadowOf(Rakam.getInstance("new_app").logThread.getLooper()).runToEndOfTasks();
        assertEquals(Rakam.getInstance("new_app").lastEventId, -1L);
        assertEquals(Rakam.getInstance("new_app").lastEventTime, -1L);
        assertEquals(Rakam.getInstance("new_app").lastIdentifyId, -1L);
        assertEquals(Rakam.getInstance("new_app").previousSessionId, -1L);

        // shared preferences should update independently
        Rakam.getInstance("new_app").logEvent("testEvent");
        Shadows.shadowOf(Rakam.getInstance("new_app").logThread.getLooper()).runToEndOfTasks();
        assertEquals(Rakam.getInstance("new_app").lastEventId, 1L);
        assertTrue(Rakam.getInstance("new_app").lastEventTime > timestamp);
        assertEquals(Rakam.getInstance("new_app").lastIdentifyId, -1L);
        assertTrue(Rakam.getInstance("new_app").previousSessionId > timestamp);

        assertEquals(Rakam.getInstance().lastEventId, 1000L);
        assertEquals(Rakam.getInstance().lastEventTime, timestamp);
        assertEquals(Rakam.getInstance().lastIdentifyId, 2000L);
        assertEquals(Rakam.getInstance().previousSessionId, timestamp);
    }
}
