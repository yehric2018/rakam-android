package io.rakam.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowLooper;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import static io.rakam.api.RakamClient.SUPER_PROPERTIES_KEY;
import static io.rakam.api.RakamClient.USER_ID_KEY;
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
        char[] array = new char[length];
        Arrays.fill(array, c);
        return new String(array);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        rakam.initialize(context, new URL(rakam.getApiUrl()), apiKey);
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
        assertEquals(userId, dbHelper.getValue(USER_ID_KEY));
        assertEquals(userId, rakam.getUserId());

        // try setting to null
        rakam.setUserId(null);
        assertNull(dbHelper.getValue(USER_ID_KEY));
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
        assertEquals(event1.optString("collection"), "event1");
        assertEquals(event1.optJSONObject("properties").optString("_user"), userId1);

        rakam.setUserId(userId2);
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
        assertEquals(event.optString("collection"), "test");
        assertEquals(event.optJSONObject("properties").optString("_device_id"), newDeviceId);
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
        JSONObject userPropertiesOperations = getLastUnsentIdentify();

        assertTrue(userPropertiesOperations.has(Constants.OP_SET));

        JSONObject setOperations = userPropertiesOperations.optJSONObject(Constants.OP_SET);
        assertTrue(compareJSONObjects(userProperties, setOperations));
    }

    @Test
    public void testIdentifyMultipleOperations() throws JSONException {
        String property1 = "string value";
        String value1 = "testValue";

        String property3 = "boolean value";
        boolean value3 = true;

        String property4 = "json value";

        Identify identify = new Identify().setOnce(property1, value1).set(property3, value3).unset(property4);

        // identify should ignore this since duplicate key
        identify.set(property4, value3);

        clock.setTimestamps(new long[]{1});
        rakam.identify(identify);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(getUnsentIdentifyCount(), 1);
        assertEquals(getUnsentEventCount(), 0);
        JSONObject userProperties = getLastUnsentIdentify();

        JSONObject expected = new JSONObject();
        expected.put(Constants.OP_SET_ONCE, new JSONObject().put(property1, value1));
        expected.put(Constants.OP_SET, new JSONObject().put(property3, value3));
        expected.put(Constants.OP_UNSET, new JSONArray().put(property4));
        expected.put("event_id", 1L);
        expected.put("time", 1);
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
        long[] timestamps = {1000, 1001};
        clock.setTimestamps(timestamps);

        RecordedRequest request = sendIdentify(rakam, new Identify().set("key", "value"));
        assertNotNull(request);
        JSONObject userProperties = getUserPropertiesFromRequest(request).optJSONObject(0);
        assertTrue(userProperties.has(Constants.OP_SET));

        JSONObject expected = new JSONObject();
        expected.put("key", "value");
        assertTrue(compareJSONObjects(userProperties.getJSONObject(Constants.OP_SET), expected));

        // verify db state
        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertNull(dbHelper.getValue(USER_ID_KEY));
        assertEquals((long) dbHelper.getLongValue(RakamClient.LAST_IDENTIFY_ID_KEY), 1L);
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
        long[] timestamps = {1, 2, 3, 4, 5, 6, 7};
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
            assertEquals(events.optJSONObject(i).optString("collection"), "test_event" + (i + 1));
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
        long[] timestamps = {1, 2, 3, 4, 5, 6, 7};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        rakam.identify(new Identify().set("photo_count", 1));
        rakam.identify(new Identify().add("karma", 2));
        rakam.identify(new Identify().unset("gender"));
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 3);
        JSONArray events = getUnsentIdentifys(3);

        assertTrue(compareJSONObjects(
                events.optJSONObject(0).optJSONObject(Constants.OP_SET),  new JSONObject().put("photo_count", 1)
        ));
        assertTrue(compareJSONObjects(
                events.optJSONObject(1).optJSONObject(Constants.OP_INCREMENT), new JSONObject().put("karma", 2)
        ));
        assertEquals(events.optJSONObject(2).optJSONArray(Constants.OP_UNSET).getString(0),
                "gender");

        // send response and check that remove events works properly
        runRequest(rakam);
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testRemoveAfterSuccessfulUpload() throws JSONException {
        long[] timestamps = new long[Constants.EVENT_UPLOAD_MAX_BATCH_SIZE + 4];
        for (int i = 0; i < timestamps.length; i++) timestamps[i] = i;
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        for (int i = 0; i < Constants.EVENT_UPLOAD_THRESHOLD; i++) {
            rakam.logEvent("test_event" + i);
        }
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();

        assertEquals(getUnsentEventCount(), Constants.EVENT_UPLOAD_THRESHOLD);

        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);
        for (int i = 0; i < events.length(); i++) {
            assertEquals(events.optJSONObject(i).optString("collection"), "test_event" + i);
        }

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
    }

    @Test
    public void testLogRevenueV2() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
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
        JSONObject props = new JSONObject().put("city", "Boston");

        Revenue revenue = new Revenue().setProductId(productId).setPrice(price);
        revenue.setQuantity(quantity).setReceipt(receipt, receiptSig);
        revenue.setRevenueType(revenueType).setEventProperties(props);

        rakam.logRevenue(revenue);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), "_revenue");

        JSONObject obj = event.optJSONObject("properties");
        assertEquals(obj.optDouble(Constants.REVENUE_PRICE), price, 0);
        assertEquals(obj.optInt(Constants.REVENUE_QUANTITY), 15);
        assertEquals(obj.optString(Constants.REVENUE_PRODUCT_ID), productId);
        assertEquals(obj.optString(Constants.REVENUE_RECEIPT), receipt);
        assertEquals(obj.optString(Constants.REVENUE_RECEIPT_SIG), receiptSig);
        assertEquals(obj.optString(Constants.REVENUE_REVENUE_TYPE), revenueType);
    }

    @Test
    public void testLogEventSync() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();

        rakam.logEventSync("test_event_sync", null);

        // Event should be in the database synchronously.
        JSONObject event = getLastEvent();
        assertEquals("test_event_sync", event.optString("collection"));

        looper.runToEndOfTasks();

        server.enqueue(new MockResponse().setBody("1"));
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
    @Ignore
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
        assertEquals(events.optJSONObject(0).optString("collection"), "test");
        assertEquals(events.optJSONObject(1).optString("collection"), "test");

        // upload limit persists until event count below threshold
        server.enqueue(new MockResponse().setBody("1"));
        looper.runToEndOfTasks(); // retry uploading after removing large event
        httpLooper.runToEndOfTasks(); // send success --> 1 event sent
        looper.runToEndOfTasks(); // event count below threshold --> disable backoff
        looper.runToEndOfTasks();
        assertEquals(1, getUnsentEventCount());

        // verify backoff disabled - queue 2 more events, see that all get uploaded
        rakam.logEvent("test");
        rakam.logEvent("test");
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);
        server.enqueue(new MockResponse().setBody("1"));
        httpLooper.runToEndOfTasks();
        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
    }

    @Test
    public void testUploadRemainingEvents() {
        long[] timestamps = {1, 2, 3, 4, 5, 6, 7};
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
            assertEquals(dbHelper.getTotalEventCount(), i + 1);
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
        ShadowLooper httpLooper = (ShadowLooper) ShadowExtractor.extract(rakam.httpThread.getLooper());
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
    public void testLimitTrackingEnabled() {
        rakam.logEvent("test");
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
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
    public void testTruncateEvent() throws JSONException {
        String longString = generateStringWithLength(Constants.MAX_STRING_LENGTH * 2, 'c');
        String truncString = generateStringWithLength(Constants.MAX_STRING_LENGTH, 'c');

        long[] timestamps = {1, 1, 2, 3};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        rakam.logEvent("test", new JSONObject().put("long_string", longString));

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        RecordedRequest request = runRequest(rakam);
        JSONArray events = getEventsFromRequest(request);

        assertEquals(events.optJSONObject(0).optString("collection"), "test");
        assertEquals(
                events.optJSONObject(0).optJSONObject("properties").getString("long_string"),
                truncString);
    }

    @Test
    public void testTruncateIdentify() throws JSONException {
        String longString = generateStringWithLength(Constants.MAX_STRING_LENGTH * 2, 'c');
        String truncString = generateStringWithLength(Constants.MAX_STRING_LENGTH, 'c');

        long[] timestamps = {1, 1, 2, 3};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        looper.runToEndOfTasks();
        rakam.identify(new Identify().set("long_string", longString));

        looper.runToEndOfTasks();
        looper.runToEndOfTasks();
        RecordedRequest request = runRequest(rakam);
        JSONObject events = getUserPropertiesFromRequest(request).optJSONObject(0);

        assertEquals(
                events.getJSONObject(Constants.OP_SET).getString("long_string"),
                truncString);
    }

    @Test
    public void testSetOffline() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        rakam.setOffline(true);

        rakam.logEvent("test1");
        rakam.logEvent("test2");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2);
        assertEquals(getUnsentIdentifyCount(), 0);

        rakam.setOffline(false);
        looper.runToEndOfTasks();
        RecordedRequest request1 = runRequest(rakam);
        JSONArray events1 = getEventsFromRequest(request1);
        looper.runToEndOfTasks();

        assertEquals(events1.length(), 2);
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 0);
    }

    @Test
    public void testSetOfflineTruncate() throws JSONException {
        long[] timestamps = {1, 2, 3, 4, 5, 6, 7, 8, 9};
        clock.setTimestamps(timestamps);
        Robolectric.getForegroundThreadScheduler().advanceTo(1);

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        int eventMaxCount = 3;
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        rakam.setEventMaxCount(eventMaxCount).setOffline(true);

        rakam.logEvent("test1");
        rakam.logEvent("test2");
        rakam.logEvent("test3");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);

        rakam.logEvent("test4");
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), eventMaxCount);

        List<JSONObject> events = dbHelper.getEvents(-1, -1);
        assertEquals(events.size(), eventMaxCount);
        assertEquals(events.get(0).optString("collection"), "test2");
        assertEquals(events.get(1).optString("collection"), "test3");
        assertEquals(events.get(2).optString("collection"), "test4");
    }

    @Test
    public void testTruncateEventsQueues() {
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
        assertEquals(getUnsentEventCount(), eventMaxCount - (eventMaxCount / 10) + 1);
    }

    @Test
    public void testTruncateEventsQueuesWithOneEvent() {
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
        assertEquals(event.optString("collection"), "test2");
    }

    @Test
    public void testClearUserProperties() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        rakam.clearUserProperties();
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 0);
        assertEquals(getUnsentIdentifyCount(), 1);
        JSONObject userPropertiesOperations = getLastUnsentIdentify();

        assertTrue(userPropertiesOperations.has(Constants.OP_CLEAR_ALL));

        assertEquals(
                1, userPropertiesOperations.optInt(Constants.OP_CLEAR_ALL)
        );
    }

    @Test
    public void testSuperProperties() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        JSONObject obj = new JSONObject().put("test", 1).put("test1", 2);
        rakam.setSuperProperties(obj);
        looper.runToEndOfTasks();

        DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper(context);
        assertTrue(compareJSONObjects(new JSONObject(dbHelper.getValue(SUPER_PROPERTIES_KEY)), obj));

        assertTrue(compareJSONObjects(rakam.getSuperProperties(), obj));
    }

    @Test
    public void testClearSuperProperties() throws JSONException {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());

        JSONObject obj = new JSONObject().put("test", 1).put("test1", 2);
        rakam.setSuperProperties(obj);
        looper.runToEndOfTasks();

        rakam.clearSuperProperties();

        assertTrue(compareJSONObjects(rakam.getSuperProperties(), null));
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

        assertEquals(events.getJSONObject(0).optString("collection"), "testEvent1");
        assertEquals(events.getJSONObject(1).optString("collection"), "testEvent2");
    }
}
