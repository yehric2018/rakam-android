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
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
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
        rakam.initialize(context, new URL("https://app.rakam.io"), apiKey, userId);

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

        rakam.initialize(context, new URL("https://app.rakam.io"), apiKey);

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

        rakam.initialize(context, new URL("https://app.rakam.io"), apiKey);

        // Test that the user id is set.
        assertEquals(rakam.userId, userId);
        assertEquals(userId, dbHelper.getValue(RakamClient.USER_ID_KEY));

        // verify that shared prefs not deleted
        assertEquals("oldUserId", prefs.getString(Constants.PREFKEY_USER_ID, null));
    }

    @Test
    public void testInitializeOptOut() throws MalformedURLException {
        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Constants.PREFKEY_OPT_OUT, true).commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertNull(dbHelper.getLongValue(RakamClient.OPT_OUT_KEY));

        rakam.initialize(context, new URL("https://app.rakam.io"), apiKey);

        assertTrue(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 1L);

        rakam.setOptOut(false);
        assertFalse(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 0L);

        // verify shared prefs deleted
        assertFalse(prefs.getBoolean(Constants.PREFKEY_OPT_OUT, false));
    }

    @Test
    public void testInitializeOptOutFromDB() {
        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(Constants.PREFKEY_OPT_OUT, true).commit();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyLongValue(RakamClient.OPT_OUT_KEY, 0L);

        URL apiUrl;
        try {
            apiUrl = new URL("https://app.rakam.io");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        rakam.initialize(context, apiUrl, apiKey);

        assertFalse(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 0L);

        // verify shared prefs not deleted
        assertTrue(prefs.getBoolean(Constants.PREFKEY_OPT_OUT, false));
    }


    @Test
    public void testInitializeLastEventId() throws JSONException {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putLong(Constants.PREFKEY_LAST_EVENT_ID, 3L).commit();

        URL apiUrl;
        try {
            apiUrl = new URL("https://app.rakam.io");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        rakam.initialize(context, apiUrl, apiKey);

        assertEquals(rakam.getLastEventId(), 3L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY), 3L);

        rakam.logEvent("testEvent");
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

        runRequest(rakam);

        assertEquals(rakam.getLastEventId(), 1L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY), 1L);

        // verify shared prefs deleted
        assertEquals(prefs.getLong(Constants.PREFKEY_LAST_EVENT_ID, -1), -1);
    }

    @Test
    public void testInitializePreviousSessionId() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, 4000L).commit();

        URL apiUrl;
        try {
            apiUrl = new URL("https://app.rakam.io");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        rakam.initialize(context, apiUrl, apiKey);

        assertEquals(rakam.sessionId, 4000L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.PREVIOUS_SESSION_ID_KEY), 4000L);

        // verify shared prefs deleted
        assertEquals(prefs.getLong(Constants.PREFKEY_PREVIOUS_SESSION_ID, -1), -1);
    }

    @Test
    public void testInitializeLastEventTime() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.insertOrReplaceKeyLongValue(RakamClient.LAST_EVENT_TIME_KEY, 5000L);

        String sourceName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(sourceName, Context.MODE_PRIVATE);
        prefs.edit().putLong(Constants.PREFKEY_LAST_EVENT_TIME, 4000L).commit();

        URL apiUrl;
        try {
            apiUrl = new URL("https://app.rakam.io");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        rakam.initialize(context, apiUrl, apiKey);

        assertEquals(rakam.getLastEventTime(), 5000L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_TIME_KEY), 5000L);

        // verify shared prefs deleted
        assertEquals(prefs.getLong(Constants.PREFKEY_LAST_EVENT_TIME, -1), 4000L);
    }

    @Test
    public void testSkipSharedPrefsToDb() {
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

        URL apiUrl;
        try {
            apiUrl = new URL("https://app.rakam.io");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        rakam.initialize(context, apiUrl, apiKey);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

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
        assertEquals(rakam.getPreviousSessionId(), 1000L);
        assertEquals(rakam.getLastEventTime(), 2000L);
        assertNull(rakam.userId);
    }

    @Test
    public void testInitializePreviousSessionIdLastEventTime() {
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

        URL apiUrl;
        try {
            apiUrl = new URL("https://app.rakam.io");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        rakam.initialize(context, apiUrl, apiKey);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        assertEquals(rakam.deviceId, "testDeviceId");
        assertEquals(rakam.getPreviousSessionId(), 6000L);
        assertEquals(rakam.getLastEventTime(), 7000L);
        assertNull(rakam.userId);

        // log first event
        rakam.logEvent("testEvent1");
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), 6000L);
        assertEquals(rakam.getLastEventTime(), 8000L);

        // log second event
        rakam.logEvent("testEvent2");
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), 14000L);
        assertEquals(rakam.getLastEventTime(), 14000L);
    }
}
