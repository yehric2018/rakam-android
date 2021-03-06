package io.rakam.api;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import static io.rakam.api.Constants.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
        rakam.initialize(context, server.url("/").url(), apiKey);
        Shadows.shadowOf(rakam.logThread.getLooper()).runOneTask();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testConstructor() {
        // verify that the constructor lowercases the instance name
        RakamClient a = new RakamClient("APP1");
        RakamClient b = new RakamClient("New_App_2");

        assertEquals(a.instanceName, "app1");
        assertEquals(b.instanceName, "new_app_2");
    }

    @Test
    public void testSetUserId() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        String userId = "_user";
        rakam.setUserId(userId);
        looper.runToEndOfTasks();
        assertEquals(userId, dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertEquals(userId, rakam.getUserId());

        // try setting to null
        rakam.setUserId(null);
        looper.runToEndOfTasks();
        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertNull(rakam.getUserId());
    }

    @Test
    public void testSetUserIdTwice() {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        String userId1 = "user_id1";
        String userId2 = "user_id2";

        rakam.setUserId(userId1);
        looper.runToEndOfTasks();
        assertEquals(rakam.getUserId(), userId1);
        rakam.logEvent("event1");
        looper.runToEndOfTasks();

        JSONObject event1 = getLastUnsentEvent();
        assertEquals(event1.optString("collection"), "event1");
        assertEquals(event1.optJSONObject("properties").optString("_user"), userId1);

        rakam.setUserId(userId2);
        looper.runToEndOfTasks();
        assertEquals(rakam.getUserId(), userId2);
        rakam.logEvent("event2");
        looper.runToEndOfTasks();

        JSONObject event2 = getLastUnsentEvent();
        assertEquals(event2.optString("collection"), "event2");
        assertEquals(event2.optJSONObject("properties").optString("_user"), userId2);
    }

    @Test
    public void testSetDeviceId() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        SharedPreferences prefs = Utils.getRakamSharedPreferences(context, rakam.instanceName);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        String deviceId = rakam.getDeviceId(); // Randomly generated device ID
        assertNotNull(deviceId);
        assertEquals(deviceId.length(), 36 + 1); // 36 for UUID, + 1 for appended R
        assertEquals(deviceId.charAt(36), 'R');
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);


        // test setting invalid device ids
        rakam.setDeviceId(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        rakam.setDeviceId("");
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        rakam.setDeviceId("9774d56d682e549c");
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        rakam.setDeviceId("unknown");
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        rakam.setDeviceId("000000000000000");
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        rakam.setDeviceId("Android");
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        rakam.setDeviceId("DEFACE");
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        rakam.setDeviceId("00000000-0000-0000-0000-000000000000");
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), deviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), deviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), deviceId);

        // set valid device id
        String newDeviceId = UUID.randomUUID().toString();
        rakam.setDeviceId(newDeviceId);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(rakam.getDeviceId(), newDeviceId);
        assertEquals(dbHelper.getValue(rakam.DEVICE_ID_KEY), newDeviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), newDeviceId);

        rakam.logEvent("test");
        looper.runToEndOfTasks();
        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), "test");
        assertEquals(event.optJSONObject("properties").optString("_device_id"), newDeviceId);
        assertEquals(prefs.getString(rakam.DEVICE_ID_KEY, null), newDeviceId);
    }

    @Test
    public void testSetUserProperties() throws JSONException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

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
        assertEquals(Constants.IDENTIFY_EVENT, event.optString("collection"));

        JSONObject userPropertiesOperations = event.optJSONObject("properties");
        assertTrue(userPropertiesOperations.has(AMP_OP_SET));

        JSONObject setOperations = userPropertiesOperations.optJSONObject(AMP_OP_SET);
        assertTrue(Utils.compareJSONObjects(userProperties, setOperations));
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
        Shadows.shadowOf(rakam.logThread.getLooper()).runToEndOfTasks();
        assertEquals(getUnsentIdentifyCount(), 1);
        assertEquals(getUnsentEventCount(), 0);
        JSONObject event = getLastUnsentIdentify();
        assertEquals(Constants.IDENTIFY_EVENT, event.optString("collection"));

        JSONObject userProperties = event.optJSONObject("properties");
        JSONObject expected = new JSONObject();
        assertTrue(Utils.compareJSONObjects(userProperties.getJSONObject(AMP_OP_SET_ONCE), new JSONObject().put(property1, value1)));
        assertTrue(Utils.compareJSONObjects(userProperties.getJSONObject(AMP_OP_ADD), new JSONObject().put(property2, value2)));
        assertTrue(Utils.compareJSONObjects(userProperties.getJSONObject(AMP_OP_SET), new JSONObject().put(property3, value3)));
        assertTrue(Utils.compareJSONObjects(userProperties.getJSONObject(AMP_OP_UNSET), new JSONObject().put(property4, true)));
    }

    @Test
    public void testOptOut() {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        ShadowLooper httplooper = Shadows.shadowOf(rakam.httpThread.getLooper());

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertFalse(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 0L);

        rakam.setOptOut(true);
        looper.runToEndOfTasks();
        assertTrue(rakam.isOptedOut());
        assertEquals((long) dbHelper.getLongValue(RakamClient.OPT_OUT_KEY), 1L);
        RecordedRequest request = sendEvent(rakam, "test_opt_out", null);
        assertNull(request);

        // Event shouldn't be sent event once opt out is turned off.
        rakam.setOptOut(false);
        looper.runToEndOfTasks();
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
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        ShadowLooper httplooper = Shadows.shadowOf(rakam.httpThread.getLooper());

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
        assertEquals(identify.getString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(identify.getLong("event_id"), 1);
        assertEquals(identify.optJSONObject("properties").getLong("_time"), timestamps[0]);

        JSONObject userProperties = identify.getJSONObject("properties");
        assertTrue(userProperties.has(AMP_OP_SET));

        JSONObject expected = new JSONObject();
        expected.put("key", "value");
        assertTrue(Utils.compareJSONObjects(userProperties.getJSONObject(AMP_OP_SET), expected));

        // verify db state
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertNull(dbHelper.getValue(RakamClient.USER_ID_KEY));
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_IDENTIFY_ID_KEY), 1L);
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_EVENT_ID_KEY), -1L);
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_EVENT_TIME_KEY), timestamps[0]);
    }

    @Test
    public void testNullIdentify() {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
    public void testLog3Events() {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
            assertEquals(events.optJSONObject(i).optString("collection"), "test_event" + (i+1));
            assertEquals(events.optJSONObject(i).optJSONObject("properties").optLong("_time"), timestamps[i]);
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

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        expectedIdentify1.put(AMP_OP_SET, new JSONObject().put("photo_count", 1));
        JSONObject expectedIdentify2 = new JSONObject();
        expectedIdentify2.put(AMP_OP_ADD, new JSONObject().put("karma", 2));
        JSONObject expectedIdentify3 = new JSONObject();
        expectedIdentify3.put(Constants.AMP_OP_UNSET, new JSONObject().put("gender", true));

        assertEquals(events.optJSONObject(0).optString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(0).optJSONObject("properties").optLong("_time"), timestamps[0]);
        assertEquals(events.optJSONObject(1).optString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(1).optJSONObject("properties").optLong("_time"), timestamps[1]);

        // TODO test content
        assertEquals(events.optJSONObject(2).optString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(2).optJSONObject("properties").optLong("_time"), timestamps[2]);

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

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        rakam.logEvent("test_event");
        rakam.identify(new Identify().add("photo_count", 1));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        // verify some internal counters
        assertEquals(getUnsentEventCount(), 1);
        assertEquals(rakam.lastEventId, 1);
        assertEquals(getUnsentIdentifyCount(), 1);
        assertEquals(rakam.lastIdentifyId, 1);

        JSONArray unsentEvents = getUnsentEvents(1);
        assertEquals(unsentEvents.optJSONObject(0).optString("collection"), "test_event");

        JSONObject expectedIdentify = new JSONObject();
        expectedIdentify.put(AMP_OP_ADD, new JSONObject().put("photo_count", 1));

        JSONArray unsentIdentifys = getUnsentIdentifys(1);
        assertEquals(unsentIdentifys.optJSONObject(0).optString("collection"), Constants.IDENTIFY_EVENT);

        // TODO test content

        // send response and check that remove events works properly
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 2);
        assertEquals(events.optJSONObject(0).optString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(events.optJSONObject(1).optString("collection"), "test_event");
//        assertTrue(Utils.compareJSONObjects(
//            events.optJSONObject(1).optJSONObject("properties"), expectedIdentify
//        ));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    // The ordering doesn't matter for us.
    @Ignore
    @Test
    public void testMergeEventsAndIdentifys() throws JSONException {
        long [] timestamps = {1, 2, 3, 4, 5, 5, 6, 7, 8, 9, 10};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        assertEquals(rakam.lastEventId, 4);
        assertEquals(getUnsentIdentifyCount(), 3);
        assertEquals(rakam.lastIdentifyId, 3);

        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 7);

        JSONObject expectedIdentify1 = new JSONObject();
        expectedIdentify1.put(AMP_OP_ADD, new JSONObject().put("photo_count", 1));
        JSONObject expectedIdentify2 = new JSONObject();
        expectedIdentify2.put(AMP_OP_SET, new JSONObject().put("gender", "male"));
        JSONObject expectedIdentify3 = new JSONObject();
        expectedIdentify3.put(Constants.AMP_OP_UNSET, new JSONObject().put("karma", true));

        assertEquals(events.getJSONObject(0).getString("collection"), "test_event1");
        assertEquals(events.getJSONObject(0).getLong("event_id"), 1);
        assertEquals(events.getJSONObject(0).getLong("_time"), timestamps[0]);

        assertEquals(events.getJSONObject(1).getString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(events.getJSONObject(1).getLong("event_id"), 1);
        assertEquals(events.getJSONObject(1).optJSONObject("properties").getLong("_time"), timestamps[1]);
        assertTrue(Utils.compareJSONObjects(
                events.getJSONObject(1).getJSONObject("properties"), expectedIdentify1
        ));

        assertEquals(events.getJSONObject(2).getString("collection"), "test_event2");
        assertEquals(events.getJSONObject(2).getLong("event_id"), 2);
        assertEquals(events.getJSONObject(2).getLong("timestamp"), timestamps[2]);
        assertEquals(events.getJSONObject(2).getLong("sequence_number"), 3);

        assertEquals(events.getJSONObject(3).getString("collection"), "test_event3");
        assertEquals(events.getJSONObject(3).getLong("event_id"), 3);
        assertEquals(events.getJSONObject(3).optJSONObject("properties").getLong("_time"), timestamps[3]);

        assertEquals(events.getJSONObject(4).getString("collection"), "test_event4");
        assertEquals(events.getJSONObject(4).getLong("event_id"), 4);
        assertEquals(events.getJSONObject(4).optJSONObject("properties").getLong("_time"), timestamps[4]);

        assertEquals(events.getJSONObject(5).getString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(events.getJSONObject(5).getLong("event_id"), 2);
        assertEquals(events.getJSONObject(5).optJSONObject("properties").getLong("_time"), timestamps[5]);
        assertTrue(Utils.compareJSONObjects(
                events.getJSONObject(5).getJSONObject("properties"), expectedIdentify2
        ));

        assertEquals(events.getJSONObject(6).getString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(events.getJSONObject(6).getLong("event_id"), 3);
        assertEquals(events.getJSONObject(6).optJSONObject("properties").getLong("_time"), timestamps[6]);
        assertTrue(Utils.compareJSONObjects(
                events.getJSONObject(6).getJSONObject("properties"), expectedIdentify3
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
        assertEquals((long)dbHelper.getLongValue(RakamClient.LAST_EVENT_TIME_KEY), timestamps[6]);
    }

    // The ordering doesn't matter for us.
    @Ignore
    @Test
    public void testMergeEventBackwardsCompatible() throws JSONException {
        rakam.setEventUploadThreshold(4);
        // eventst logged before v2.1.0 won't have a sequence number, should get priority
        long [] timestamps = {1, 1, 2, 3};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        assertEquals(rakam.lastEventId, 3);
        assertEquals(getUnsentIdentifyCount(), 2);
        assertEquals(rakam.lastIdentifyId, 2);

        JSONObject expectedIdentify1 = new JSONObject();
        expectedIdentify1.put(AMP_OP_ADD, new JSONObject().put("photo_count", 1));
        JSONObject expectedIdentify2 = new JSONObject();
        expectedIdentify2.put(AMP_OP_ADD, new JSONObject().put("photo_count", 2));

        // send response and check that merging events correctly ordered events
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 4);
        assertEquals(events.optJSONObject(0).optString("collection"), "test_event1");
        assertEquals(events.optJSONObject(1).optString("collection"), Constants.IDENTIFY_EVENT);
        assertTrue(Utils.compareJSONObjects(
                events.optJSONObject(1).optJSONObject("properties"), expectedIdentify1
        ));
        assertEquals(events.optJSONObject(2).optString("collection"), Constants.IDENTIFY_EVENT);
        assertTrue(Utils.compareJSONObjects(
                events.optJSONObject(2).optJSONObject("properties"), expectedIdentify2
        ));
        assertEquals(events.optJSONObject(3).optString("collection"), "test_event2");
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

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
            assertEquals(events.optJSONObject(i).optString("collection"), "test_event" + i);
        }

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 2); // should have 2 identifys left
    }

    @Test
    public void testLogEventHasUUID() {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        rakam.logEvent("test_event");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        JSONObject event = getLastUnsentEvent();
        String id = event.optJSONObject("properties").optString("_id");
        assertNotNull(id);
        assertTrue(id.length() > 0);
    }

    @Test
    public void testLogRevenue() throws JSONException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        // ignore invalid revenue objects
        rakam.logRevenue(null);
        looper.runToEndOfTasks();
        rakam.logRevenue(new Revenue());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);

        // log valid revenue object
        double price = 10.99;
        int quantity = 15;
        String productId = "testProductId";
        String receipt = "testReceipt";
        String receiptSig = "testReceiptSig";
        String revenueType = "testRevenueType";
        JSONObject props = new JSONObject().put("ahmet", "Mehmet");

        Revenue revenue = new Revenue().setProductId(productId).setPrice(price);
        revenue.setQuantity(quantity).setReceipt(receipt, receiptSig);
        revenue.setRevenueType(revenueType).setEventProperties(props);

        rakam.logRevenue(revenue);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), "_revenue");

        JSONObject obj = event.optJSONObject("properties");
        assertEquals(obj.optDouble("_price"), price, 0);
        assertEquals(obj.optInt("_quantity"), 15);
        assertEquals(obj.optString("_product_id"), productId);
        assertEquals(obj.optString("_receipt"), receipt);
        assertEquals(obj.optString("_receipt_sig"), receiptSig);
        assertEquals(obj.optString("_revenue_type"), revenueType);
        assertEquals(obj.optString("ahmet"), "Mehmet");
    }

    @Test
    public void testLogEventSync() {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        rakam.logEventSync("test_event_sync", null);

        // Event should be in the database synchronously.
        JSONObject event = getLastEvent();
        assertEquals("test_event_sync", event.optString("collection"));

        looper.runToEndOfTasks();

        server.enqueue(new MockResponse().setBody("1"));
        ShadowLooper httplooper = Shadows.shadowOf(rakam.httpThread.getLooper());
        httplooper.runToEndOfTasks();

        try {
            assertNotNull(server.takeRequest(1, SECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    /**
     * Test for not excepting on empty event properties.
     * See https://github.com/rakam/Rakam-Android/issues/35
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
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);

        for (int i = 0; i < Constants.EVENT_UPLOAD_THRESHOLD; i++) {
            rakam.logEvent("test");
        }
        looper.runToEndOfTasks();
        // unsent events will be threshold (+1 for start session)
        assertEquals(getUnsentEventCount(), Constants.EVENT_UPLOAD_THRESHOLD + 1);

        server.enqueue(new MockResponse().setResponseCode(403));
        server.enqueue(new MockResponse().setBody("bad_checksum"));
        ShadowLooper httpLooper = Shadows.shadowOf(rakam.httpThread.getLooper());
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
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);

        // 413 error force backoff with 2 events --> new upload limit will be 1
        rakam.logEvent("test");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2); // 2 events: start session + test
        server.enqueue(new MockResponse().setResponseCode(413));
        ShadowLooper httpLooper = Shadows.shadowOf(rakam.httpThread.getLooper());
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
        assertEquals(events.optJSONObject(0).optString("collection"), "test");
        assertEquals(events.optJSONObject(1).optString("collection"), "test");

        // upload limit persists until event count below threshold
        server.enqueue(new MockResponse().setBody("1"));
        looper.runToEndOfTasks(); // retry uploading after removing large event
        httpLooper.runToEndOfTasks(); // send success --> 1 event sent
        looper.runToEndOfTasks(); // event count below threshold --> disable backoff
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        // verify backoff disabled - queue 2 more events, see that all get uploaded
        rakam.logEvent("test");
        rakam.logEvent("test");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);
        server.enqueue(new MockResponse().setBody("1"));
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks();
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
    }

    @Test
    public void testUploadRemainingEvents() {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        server.enqueue(new MockResponse().setBody("1"));
        ShadowLooper httpLooper = Shadows.shadowOf(rakam.httpThread.getLooper());
        httpLooper.runToEndOfTasks();

        // when receive success response, continue uploading
        looper.runToEndOfTasks();
        looper.runToEndOfTasks(); // remove uploaded events
        assertEquals(dbHelper.getEventCount(), 5);
        assertEquals(dbHelper.getIdentifyCount(), 0);
        assertEquals(dbHelper.getTotalEventCount(), 5);

        // 2nd server response
        server.enqueue(new MockResponse().setBody("1"));
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks(); // remove uploaded events
        assertEquals(dbHelper.getEventCount(), 3);
        assertEquals(dbHelper.getIdentifyCount(), 0);
        assertEquals(dbHelper.getTotalEventCount(), 3);

        // 3rd server response
        server.enqueue(new MockResponse().setBody("1"));
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

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        ShadowLooper httpLooper = Shadows.shadowOf(rakam.httpThread.getLooper());
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
        assertEquals(events.optJSONObject(0).optString("collection"), "test1");
        assertEquals(events.optJSONObject(1).optString("collection"), "test2");
    }

    @Test
    public void testLimitTrackingEnabled() {
        rakam.logEvent("test");
        Shadows.shadowOf(rakam.logThread.getLooper()).runToEndOfTasks();
        JSONObject apiProperties = getLastUnsentEvent().optJSONObject("properties");
        assertTrue(apiProperties.has("_limit_ad_tracking"));
        assertFalse(apiProperties.optBoolean("_limit_ad_tracking"));
        assertFalse(apiProperties.has("_android_adid"));
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
        object.put(Constants.AMP_REVENUE_RECEIPT, longString);
        object.put(Constants.AMP_REVENUE_RECEIPT_SIG, longString);

        object = rakam.truncate(object);
        assertEquals(object.optInt("int value"), 10);
        assertEquals(object.optBoolean("bool value"), false);
        assertEquals(object.optString("long string"), truncString);
        assertEquals(object.optJSONArray("array").length(), 2);
        assertEquals(object.optJSONArray("array").getString(0), truncString);
        assertEquals(object.optJSONArray("array").getInt(1), 10);
        assertEquals(object.optJSONObject("jsonobject").length(), 1);
        assertEquals(object.optJSONObject("jsonobject").optString("long string"), truncString);

        // receipt and receipt sig should not be truncated
//        assertEquals(object.optString(Constants.AMP_REVENUE_RECEIPT), longString);
//        assertEquals(object.optString(Constants.AMP_REVENUE_RECEIPT_SIG), longString);
    }

    @Test
    public void testTruncateNullJSONObject() throws JSONException {
        assertTrue(Utils.compareJSONObjects(
            rakam.truncate((JSONObject) null), new JSONObject()
        ));
        assertEquals(rakam.truncate((JSONArray) null).length(), 0);
    }

    @Test
    public void testTruncateEventAndIdentify() throws JSONException {
        String longString = generateStringWithLength(Constants.MAX_STRING_LENGTH * 2, 'c');
        String truncString = generateStringWithLength(Constants.MAX_STRING_LENGTH, 'c');

        long [] timestamps = {1, 1, 2, 3};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        rakam.logEvent("test", new JSONObject().put("long_string", longString));
        rakam.identify(new Identify().set("long_string", longString));

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);

        assertEquals(events.optJSONObject(1).optString("collection"), "test");
        assertEquals(
                events.optJSONObject(1).optJSONObject("properties").optString("long_string"),
                truncString
        );
        assertEquals(events.optJSONObject(0).optString("collection"), Constants.IDENTIFY_EVENT);
        assertEquals(
                events.optJSONObject(0).optJSONObject("properties").optJSONObject(AMP_OP_SET).optString("long_string"),
                truncString
        );
    }

    @Test
    public void testSetOffline() throws JSONException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        assertEquals(events.get(0).optString("collection"), "test2");
        assertEquals(events.get(1).optString("collection"), "test3");
        assertEquals(events.get(2).optString("collection"), "test4");

        List<JSONObject> identifys = dbHelper.getIdentifys(-1, -1);
        assertEquals(identifys.size(), eventMaxCount);
        assertEquals(identifys.get(0).optJSONObject("properties").optJSONObject("$unset").optBoolean("key2"), true);
        assertEquals(identifys.get(1).optJSONObject("properties").optJSONObject("$unset").optBoolean("key3"), true);
        assertEquals(identifys.get(2).optJSONObject("properties").optJSONObject("$unset").optBoolean("key4"), true);
    }

    @Test
    public void testTruncateEventsQueues() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        int eventMaxCount = 50;
        assertTrue(eventMaxCount > Constants.EVENT_REMOVE_BATCH_SIZE);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
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
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        rakam.setEventMaxCount(eventMaxCount).setOffline(true);

        rakam.logEvent("test1");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);

        rakam.logEvent("test2");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);

        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), "test2");
    }

    @Test
    public void testClearUserProperties() {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

        rakam.clearUserProperties();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 1);
        JSONObject event = getLastUnsentIdentify();
        assertEquals(Constants.IDENTIFY_EVENT, event.optString("collection"));
        JSONObject properties = event.optJSONObject("properties");
        assertTrue(properties.optBoolean(Constants.AMP_OP_CLEAR_ALL));

        assertTrue(properties.has(Constants.AMP_OP_CLEAR_ALL));

        assertEquals(
            true, properties.optBoolean(Constants.AMP_OP_CLEAR_ALL)
        );
    }

    @Test
    public void testMergeEventsArrayIndexOutOfBounds() throws JSONException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

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

        assertEquals(events.getJSONObject(0).optString("collection"), "testEvent1");
        assertEquals(events.getJSONObject(0).optLong("event_id"), 1);

        assertEquals(events.getJSONObject(1).optString("collection"), "testEvent2");
        assertEquals(events.getJSONObject(1).optLong("event_id"), 2);
    }

    @Test
    public void testCursorWindowAllocationException() {
        Robolectric.getForegroundThreadScheduler().advanceTo(1);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

        // log an event successfully
        rakam.logEvent("testEvent1");
        assertEquals(getUnsentEventCount(), 1);
        assertEquals(getUnsentIdentifyCount(), 0);

        // mock out database helper to force CursorWindowAllocationExceptions
        DatabaseHelper dbHelper = rakam.dbHelper;
        rakam.dbHelper = new MockDatabaseHelper(context);

        // force an upload and verify no request sent
        // make sure we catch it during sending of events and defer sending
        RecordedRequest request = runRequest(rakam);

        looper.runToEndOfTasks();

        assertNull(request);
        assertEquals(getUnsentEventCount(), 1);
        assertEquals(getUnsentIdentifyCount(), 0);

        // make sure we catch it during initialization and treat as uninitialized
        rakam.initialized = false;
        rakam.initialize(context, server.url("/").url(), apiKey);

        looper.runToEndOfTasks();

        // since event meta data is loaded during initialize, in theory we should
        // be able to log an event even if we can't query from it
        rakam.context = context;
        rakam.apiKey = apiKey;
        Identify identify = new Identify().set("car", "blue");
        rakam.identify(identify);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);
        assertEquals(getUnsentIdentifyCount(), 1);
    }

    @Test
    public void testBlockTooManyEventUserProperties() throws JSONException {
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

        JSONObject eventProperties = new JSONObject();
        JSONObject userProperties = new JSONObject();
        Identify identify = new Identify();

        for (int i = 0; i < Constants.MAX_PROPERTY_KEYS + 1; i++) {
            eventProperties.put(String.valueOf(i), i);
            userProperties.put(String.valueOf(i*2), i*2);
            identify.setOnce(String.valueOf(i), i);
        }

        // verify user properties is filtered out
        rakam.setUserProperties(userProperties);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentIdentifyCount(), 0);

        // verify scrubbed from events
        rakam.logEvent("test event", eventProperties);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);
        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), "test event");
        assertTrue(Utils.compareJSONObjects(
            event.optJSONObject("properties"), new JSONObject()
        ));
    }

    @Test
    public void testRegenerateDeviceId() {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        String oldDeviceId = rakam.getDeviceId();
        assertEquals(oldDeviceId, dbHelper.getValue("device_id"));

        rakam.regenerateDeviceId();
        Shadows.shadowOf(rakam.logThread.getLooper()).runToEndOfTasks();
        String newDeviceId = rakam.getDeviceId();
        assertNotEquals(oldDeviceId, newDeviceId);
        assertEquals(newDeviceId, dbHelper.getValue("device_id"));
        assertTrue(newDeviceId.endsWith("R"));
    }

    @Test
    public void testSendNullEvents() throws JSONException {
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());

        dbHelper.addEvent(null);
        rakam.setLastEventId(1);
        assertEquals(getUnsentEventCount(), 1);

        rakam.logEvent("test event");
        looper.runToEndOfTasks();

        rakam.updateServer();
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        assertEquals(events.length(), 1);
        assertEquals(events.optJSONObject(0).optString("collection"), "test event");
    }

    @Test
    @PrepareForTest(OkHttpClient.class)
    public void testHandleUploadExceptions() throws Exception {
        ShadowLooper logLooper = Shadows.shadowOf(rakam.logThread.getLooper());
        ShadowLooper httpLooper = Shadows.shadowOf(rakam.httpThread.getLooper());
        IOException error = new IOException("test IO Exception");

        // mock out client
        OkHttpClient oldClient = rakam.httpClient;
        OkHttpClient mockClient = PowerMockito.mock(OkHttpClient.class);

        // need to have mock client return mock call that throws exception
        Call mockCall = PowerMockito.mock(Call.class);
        PowerMockito.when(mockCall.execute()).thenThrow(error);
        PowerMockito.when(mockClient.newCall(Matchers.any(Request.class))).thenReturn(mockCall);

        // attach mock client to rakam
        rakam.httpClient = mockClient;
        rakam.logEvent("test event");
        logLooper.runToEndOfTasks();
        logLooper.runToEndOfTasks();
        httpLooper.runToEndOfTasks();

        assertEquals(rakam.lastError, error);

        // restore old client
        rakam.httpClient = oldClient;
    }

    @Test
    public void testDefaultPlatform() {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        assertEquals(rakam.platform, Constants.PLATFORM);

        rakam.logEvent("test_event1");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), 1);
        assertEquals(getUnsentIdentifyCount(), 0);
        JSONArray events = getUnsentEvents(1);
        for (int i = 0; i < 1; i++) {
            assertEquals(events.optJSONObject(i).optString("collection"), "test_event" + (i+1));
            assertEquals(events.optJSONObject(i).optJSONObject("properties").optLong("_time"), timestamps[i]);
            assertEquals(events.optJSONObject(i).optJSONObject("properties").optString("_platform"), Constants.PLATFORM);
        }
        runRequest(rakam);
    }

    @Test
    public void testOverridePlatform() {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        String customPlatform = "test_custom_platform";

        // force re-initialize to override platform
        rakam.initialized = false;
        rakam.initialize(context, server.url("/").url(), apiKey, null, customPlatform, false);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(rakam.platform, customPlatform);

        rakam.logEvent("test_event1");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), 1);
        assertEquals(getUnsentIdentifyCount(), 0);
        JSONArray events = getUnsentEvents(1);
        for (int i = 0; i < 1; i++) {
            assertEquals(events.optJSONObject(i).optString("collection"), "test_event" + (i+1));
            assertEquals(events.optJSONObject(i).optJSONObject("properties").optLong("_time"), timestamps[i]);
            assertEquals(events.optJSONObject(i).optJSONObject("properties").optString("_platform"), customPlatform);
        }
        runRequest(rakam);
    }

    @Test
    public void testSetTrackingConfig() throws JSONException {
        long [] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = Shadows.shadowOf(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        TrackingOptions options = new TrackingOptions().disableCity().disableCountry().disableIpAddress().disableLanguage().disableLatLng();
        rakam.setTrackingOptions(options);

        assertEquals(rakam.trackingOptions, options);
        assertTrue(Utils.compareJSONObjects(rakam.apiPropertiesTrackingOptions, options.getApiPropertiesTrackingOptions()));
        assertFalse(rakam.trackingOptions.shouldTrackCity());
        assertFalse(rakam.trackingOptions.shouldTrackCountry());
        assertFalse(rakam.trackingOptions.shouldTrackIpAddress());
        assertFalse(rakam.trackingOptions.shouldTrackLanguage());
        assertFalse(rakam.trackingOptions.shouldTrackLatLng());

        rakam.logEvent("test event");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        JSONArray events = getUnsentEvents(1);
        assertEquals(events.length(), 1);
        JSONObject event = events.getJSONObject(0).getJSONObject("properties");

        // verify we do have platform and carrier since those were not filtered out
        assertTrue(event.has("_carrier"));
        assertTrue(event.has("_platform"));

        // verify we do not have any of the filtered out fields
        assertFalse(event.has("_city"));
        assertFalse(event.has("_country_code"));
        assertFalse(event.has("_language"));

        assertFalse(event.getBoolean("_limit_ad_tracking"));
        assertFalse(event.getBoolean("_gps_enabled"));
    }
}
