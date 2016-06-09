package io.rakam.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RakamClientTest extends BaseTest {

    private String generateStringWithLength(int length, char c) {
        if (length < 0) return "";
        char [] array = new char[length];
        Arrays.fill(array, c);
        return new String(array);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        rakam.initialize(context, apiKey);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSetUserId() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        String userId = "user_id";
        rakam.setUserId(userId);
        assertEquals(userId, dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertEquals(userId, rakam.getUserId());

        // try setting to null
        rakam.setUserId(null);
        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertNull(rakam.getUserId());
    }

    @Test
    public void testSetUserIdTwice() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        String userId1 = "user_id1";
        String userId2 = "user_id2";

        rakam.setUserId(userId1);
        assertEquals(rakam.getUserId(), userId1);
        rakam.logEvent("event1");
        looper.runToEndOfTasks();

        JSONObject event1 = getLastUnsentEvent();
        assertEquals(event1.optString("event_type"), "event1");
        assertEquals(event1.optString("user_id"), userId1);

        rakam.setUserId(userId2);
        assertEquals(rakam.getUserId(), userId2);
        rakam.logEvent("event2");
        looper.runToEndOfTasks();

        JSONObject event2 = getLastUnsentEvent();
        assertEquals(event2.optString("event_type"), "event2");
        assertEquals(event2.optString("user_id"), userId2);
    }

    @Test
    public void testSetDeviceId() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        assertNull(rakam.getDeviceId());
        looper.runToEndOfTasks();

        String deviceId = rakam.getDeviceId(); // Randomly generated device ID
        assertNotNull(deviceId);
        assertEquals(deviceId.length(), 36 + 1); // 36 for UUID, + 1 for appended R
        assertEquals(deviceId.charAt(36), 'R');
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);


        // test setting invalid device ids
        rakam.setDeviceId(null);
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);

        rakam.setDeviceId("");
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);

        rakam.setDeviceId("9774d56d682e549c");
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);

        rakam.setDeviceId("unknown");
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);

        rakam.setDeviceId("000000000000000");
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);

        rakam.setDeviceId("Android");
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);

        rakam.setDeviceId("DEFACE");
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);

        // set valid device id
        String newDeviceId = UUID.randomUUID().toString();
        rakam.setDeviceId(newDeviceId);
        assertEquals(rakam.getDeviceId(), newDeviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), newDeviceId);

        rakam.logEvent("test");
        looper.runToEndOfTasks();
        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("event_type"), "test");
        assertEquals(event.optString("device_id"), newDeviceId);
    }

    @Test
    public void testSetUserProperties() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        // setting null or empty user properties does nothing
        rakam.setUserProperties(null);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
        rakam.setUserProperties(new JSONObject());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);

        JSONObject userProperties = new JSONObject().put("key1", "value1").put("key2", "value2");
        rakam.setUserProperties(userProperties);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 1);
        JSONObject event = getLastUnsentIdentify();
        assertEquals(Constants.IDENTIFY_EVENT, event.optString("event_type"));
        assertEquals(event.optJSONObject("event_properties").length(), 0);

        JSONObject userPropertiesOperations = event.optJSONObject("user_properties");
        assertEquals(userPropertiesOperations.length(), 1);
        assertTrue(userPropertiesOperations.has(Constants.AMP_OP_SET));

        JSONObject setOperations = userPropertiesOperations.optJSONObject(Constants.AMP_OP_SET);
        assertTrue(compareJSONObjects(userProperties, setOperations));
    }

    @Test
    public void testIdentifyMultipleOperations() throws JSONException {
        String property1 = "string value";
        String value1 = "testValue";

        String property2 = "double value";
        double value2 = 0.123;

        String property3 = "boolean value";
        boolean value3 = true;

        String property4 = "json value";

        Identify identify = new Identify().setOnce(property1, value1).add(property2, value2);
        identify.set(property3, value3).unset(property4);

        // identify should ignore this since duplicate key
        identify.set(property4, value3);

        rakam.identify(identify);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(getUnsentIdentifyCount(), 1);
        assertEquals(getUnsentEventCount(), 0);
        JSONObject event = getLastUnsentIdentify();
        assertEquals(Constants.IDENTIFY_EVENT, event.optString("event_type"));

        JSONObject userProperties = event.optJSONObject("user_properties");
        JSONObject expected = new JSONObject();
        expected.put(Constants.AMP_OP_SET_ONCE, new JSONObject().put(property1, value1));
        expected.put(Constants.AMP_OP_ADD, new JSONObject().put(property2, value2));
        expected.put(Constants.AMP_OP_SET, new JSONObject().put(property3, value3));
        expected.put(Constants.AMP_OP_UNSET, new JSONObject().put(property4, "-"));
        assertTrue(compareJSONObjects(userProperties, expected));
    }

    @Test
    public void testReloadDeviceIdFromDatabase() {
        String deviceId = "test_device_id";
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        assertNull(rakam.getDeviceId());
        DatabaseHelper.getDatabaseHelper(context).insertOrReplaceKeyValue(
                RakamClient.DEVICE_ID_KEY,
                deviceId
        );
        looper.getScheduler().advanceToLastPostedRunnable();
        assertEquals(deviceId, rakam.getDeviceId());
    }

    @Test
    public void testDoesNotUpgradeDeviceIdFromSharedPrefsToDatabase() {
        assertNull(rakam.getDeviceId());
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        // initializeDeviceId no longer fetches from SharedPrefs, will get advertising ID instead
        String targetName = Constants.PACKAGE_NAME + "." + context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(targetName, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREFKEY_DEVICE_ID, "test_device_id").commit();

        looper.getScheduler().advanceToLastPostedRunnable();
        String deviceId = rakam.getDeviceId();
        assertTrue(deviceId.endsWith("R"));
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertEquals(
                deviceId,
                dbHelper.getValue(RakamClient.DEVICE_ID_KEY)
        );
    }

    @Test
    public void testGetDeviceIdWithoutAdvertisingId() {
        assertNull(rakam.getDeviceId());
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.getScheduler().advanceToLastPostedRunnable();
        assertNotNull(rakam.getDeviceId());
        assertEquals(37, rakam.getDeviceId().length());
        String deviceId = rakam.getDeviceId();
        assertTrue(deviceId.endsWith("R"));
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertEquals(
                deviceId,
                dbHelper.getValue(RakamClient.DEVICE_ID_KEY)
        );
    }

    @Test
    public void testOptOut() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        ShadowLooper httplooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertFalse(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 0L);

        rakam.setOptOut(true);
        assertTrue(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 1L);
        RecordedRequest request = sendEvent(rakam, "test_opt_out", null);
        assertNull(request);

        // Event shouldn't be sent event once opt out is turned off.
        rakam.setOptOut(false);
        assertFalse(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 0L);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        httplooper.runToEndOfTasks();
        assertNull(request);

        request = sendEvent(rakam, "test_opt_out", null);
        assertNotNull(request);
    }

    @Test
    public void testOffline() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        ShadowLooper httplooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());

        rakam.setOffline(true);
        RecordedRequest request = sendEvent(rakam, "test_offline", null);
        assertNull(request);

        // Events should be sent after offline is turned off.
        rakam.setOffline(false);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        httplooper.runToEndOfTasks();

        try {
            request = server.takeRequest(1, SECONDS);
        } catch (InterruptedException e) {
        }
        assertNotNull(request);
    }

    @Test
    public void testLogEvent() {
        RecordedRequest request = sendEvent(rakam, "test_event", null);
        assertNotNull(request);
    }

    @Test
    public void testIdentify() throws JSONException {
        long [] timestamps = {1000, 1001};
        clock.setTimestamps(timestamps);

        RecordedRequest request = sendIdentify(rakam, new Identify().set("key", "value"));
        assertNotNull(request);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 1);
        JSONObject identify = events.getJSONObject(0);
        assertEquals(identify.getString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(identify.getLong("event_id"), 1);
        assertEquals(identify.getLong("timestamp"), timestamps[0]);
        assertEquals(identify.getLong("sequence_number"), 1);
        JSONObject userProperties = identify.getJSONObject("user_properties");
        assertEquals(userProperties.length(), 1);
        assertTrue(userProperties.has(Constants.AMP_OP_SET));

        JSONObject expected = new JSONObject();
        expected.put("key", "value");
        assertTrue(compareJSONObjects(userProperties.getJSONObject(Constants.AMP_OP_SET), expected));

        // verify db state
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_IDENTIFY_ID_KEY), 1L);
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY), -1L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.SEQUENCE_NUMBER_KEY), 1L);
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_EVENT_TIME_KEY), timestamps[0]);
    }

    @Test
    public void testNullIdentify() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);

        rakam.identify(null);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testLog3Events() throws InterruptedException {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();


        rakam.logEvent("test_event1");
        rakam.logEvent("test_event2");
        rakam.logEvent("test_event3");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), 3);
        assertEquals(getUnsentIdentifyCount(), 0);
        JSONArray events = getUnsentEvents(3);
        for (int i = 0; i < 3; i++) {
            assertEquals(events.optJSONObject(i).optString("event_type"), "test_event" + (i+1));
            assertEquals(events.optJSONObject(i).optLong("timestamp"), timestamps[i]);
            assertEquals(events.optJSONObject(i).optLong("sequence_number"), i+1);
        }

        // send response and check that remove events works properly
        runRequest(rakam);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testLog3Identifys() throws JSONException {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        Robolectric.getForegroundThreadScheduler().advanceTo(1);
        rakam.identify(new Identify().set("photo_count", 1));
        rakam.identify(new Identify().add("karma", 2));
        rakam.identify(new Identify().unset("gender"));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 3);
        JSONArray events = getUnsentIdentifys(3);

        JSONObject expectedIdentify1 = new JSONObject();
        expectedIdentify1.put(Constants.AMP_OP_SET, new JSONObject().put("photo_count", 1));
        JSONObject expectedIdentify2 = new JSONObject();
        expectedIdentify2.put(Constants.AMP_OP_ADD, new JSONObject().put("karma", 2));
        JSONObject expectedIdentify3 = new JSONObject();
        expectedIdentify3.put(Constants.AMP_OP_UNSET, new JSONObject().put("gender", "-"));

        assertEquals(events.optJSONObject(0).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(0).optLong("timestamp"), timestamps[0]);
        assertEquals(events.optJSONObject(0).optLong("sequence_number"), 1);
        assertTrue(compareJSONObjects(
                events.optJSONObject(0).optJSONObject("user_properties"), expectedIdentify1
        ));
        assertEquals(events.optJSONObject(1).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(1).optLong("timestamp"), timestamps[1]);
        assertEquals(events.optJSONObject(1).optLong("sequence_number"), 2);
        assertTrue(compareJSONObjects(
                events.optJSONObject(1).optJSONObject("user_properties"), expectedIdentify2
        ));
        assertEquals(events.optJSONObject(2).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(2).optLong("timestamp"), timestamps[2]);
        assertEquals(events.optJSONObject(2).optLong("sequence_number"), 3);
        assertTrue(compareJSONObjects(
                events.optJSONObject(2).optJSONObject("user_properties"), expectedIdentify3
        ));

        // send response and check that remove events works properly
        runRequest(rakam);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testLogEventAndIdentify() throws JSONException {
        long [] timestamps = {1, 1, 2};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        rakam.logEvent("test_event");
        rakam.identify(new Identify().add("photo_count", 1));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        // verify some internal counters
        assertEquals(getUnsentEventCount(), 1);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(getUnsentIdentifyCount(), 1);
        assertEquals(rakam.getLastIdentifyId(), 1);

        JSONArray unsentEvents = getUnsentEvents(1);
        assertEquals(unsentEvents.optJSONObject(0).optString("event_type"), "test_event");
        assertEquals(unsentEvents.optJSONObject(0).optLong("sequence_number"), 1);

        JSONObject expectedIdentify = new JSONObject();
        expectedIdentify.put(Constants.AMP_OP_ADD, new JSONObject().put("photo_count", 1));

        JSONArray unsentIdentifys = getUnsentIdentifys(1);
        assertEquals(unsentIdentifys.optJSONObject(0).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(unsentIdentifys.optJSONObject(0).optLong("sequence_number"), 2);
        assertTrue(compareJSONObjects(
                unsentIdentifys.optJSONObject(0).optJSONObject("user_properties"), expectedIdentify
        ));

        // send response and check that remove events works properly
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 2);
        assertEquals(events.optJSONObject(0).optString("event_type"), "test_event");
        assertEquals(events.optJSONObject(1).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertTrue(compareJSONObjects(
                events.optJSONObject(1).optJSONObject("user_properties"), expectedIdentify
        ));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testMergeEventsAndIdentifys() throws JSONException {
        long [] timestamps = {1, 2, 3, 4, 5, 5, 6, 7, 8, 9, 10};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        rakam.logEvent("test_event1");
        rakam.identify(new Identify().add("photo_count", 1));
        rakam.logEvent("test_event2");
        rakam.logEvent("test_event3");
        rakam.logEvent("test_event4");
        rakam.identify(new Identify().set("gender", "male"));
        rakam.identify(new Identify().unset("karma"));

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        // verify some internal counters
        assertEquals(getUnsentEventCount(), 4);
        assertEquals(rakam.getLastEventId(), 4);
        assertEquals(getUnsentIdentifyCount(), 3);
        assertEquals(rakam.getLastIdentifyId(), 3);

        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 7);

        JSONObject expectedIdentify1 = new JSONObject();
        expectedIdentify1.put(Constants.AMP_OP_ADD, new JSONObject().put("photo_count", 1));
        JSONObject expectedIdentify2 = new JSONObject();
        expectedIdentify2.put(Constants.AMP_OP_SET, new JSONObject().put("gender", "male"));
        JSONObject expectedIdentify3 = new JSONObject();
        expectedIdentify3.put(Constants.AMP_OP_UNSET, new JSONObject().put("karma", "-"));

        assertEquals(events.getJSONObject(0).getString("event_type"), "test_event1");
        assertEquals(events.getJSONObject(0).getLong("event_id"), 1);
        assertEquals(events.getJSONObject(0).getLong("timestamp"), timestamps[0]);
        assertEquals(events.getJSONObject(0).getLong("sequence_number"), 1);

        assertEquals(events.getJSONObject(1).getString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.getJSONObject(1).getLong("event_id"), 1);
        assertEquals(events.getJSONObject(1).getLong("timestamp"), timestamps[1]);
        assertEquals(events.getJSONObject(1).getLong("sequence_number"), 2);
        assertTrue(compareJSONObjects(
                events.getJSONObject(1).getJSONObject("user_properties"), expectedIdentify1
        ));

        assertEquals(events.getJSONObject(2).getString("event_type"), "test_event2");
        assertEquals(events.getJSONObject(2).getLong("event_id"), 2);
        assertEquals(events.getJSONObject(2).getLong("timestamp"), timestamps[2]);
        assertEquals(events.getJSONObject(2).getLong("sequence_number"), 3);

        assertEquals(events.getJSONObject(3).getString("event_type"), "test_event3");
        assertEquals(events.getJSONObject(3).getLong("event_id"), 3);
        assertEquals(events.getJSONObject(3).getLong("timestamp"), timestamps[3]);
        assertEquals(events.getJSONObject(3).getLong("sequence_number"), 4);

        // sequence number guarantees strict ordering regardless of timestamp
        assertEquals(events.getJSONObject(4).getString("event_type"), "test_event4");
        assertEquals(events.getJSONObject(4).getLong("event_id"), 4);
        assertEquals(events.getJSONObject(4).getLong("timestamp"), timestamps[4]);
        assertEquals(events.getJSONObject(4).getLong("sequence_number"), 5);

        assertEquals(events.getJSONObject(5).getString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.getJSONObject(5).getLong("event_id"), 2);
        assertEquals(events.getJSONObject(5).getLong("timestamp"), timestamps[5]);
        assertEquals(events.getJSONObject(5).getLong("sequence_number"), 6);
        assertTrue(compareJSONObjects(
                events.getJSONObject(5).getJSONObject("user_properties"), expectedIdentify2
        ));

        assertEquals(events.getJSONObject(6).getString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.getJSONObject(6).getLong("event_id"), 3);
        assertEquals(events.getJSONObject(6).getLong("timestamp"), timestamps[6]);
        assertEquals(events.getJSONObject(6).getLong("sequence_number"), 7);
        assertTrue(compareJSONObjects(
                events.getJSONObject(6).getJSONObject("user_properties"), expectedIdentify3
        ));

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);

        // verify db state
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_IDENTIFY_ID_KEY), 3L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY), 4L);
        assertEquals((long) dbHelper.getLongValue(RakamClient.SEQUENCE_NUMBER_KEY), 7L);
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_EVENT_TIME_KEY), timestamps[6]);
    }

    @Test
    public void testMergeEventBackwardsCompatible() throws JSONException {
        rakam.setEventUploadThreshold(4);
        // eventst logged before v2.1.0 won't have a sequence number, should get priority
        long [] timestamps = {1, 1, 2, 3};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        rakam.uploadingCurrently.set(true);
        rakam.identify(new Identify().add("photo_count", 1));
        rakam.logEvent("test_event1");
        rakam.identify(new Identify().add("photo_count", 2));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        // need to delete sequence number from test event
        JSONObject event = getUnsentEvents(1).getJSONObject(0);
        assertEquals(event.getLong("event_id"), 1);
        event.remove("sequence_number");
        event.remove("event_id");
        // delete event from db and reinsert modified event
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        dbHelper.removeEvent(1);
        dbHelper.addEvent(event.toString());
        rakam.uploadingCurrently.set(false);

        // log another event to trigger upload
        rakam.logEvent("test_event2");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        // verify some internal counters
        assertEquals(getUnsentEventCount(), 2);
        assertEquals(rakam.getLastEventId(), 3);
        assertEquals(getUnsentIdentifyCount(), 2);
        assertEquals(rakam.getLastIdentifyId(), 2);

        JSONObject expectedIdentify1 = new JSONObject();
        expectedIdentify1.put(Constants.AMP_OP_ADD, new JSONObject().put("photo_count", 1));
        JSONObject expectedIdentify2 = new JSONObject();
        expectedIdentify2.put(Constants.AMP_OP_ADD, new JSONObject().put("photo_count", 2));

        // send response and check that merging events correctly ordered events
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 4);
        assertEquals(events.optJSONObject(0).optString("event_type"), "test_event1");
        assertFalse(events.optJSONObject(0).has("sequence_number"));
        assertEquals(events.optJSONObject(1).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(1).optLong("sequence_number"), 1);
        assertTrue(compareJSONObjects(
                events.optJSONObject(1).optJSONObject("user_properties"), expectedIdentify1
        ));
        assertEquals(events.optJSONObject(2).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(2).optLong("sequence_number"), 3);
        assertTrue(compareJSONObjects(
                events.optJSONObject(2).optJSONObject("user_properties"), expectedIdentify2
        ));
        assertEquals(events.optJSONObject(3).optString("event_type"), "test_event2");
        assertEquals(events.optJSONObject(3).optLong("sequence_number"), 4);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testRemoveAfterSuccessfulUpload() throws JSONException {
        long [] timestamps = new long[Constants.EVENT_UPLOAD_MAX_BATCH_SIZE + 4];
        for (int i = 0; i < timestamps.length; i++) timestamps[i] = i;
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        for (int i = 0; i < Constants.EVENT_UPLOAD_THRESHOLD; i++) {
            rakam.logEvent("test_event" + i);
        }
        rakam.identify(new Identify().add("photo_count", 1));
        rakam.identify(new Identify().add("photo_count", 2));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), Constants.EVENT_UPLOAD_THRESHOLD);
        assertEquals(getUnsentIdentifyCount(), 2);

        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        for (int i = 0; i < events.length(); i++) {
            assertEquals(events.optJSONObject(i).optString("event_type"), "test_event" + i);
        }

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 2); // should have 2 identifys left
    }

    @Test
    public void testLogEventHasUUID() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        rakam.logEvent("test_event");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        JSONObject event = getLastUnsentEvent();
        assertTrue(event.has("uuid"));
        assertNotNull(event.optString("uuid"));
        assertTrue(event.optString("uuid").length() > 0);
    }

    @Test
    public void testLogRevenue() {
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

        JSONObject event, apiProps;

        rakam.logRevenue(10.99);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

        event = getLastUnsentEvent();
        apiProps = event.optJSONObject("api_properties");
        assertEquals(Constants.AMP_REVENUE_EVENT, event.optString("event_type"));
        assertEquals(Constants.AMP_REVENUE_EVENT, apiProps.optString("special"));
        assertEquals(1, apiProps.optInt("quantity"));
        assertNull(apiProps.optString("productId", null));
        assertEquals(10.99, apiProps.optDouble("price"), .01);
        assertNull(apiProps.optString("receipt", null));
        assertNull(apiProps.optString("receiptSig", null));

        rakam.logRevenue("ID1", 2, 9.99);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

        event = getLastUnsentEvent();
        apiProps = event.optJSONObject("api_properties");;
        assertEquals(Constants.AMP_REVENUE_EVENT, event.optString("event_type"));
        assertEquals(Constants.AMP_REVENUE_EVENT, apiProps.optString("special"));
        assertEquals(2, apiProps.optInt("quantity"));
        assertEquals("ID1", apiProps.optString("productId"));
        assertEquals(9.99, apiProps.optDouble("price"), .01);
        assertNull(apiProps.optString("receipt", null));
        assertNull(apiProps.optString("receiptSig", null));

        rakam.logRevenue("ID2", 3, 8.99, "RECEIPT", "SIG");
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

        event = getLastUnsentEvent();
        apiProps = event.optJSONObject("api_properties");
        assertEquals(Constants.AMP_REVENUE_EVENT, event.optString("event_type"));
        assertEquals(Constants.AMP_REVENUE_EVENT, apiProps.optString("special"));
        assertEquals(3, apiProps.optInt("quantity"));
        assertEquals("ID2", apiProps.optString("productId"));
        assertEquals(8.99, apiProps.optDouble("price"), .01);
        assertEquals("RECEIPT", apiProps.optString("receipt"));
        assertEquals("SIG", apiProps.optString("receiptSig"));

        assertNotNull(runRequest(rakam));
    }

    @Test
    public void testLogRevenueV2() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        // ignore invalid revenue objects
        rakam.logRevenueV2(null);
        looper.runToEndOfTasks();
        rakam.logRevenueV2(new Revenue());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);

        // log valid revenue object
        double price = 10.99;
        int quantity = 15;
        String productId = "testProductId";
        String receipt = "testReceipt";
        String receiptSig = "testReceiptSig";
        String revenueType = "testRevenueType";
        JSONObject props = new JSONObject().put("city", "Boston");

        Revenue revenue = new Revenue().setProductId(productId).setPrice(price);
        revenue.setQuantity(quantity).setReceipt(receipt, receiptSig);
        revenue.setRevenueType(revenueType).setRevenueProperties(props);

        rakam.logRevenueV2(revenue);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("event_type"), "revenue_amount");

        JSONObject obj = event.optJSONObject("event_properties");
        assertEquals(obj.optDouble("$price"), price, 0);
        assertEquals(obj.optInt("$quantity"), 15);
        assertEquals(obj.optString("$productId"), productId);
        assertEquals(obj.optString("$receipt"), receipt);
        assertEquals(obj.optString("$receiptSig"), receiptSig);
        assertEquals(obj.optString("$revenueType"), revenueType);
        assertEquals(obj.optString("city"), "Boston");

        // user properties should be empty
        assertEquals(
            compareJSONObjects(event.optJSONObject("user_properties"), new JSONObject()), true
        );

        // api properties should not have any revenue info
        JSONObject apiProps = event.optJSONObject("api_properties");
        assertTrue(apiProps.length() > 0);
        assertFalse(apiProps.has("special"));
        assertFalse(apiProps.has("productId"));
        assertFalse(apiProps.has("quantity"));
        assertFalse(apiProps.has("price"));
        assertFalse(apiProps.has("receipt"));
        assertFalse(apiProps.has("receiptSig"));
    }

    @Test
    public void testLogEventSync() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        rakam.logEventSync("test_event_sync", null);

        // Event should be in the database synchronously.
        JSONObject event = getLastEvent();
        assertEquals("test_event_sync", event.optString("event_type"));

        looper.runToEndOfTasks();

        server.enqueue(new MockResponse().setBody("success"));
        ShadowLooper httplooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());
        httplooper.runToEndOfTasks();

        try {
            assertNotNull(server.takeRequest(1, SECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    /**
     * Test for not excepting on empty event properties.
     * See https://github.com/buremba/rakam-android/issues/35
     */
    @Test
    public void testEmptyEventProps() {
        RecordedRequest request = sendEvent(rakam, "test_event", new JSONObject());
        assertNotNull(request);
    }

    /**
     * Test that resend failed events only occurs every 30 events.
     */
    @Test
    public void testSaveEventLogic() {
        rakam.trackSessionEvents(true);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);

        for (int i = 0; i < Constants.EVENT_UPLOAD_THRESHOLD; i++) {
            rakam.logEvent("test");
        }
        looper.runToEndOfTasks();
        // unsent events will be threshold (+1 for start session)
        assertEquals(getUnsentEventCount(), Constants.EVENT_UPLOAD_THRESHOLD + 1);

        server.enqueue(new MockResponse().setBody("invalid_api_key"));
        server.enqueue(new MockResponse().setBody("bad_checksum"));
        ShadowLooper httpLooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());
        httpLooper.runToEndOfTasks();

        // no events sent, queue should be same size
        assertEquals(getUnsentEventCount(), Constants.EVENT_UPLOAD_THRESHOLD + 1);

        for (int i = 0; i < Constants.EVENT_UPLOAD_THRESHOLD; i++) {
            rakam.logEvent("test");
        }
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), Constants.EVENT_UPLOAD_THRESHOLD * 2 + 1);
        httpLooper.runToEndOfTasks();

        // sent 61 events, should have only made 2 requests
        assertEquals(server.getRequestCount(), 2);
    }

    @Test
    public void testRequestTooLargeBackoffLogic() {
        rakam.trackSessionEvents(true);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        // verify event queue empty
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);

        // 413 error force backoff with 2 events --> new upload limit will be 1
        rakam.logEvent("test");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2); // 2 events: start session + test
        server.enqueue(new MockResponse().setResponseCode(413));
        ShadowLooper httpLooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());
        httpLooper.runToEndOfTasks();

        // 413 error with upload limit 1 will remove the top (start session) event
        rakam.logEvent("test");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);
        server.enqueue(new MockResponse().setResponseCode(413));
        httpLooper.runToEndOfTasks();

        // verify only start session event removed
        assertEquals(getUnsentEventCount(), 2);
        JSONArray events = getUnsentEvents(2);
        assertEquals(events.optJSONObject(0).optString("event_type"), "test");
        assertEquals(events.optJSONObject(1).optString("event_type"), "test");

        // upload limit persists until event count below threshold
        server.enqueue(new MockResponse().setBody("success"));
        looper.runToEndOfTasks(); // retry uploading after removing large event
        httpLooper.runToEndOfTasks(); // send success --> 1 event sent
        looper.runToEndOfTasks(); // event count below threshold --> disable backoff
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        // verify backoff disabled - queue 2 more events, see that all get uploaded
        rakam.logEvent("test");
        rakam.logEvent("test");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);
        server.enqueue(new MockResponse().setBody("success"));
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
    }

    @Test
    public void testUploadRemainingEvents() {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);

        rakam.setEventUploadMaxBatchSize(2);
        rakam.setEventUploadThreshold(2);
        rakam.uploadingCurrently.set(true); // block uploading until we queue up enough events
        for (int i = 0; i < 6; i++) {
            rakam.logEvent(String.format("test%d", i));
            looper.runToEndOfTasks();
            looper.runToEndOfTasks();
            assertEquals(dbHelper.getTotalEventCount(), i+1);
        }
        rakam.uploadingCurrently.set(false);

        // allow event uploads
        // 7 events in queue, should upload 2, and then 2, and then 2, and then 2
        rakam.logEvent("test7");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(dbHelper.getEventCount(), 7);
        assertEquals(dbHelper.getIdentifyCount(), 0);
        assertEquals(dbHelper.getTotalEventCount(), 7);

        // server response
        server.enqueue(new MockResponse().setBody("success"));
        ShadowLooper httpLooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());
        httpLooper.runToEndOfTasks();

        // when receive success response, continue uploading
        looper.runToEndOfTasks();
        looper.runToEndOfTasks(); // remove uploaded events
        assertEquals(dbHelper.getEventCount(), 5);
        assertEquals(dbHelper.getIdentifyCount(), 0);
        assertEquals(dbHelper.getTotalEventCount(), 5);

        // 2nd server response
        server.enqueue(new MockResponse().setBody("success"));
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks(); // remove uploaded events
        assertEquals(dbHelper.getEventCount(), 3);
        assertEquals(dbHelper.getIdentifyCount(), 0);
        assertEquals(dbHelper.getTotalEventCount(), 3);

        // 3rd server response
        server.enqueue(new MockResponse().setBody("success"));
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks(); // remove uploaded events
        looper.runToEndOfTasks();
        assertEquals(dbHelper.getEventCount(), 1);
        assertEquals(dbHelper.getIdentifyCount(), 0);
        assertEquals(dbHelper.getTotalEventCount(), 1);
    }

    @Test
    public void testBackoffRemoveIdentify() {
        long [] timestamps = {1, 1, 2, 3, 4, 5};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);

        // 413 error force backoff with 2 events --> new upload limit will be 1
        rakam.identify(new Identify().add("photo_count", 1));
        rakam.logEvent("test1");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentIdentifyCount(), 1);
        assertEquals(getUnsentEventCount(), 1);

        server.enqueue(new MockResponse().setResponseCode(413));
        ShadowLooper httpLooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());
        httpLooper.runToEndOfTasks();

        // 413 error with upload limit 1 will remove the top identify
        rakam.logEvent("test2");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2);
        assertEquals(getUnsentIdentifyCount(), 1);
        server.enqueue(new MockResponse().setResponseCode(413));
        httpLooper.runToEndOfTasks();

        // verify only identify removed
        assertEquals(getUnsentEventCount(), 2);
        assertEquals(getUnsentIdentifyCount(), 0);
        JSONArray events = getUnsentEvents(2);
        assertEquals(events.optJSONObject(0).optString("event_type"), "test1");
        assertEquals(events.optJSONObject(1).optString("event_type"), "test2");
    }

    @Test
    public void testLimitTrackingEnabled() {
        rakam.logEvent("test");
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        JSONObject apiProperties = getLastUnsentEvent().optJSONObject("api_properties");
        assertTrue(apiProperties.has("limit_ad_tracking"));
        assertFalse(apiProperties.optBoolean("limit_ad_tracking"));
        assertFalse(apiProperties.has("androidADID"));
    }

    @Test
    public void testTruncateString() {
        String longString = generateStringWithLength(Constants.MAX_STRING_LENGTH * 2, 'c');
        assertEquals(longString.length(), Constants.MAX_STRING_LENGTH * 2);
        String truncatedString = rakam.truncate(longString);
        assertEquals(truncatedString.length(), Constants.MAX_STRING_LENGTH);
        assertEquals(truncatedString, generateStringWithLength(Constants.MAX_STRING_LENGTH, 'c'));
    }

    @Test
    public void testTruncateJSONObject() throws JSONException {
        String longString = generateStringWithLength(Constants.MAX_STRING_LENGTH * 2, 'c');
        String truncString = generateStringWithLength(Constants.MAX_STRING_LENGTH, 'c');
        JSONObject object = new JSONObject();
        object.put("int value", 10);
        object.put("bool value", false);
        object.put("long string", longString);
        object.put("array", new JSONArray().put(longString).put(10));
        object.put("jsonobject", new JSONObject().put("long string", longString));

        object = rakam.truncate(object);
        assertEquals(object.optInt("int value"), 10);
        assertEquals(object.optBoolean("bool value"), false);
        assertEquals(object.optString("long string"), truncString);
        assertEquals(object.optJSONArray("array").length(), 2);
        assertEquals(object.optJSONArray("array").getString(0), truncString);
        assertEquals(object.optJSONArray("array").getInt(1), 10);
        assertEquals(object.optJSONObject("jsonobject").length(), 1);
        assertEquals(object.optJSONObject("jsonobject").optString("long string"), truncString);
    }

    @Test
    public void testTruncateNullJSONObject() throws JSONException {
        assertNull(rakam.truncate((JSONObject) null));
        assertNull(rakam.truncate((JSONArray) null));
    }

    @Test
    public void testTruncateEventAndIdentify() throws JSONException {
        String longString = generateStringWithLength(Constants.MAX_STRING_LENGTH * 2, 'c');
        String truncString = generateStringWithLength(Constants.MAX_STRING_LENGTH, 'c');

        long [] timestamps = {1, 1, 2, 3};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        rakam.logEvent("test", new JSONObject().put("long_string", longString));
        rakam.identify(new Identify().set("long_string", longString));

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);

        assertEquals(events.optJSONObject(0).optString("event_type"), "test");
        assertTrue(compareJSONObjects(
                events.optJSONObject(0).optJSONObject("event_properties"),
                new JSONObject().put("long_string", truncString)
        ));
        assertEquals(events.optJSONObject(1).optString("event_type"), Constants.IDENTIFY_EVENT);
        assertTrue(compareJSONObjects(
                events.optJSONObject(1).optJSONObject("user_properties"),
                new JSONObject().put(Constants.AMP_OP_SET, new JSONObject().put("long_string", truncString))
        ));
    }

    @Test
    public void testAutoIncrementSequenceNumber() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        int limit = 10;
        for (int i = 0; i < limit; i++) {
            assertEquals(rakam.getNextSequenceNumber(), i+1);
            assertEquals(dbHelper.getLongValue(RakamClient.SEQUENCE_NUMBER_KEY), Long.valueOf(i+1));
        }
    }

    @Test
    public void testSetOffline() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        rakam.setOffline(true);

        rakam.logEvent("test1");
        rakam.logEvent("test2");
        rakam.identify(new Identify().unset("key1"));
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2);
        assertEquals(getUnsentIdentifyCount(), 1);

        rakam.setOffline(false);
        looper.runToEndOfTasks();
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        looper.runToEndOfTasks();

        assertEquals(events.length(), 3);
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testSetOfflineTruncate() throws JSONException {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        int eventMaxCount = 3;
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        rakam.setEventMaxCount(eventMaxCount).setOffline(true);

        rakam.logEvent("test1");
        rakam.logEvent("test2");
        rakam.logEvent("test3");
        rakam.identify(new Identify().unset("key1"));
        rakam.identify(new Identify().unset("key2"));
        rakam.identify(new Identify().unset("key3"));
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);
        assertEquals(getUnsentIdentifyCount(), eventMaxCount);

        rakam.logEvent("test4");
        rakam.identify(new Identify().unset("key4"));
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);
        assertEquals(getUnsentIdentifyCount(), eventMaxCount);

        List<JSONObject> events = dbHelper.getEvents(-1, -1);
        assertEquals(events.size(), eventMaxCount);
        assertEquals(events.get(0).optString("event_type"), "test2");
        assertEquals(events.get(1).optString("event_type"), "test3");
        assertEquals(events.get(2).optString("event_type"), "test4");

        List<JSONObject> identifys = dbHelper.getIdentifys(-1, -1);
        assertEquals(identifys.size(), eventMaxCount);
        assertEquals(identifys.get(0).optJSONObject("user_properties").optJSONObject("$unset").optString("key2"), "-");
        assertEquals(identifys.get(1).optJSONObject("user_properties").optJSONObject("$unset").optString("key3"), "-");
        assertEquals(identifys.get(2).optJSONObject("user_properties").optJSONObject("$unset").optString("key4"), "-");
    }

    @Test
    public void testTruncateEventsQueues() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        int eventMaxCount = 50;
        assertTrue(eventMaxCount > Constants.EVENT_REMOVE_BATCH_SIZE);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        rakam.setEventMaxCount(eventMaxCount).setOffline(true);

        for (int i = 0; i < eventMaxCount; i++) {
            rakam.logEvent("test");
        }
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);

        rakam.logEvent("test");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount - (eventMaxCount/10) + 1);
    }

    @Test
    public void testTruncateEventsQueuesWithOneEvent() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        int eventMaxCount = 1;
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        rakam.setEventMaxCount(eventMaxCount).setOffline(true);

        rakam.logEvent("test1");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);

        rakam.logEvent("test2");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);

        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("event_type"), "test2");
    }

    @Test
    public void testClearUserProperties() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        rakam.clearUserProperties();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 1);
        JSONObject event = getLastUnsentIdentify();
        assertEquals(Constants.IDENTIFY_EVENT, event.optString("event_type"));
        assertEquals(event.optJSONObject("event_properties").length(), 0);

        JSONObject userPropertiesOperations = event.optJSONObject("user_properties");
        assertEquals(userPropertiesOperations.length(), 1);
        assertTrue(userPropertiesOperations.has(Constants.AMP_OP_CLEAR_ALL));

        assertEquals(
            "-", userPropertiesOperations.optString(Constants.AMP_OP_CLEAR_ALL)
        );
    }

    @Test
    public void testSetGroup() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        rakam.setGroup("orgId", new JSONArray().put(10).put(15));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 1);
        JSONObject event = getLastUnsentIdentify();
        assertEquals(Constants.IDENTIFY_EVENT, event.optString("event_type"));
        assertEquals(event.optJSONObject("event_properties").length(), 0);

        JSONObject userPropertiesOperations = event.optJSONObject("user_properties");
        assertEquals(userPropertiesOperations.length(), 1);
        assertTrue(userPropertiesOperations.has(Constants.AMP_OP_SET));

        JSONObject groups = event.optJSONObject("groups");
        assertEquals(groups.length(), 1);
        assertEquals(groups.optJSONArray("orgId"), new JSONArray().put(10).put(15));

        JSONObject setOperations = userPropertiesOperations.optJSONObject(Constants.AMP_OP_SET);
        assertEquals(setOperations.length(), 1);
        assertEquals(setOperations.optJSONArray("orgId"), new JSONArray().put(10).put(15));
    }

    @Test
    public void testLogEventWithGroups() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        JSONObject groups = new JSONObject().put("orgId", 10).put("sport", "tennis");
        rakam.logEvent("test", null, groups);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), 1);
        assertEquals(getUnsentIdentifyCount(), 0);
        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("event_type"), "test");
        assertEquals(event.optJSONObject("event_properties").length(), 0);
        assertEquals(event.optJSONObject("user_properties").length(), 0);

        JSONObject eventGroups = event.optJSONObject("groups");
        assertEquals(eventGroups.length(), 2);
        assertEquals(eventGroups.optInt("orgId"), 10);
        assertEquals(eventGroups.optString("sport"), "tennis");
    }

    @Test
    public void testMergeEventsArrayIndexOutOfBounds() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        rakam.setOffline(true);

        rakam.logEvent("testEvent1");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        // force failure case
        rakam.setLastEventId(0);

        rakam.setOffline(false);
        looper.runToEndOfTasks();

        // make sure next upload succeeds
        rakam.setLastEventId(1);
        rakam.logEvent("testEvent2");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 2);

        assertEquals(events.getJSONObject(0).optString("event_type"), "testEvent1");
        assertEquals(events.getJSONObject(0).optLong("event_id"), 1);

        assertEquals(events.getJSONObject(1).optString("event_type"), "testEvent2");
        assertEquals(events.getJSONObject(1).optLong("event_id"), 2);
    }
}
