package io.rakam.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import okhttp3.mockwebserver.RecordedRequest;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class InitializeTest extends BaseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testInitializeUserId() throws MalformedURLException {

        // the userId passed to initialize should override any existing values
        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_USER_ID, "oldestUserId").commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyValue(RakamClient.USER_ID_KEY, "oldUserId");

        String userId = "newUserId";
        rakam.initialize(context, server.url("/").url(), apiKey, userId);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();

        // Test that the user id is set.
        assertEquals(userId, rakam.userId);
        assertEquals(userId, dbHelper.getValue(RakamClient.USER_ID_KEY));

        // Test that events are logged.
        RecordedRequest request = sendEvent(rakam, "init_test_event", null);
        assertNotNull(request);

        // verified shared prefs not deleted
        assertEquals(
            prefs.getString(Constants.PREFKEY_USER_ID, null),
            "oldestUserId"
        );
    }

    @Test
    public void testInitializeUserIdFromSharedPrefs() throws MalformedURLException {
        String userId = "testUserId";
        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_USER_ID, userId).commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));

        rakam.initialize(context, server.url("/").url(), apiKey);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();

        // Test that the user id is set.
        assertEquals(rakam.userId, userId);
        assertEquals(userId, dbHelper.getValue(RakamClient.USER_ID_KEY));

        // verify shared prefs deleted
        assertNull(prefs.getString(Constants.PREFKEY_USER_ID, null));
    }

    @Test
    public void testInitializeUserIdFromDb() throws MalformedURLException {
        // since user id already exists in database, ignore old value in shared prefs
        String userId = "testUserId";
        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_USER_ID, "oldUserId").commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyValue(RakamClient.USER_ID_KEY, userId);

        rakam.initialize(context, server.url("/").url(), apiKey);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();

        // Test that the user id is set.
        assertEquals(rakam.userId, userId);
        assertEquals(userId, dbHelper.getValue(RakamClient.USER_ID_KEY));

        // verify that shared prefs not deleted
        assertEquals("oldUserId", prefs.getString(Constants.PREFKEY_USER_ID, null));
    }

    @Test
    public void testInitializeOptOut() throws MalformedURLException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Constants.PREFKEY_OPT_OUT, true).commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertNull(dbHelper.getLongValue(RakamClient.OPT_OUT_KEY));

        rakam.initialize(context, server.url("/").url(), apiKey);
        looper.runOneTask();

        assertTrue(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 1L);

        rakam.setOptOut(false);
        looper.runOneTask();
        assertFalse(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 0L);

        // verify shared prefs deleted
        assertFalse(prefs.getBoolean(Constants.PREFKEY_OPT_OUT, false));
    }

    @Test
    public void testInitializeOptOutFromDB() throws MalformedURLException {
        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Constants.PREFKEY_OPT_OUT, true).commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyLongValue(RakamClient.OPT_OUT_KEY, 0L);

        rakam.initialize(context, server.url("/").url(), apiKey);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();

        assertFalse(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 0L);

        // verify shared prefs not deleted
        assertTrue(prefs.getBoolean(Constants.PREFKEY_OPT_OUT, false));
    }


    @Test
    public void testInitializeLastEventId() throws JSONException, MalformedURLException {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putLong(Constants.PREFKEY_LAST_EVENT_ID, 3L).commit();

        rakam.initialize(context, server.url("/").url(), apiKey);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();

        assertEquals(rakam.lastEventId, 3L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY), 3L);

        rakam.logEvent("testEvent");
        Shadows.shadowOf(rakam.logThread.getLooper()).runToEndOfTasks();
        Shadows.shadowOf(rakam.logThread.getLooper()).runToEndOfTasks();

        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);

        assertEquals(events.getJSONObject(0).getLong("event_id"), 1L);

        assertEquals(rakam.lastEventId, 1L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY), 1L);

        // verify shared prefs deleted
        assertEquals(prefs.getLong(Constants.PREFKEY_LAST_EVENT_ID, -1), -1);
    }

    @Test
    public void testInitializePreviousSessionId() throws MalformedURLException {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, 4000L).commit();

        rakam.initialize(context, server.url("/").url(), apiKey);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();

        assertEquals(rakam.sessionId, 4000L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.PREVIOUS_SESSION_ID_KEY), 4000L);

        // verify shared prefs deleted
        assertEquals(prefs.getLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, -1), -1);
    }

    @Test
    public void testInitializeLastEventTime() throws MalformedURLException {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyLongValue(RakamClient.LAST_EVENT_TIME_KEY, 5000L);

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putLong(Constants.PREFKEY_LAST_EVENT_TIME, 4000L).commit();

        rakam.initialize(context, server.url("/").url(), apiKey);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();

        assertEquals(rakam.lastEventTime, 5000L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_TIME_KEY), 5000L);

        // verify shared prefs deleted
        assertEquals(prefs.getLong(Constants.PREFKEY_LAST_EVENT_TIME, -1), 4000L);
    }

    @Test
    public void testSkipSharedPrefsToDb() throws MalformedURLException {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyValue(RakamClient.DEVICE_ID_KEY, "testDeviceId");
        dbHelper.insertOrReplaceKeyLongValue(RakamClient.PREVIOUS_SESSION_ID_KEY, 1000L);
        dbHelper.insertOrReplaceKeyLongValue(RakamClient.LAST_EVENT_TIME_KEY, 2000L);

        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertNull(dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY));
        assertNull(dbHelper.getLongValue(RakamClient.LAST_IDENTIFY_ID_KEY));
        assertNull(dbHelper.getLongValue(RakamClient.OPT_OUT_KEY));

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_DEVICE_ID, "otherDeviceId").commit();
        prefs.edit().putString(Constants.PREFKEY_USER_ID, "testUserId").commit();
        prefs.edit().putBoolean(Constants.PREFKEY_OPT_OUT, true).commit();
        prefs.edit().putLong(Constants.PREFKEY_LAST_IDENTIFY_ID, 3000L).commit();

        rakam.initialize(context, server.url("/").url(), apiKey);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runOneTask();
        looper.runToEndOfTasks();

        assertEquals(dbHelper.getValue(RakamClient.DEVICE_ID_KEY), "testDeviceId");
        assertEquals((long) dbHelper.getLongValue(RakamClient.PREVIOUS_SESSION_ID_KEY), 1000L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_TIME_KEY), 2000L);
        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertNull(dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY));
        assertNull(dbHelper.getLongValue(RakamClient.LAST_IDENTIFY_ID_KEY));
        assertNull(dbHelper.getLongValue(RakamClient.OPT_OUT_KEY));

        assertEquals(prefs.getString(Constants.PREFKEY_DEVICE_ID, null), "otherDeviceId");
        assertEquals(prefs.getString(Constants.PREFKEY_USER_ID, null), "testUserId");
        assertTrue(prefs.getBoolean(Constants.PREFKEY_OPT_OUT, false));
        assertEquals(prefs.getLong(Constants.PREFKEY_LAST_IDENTIFY_ID, -1), 3000L);

        // after upgrade, pref values still there since they weren't deleted
        assertEquals(rakam.deviceId, "testDeviceId");
        assertEquals(rakam.previousSessionId, 1000L);
        assertEquals(rakam.lastEventTime, 2000L);
        assertNull(rakam.userId);
    }

    @Test
    public void testInitializePreviousSessionIdLastEventTime() throws MalformedURLException {
        // set a previous session id & last event time
        // log an event with timestamp such that same session is continued
        // log second event with timestamp such that new session is started

        rakam.setSessionTimeoutMillis(5000); // 5s

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_DEVICE_ID, "testDeviceId").commit();
        prefs.edit().putLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, 6000L).commit();
        prefs.edit().putLong(Constants.PREFKEY_LAST_EVENT_TIME, 6000L).commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyLongValue(RakamClient.LAST_EVENT_TIME_KEY, 7000L);

        long [] timestamps = {8000, 14000};
        clock.setTimestamps(timestamps);

        rakam.initialize(context, server.url("/").url(), apiKey);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runOneTask();
        looper.runToEndOfTasks();

        assertEquals(rakam.deviceId, "testDeviceId");
        assertEquals(rakam.previousSessionId, 6000L);
        assertEquals(rakam.lastEventTime, 7000L);
        assertNull(rakam.userId);

        // log first event
        rakam.logEvent("testEvent1");
        looper.runToEndOfTasks();
        assertEquals(rakam.previousSessionId, 6000L);
        assertEquals(rakam.lastEventTime, 8000L);

        // log second event
        rakam.logEvent("testEvent2");
        looper.runToEndOfTasks();
        assertEquals(rakam.previousSessionId, 14000L);
        assertEquals(rakam.lastEventTime, 14000L);
    }

    @Test
    public void testReloadDeviceIdFromDatabase() throws MalformedURLException {
        String deviceId = "test_device_id_from_database";
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        DatabaseHelper.getDatabaseHelper(context).insertOrReplaceKeyValue(
            RakamClient.DEVICE_ID_KEY, deviceId
        );
        assertNull(Utils.getStringFromSharedPreferences(
            context, rakam.instanceName, RakamClient.DEVICE_ID_KEY
        ));

        rakam.initialize(context, server.url("/").url(), apiKey);
        looper.runToEndOfTasks();
        assertEquals(deviceId, rakam.getDeviceId());

        String newSharedPrefsDeviceId = Utils.getStringFromSharedPreferences(
            context, rakam.instanceName, RakamClient.DEVICE_ID_KEY
        );
        assertEquals(deviceId, newSharedPrefsDeviceId);
    }

    @Test
    public void testReloadDeviceIdFromSharedPrefs() throws MalformedURLException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context, rakam.instanceName);
        assertNull(dbHelper.getValue(RakamClient.DEVICE_ID_KEY));

        String deviceId = "test_device_id_from_shared_prefs";
        Utils.writeStringToSharedPreferences(
            context, rakam.instanceName, RakamClient.DEVICE_ID_KEY, deviceId
        );

        rakam.initialize(context, server.url("/").url(), apiKey);
        looper.runToEndOfTasks();
        assertEquals(deviceId, rakam.getDeviceId());
        assertEquals(deviceId, dbHelper.getValue(RakamClient.DEVICE_ID_KEY));
        assertEquals(deviceId, Utils.getStringFromSharedPreferences(
            context, rakam.instanceName, RakamClient.DEVICE_ID_KEY
        ));
    }

    @Test
    public void testUpgradeDeviceIdFromLegacySharedPrefsToDatabase() throws MalformedURLException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

        // default instance migrates from legacy shared preferences into database
        String testDeviceId = "test_device_id_from_legacy_shared_prefs";
        String targetName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(targetName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_DEVICE_ID, testDeviceId).commit();

        rakam.initialize(context, server.url("/").url(), apiKey);
        looper.runToEndOfTasks();
        String deviceId = rakam.getDeviceId();
        assertEquals(deviceId, testDeviceId);
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertEquals(testDeviceId, dbHelper.getValue(RakamClient.DEVICE_ID_KEY));

        String newSharedPrefsDeviceId = Utils.getStringFromSharedPreferences(
            context, rakam.instanceName, RakamClient.DEVICE_ID_KEY
        );
        assertEquals(testDeviceId, newSharedPrefsDeviceId);

        // verify deviceId deleted from legacy shared prefs
        assertNull(prefs.getString(Constants.PREFKEY_DEVICE_ID, null));
    }

    @Test
    public void testInitializeDeviceIdWithRandomUUID() throws MalformedURLException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        rakam.initialize(context, server.url("/").url(), apiKey);
        looper.runToEndOfTasks();

        String deviceId = rakam.getDeviceId();
        assertEquals(37, deviceId.length());
        assertTrue(deviceId.endsWith("R"));
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertEquals(deviceId, dbHelper.getValue(RakamClient.DEVICE_ID_KEY));

        // verify deviceID persisted to SharedPrefs
        String sharedPrefsDeviceId = Utils.getStringFromSharedPreferences(
            context, rakam.instanceName, RakamClient.DEVICE_ID_KEY
        );
        assertEquals(deviceId, sharedPrefsDeviceId);
    }
}
