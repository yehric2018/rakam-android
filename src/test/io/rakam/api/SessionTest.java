package io.rakam.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SessionTest extends BaseTest {

    // allows for control of System.currentTimeMillis
    private class RakamCallbacksWithTime extends RakamCallbacks {

        private int index;
        private long [] timestamps = null;

        public RakamCallbacksWithTime(RakamClient client, long [] timestamps) {
            super(client);
            this.index = 0;
            this.timestamps = timestamps;
        }

        @Override
        protected long getCurrentTimeMillis() {
            return timestamps[index++ % timestamps.length];
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(true);
        rakam.initialize(context, apiKey);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testDefaultStartSession() {
        long timestamp = System.currentTimeMillis();
        rakam.logEventAsync("test", null, timestamp, false);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

        // trackSessionEvents is false, no start_session event added
        assertEquals(getUnsentEventCount(), 1);
        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), "test");
        assertEquals(event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));
    }

    @Test
    public void testDefaultTriggerNewSession() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long sessionTimeoutMillis = 5 * 1000; //5s
        rakam.setSessionTimeoutMillis(sessionTimeoutMillis);

        // log 1st event, initialize first session
        long timestamp1 = System.currentTimeMillis();
        rakam.logEventAsync("test1", null, timestamp1, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        // log 2nd event past timeout, verify new session started
        long timestamp2 = timestamp1 + sessionTimeoutMillis;
        rakam.logEventAsync("test2", null, timestamp2, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2);

        JSONArray events = getUnsentEvents(2);
        JSONObject event1 = events.optJSONObject(0);
        JSONObject event2 = events.optJSONObject(1);

        assertEquals(event1.optString("collection"), "test1");
        assertEquals(event1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event2.optString("collection"), "test2");
        assertEquals(event2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp2));

        // also test getSessionId
        assertEquals(rakam.getSessionId(), timestamp2);
    }

    @Test
    public void testDefaultExtendSession() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long sessionTimeoutMillis = 5 * 1000; //5s
        rakam.setSessionTimeoutMillis(sessionTimeoutMillis);

        // log 3 events all just within session expiration window, verify all in same session
        long timestamp1 = System.currentTimeMillis();
        rakam.logEventAsync("test1", null, timestamp1, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        long timestamp2 = timestamp1 + sessionTimeoutMillis - 1;
        rakam.logEventAsync("test2", null, timestamp2, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2);

        long timestamp3 = timestamp2 + sessionTimeoutMillis - 1;
        rakam.logEventAsync("test3", null, timestamp3, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);

        JSONArray events = getUnsentEvents(3);
        JSONObject event1 = events.optJSONObject(0);
        JSONObject event2 = events.optJSONObject(1);
        JSONObject event3 = events.optJSONObject(2);

        assertEquals(event1.optString("collection"), "test1");
        assertEquals(event1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event1.optJSONObject("properties").optString("_time"), String.valueOf(timestamp1));

        assertEquals(event2.optString("collection"), "test2");
        assertEquals(event2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event2.optJSONObject("properties").optString("_time"), String.valueOf(timestamp2));

        assertEquals(event3.optString("collection"), "test3");
        assertEquals(event3.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event3.optJSONObject("properties").optString("_time"), String.valueOf(timestamp3));
    }

    @Test
    public void testDefaultStartSessionWithTracking() {
        rakam.trackSessionEvents(true);

        long timestamp = System.currentTimeMillis();
        rakam.logEventAsync("test", null, timestamp, false);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();

        // trackSessions is true, start_session event is added
        assertEquals(getUnsentEventCount(), 2);
        JSONArray events = getUnsentEvents(2);
        JSONObject session_event = events.optJSONObject(0);
        JSONObject test_event = events.optJSONObject(1);

        assertEquals(session_event.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(session_event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));

        assertEquals(test_event.optString("collection"), "test");
        assertEquals(test_event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));
    }

    @Test
    public void testDefaultStartSessionWithTrackingSynchronous() {
        rakam.trackSessionEvents(true);

        long timestamp = System.currentTimeMillis();
        rakam.logEvent("test", null, timestamp, false);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        // trackSessions is true, start_session event is added
        assertEquals(getUnsentEventCount(), 2);

        // verify order of synchronous events
        JSONArray events = getUnsentEvents(2);
        JSONObject session_event = events.optJSONObject(0);
        JSONObject test_event = events.optJSONObject(1);

        assertEquals(session_event.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(session_event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));

        assertEquals(test_event.optString("collection"), "test");
        assertEquals(test_event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));
    }

    @Test
    public void testDefaultTriggerNewSessionWithTracking() {
        rakam.trackSessionEvents(true);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long sessionTimeoutMillis = 5 * 1000; //5s
        rakam.setSessionTimeoutMillis(sessionTimeoutMillis);

        // log 1st event, initialize first session
        long timestamp1 = System.currentTimeMillis();
        rakam.logEventAsync("test1", null, timestamp1, false);
        looper.runToEndOfTasks();
        // trackSessions is true, start_session event is added
        assertEquals(getUnsentEventCount(), 2);

        // log 2nd event past timeout, verify new session started
        long timestamp2 = timestamp1 + sessionTimeoutMillis;
        rakam.logEventAsync("test2", null, timestamp2, false);
        looper.runToEndOfTasks();
        // trackSessions is true, end_session and start_session events are added
        assertEquals(getUnsentEventCount(), 5);

        JSONArray events = getUnsentEvents(5);
        JSONObject startSession1 = events.optJSONObject(0);
        JSONObject event1 = events.optJSONObject(1);
        JSONObject endSession = events.optJSONObject(2);
        JSONObject startSession2 = events.optJSONObject(3);
        JSONObject event2 = events.optJSONObject(4);

        assertEquals(startSession1.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));

        assertEquals(event1.optString("collection"), "test1");
        assertEquals(event1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event1.optJSONObject("properties").optString("_time"), String.valueOf(timestamp1));

        assertEquals(endSession.optString("collection"), RakamClient.END_SESSION_EVENT);
        assertEquals(endSession.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));

        assertEquals(startSession2.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp2));

        assertEquals(event2.optString("collection"), "test2");
        assertEquals(event2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp2));
        assertEquals(event2.optJSONObject("properties").optString("_time"), String.valueOf(timestamp2));
    }

    @Test
    public void testDefaultTriggerNewSessionWithTrackingSynchronous() {
        rakam.trackSessionEvents(true);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long sessionTimeoutMillis = 5 * 1000; //5s
        rakam.setSessionTimeoutMillis(sessionTimeoutMillis);

        // log 1st event, initialize first session
        long timestamp1 = System.currentTimeMillis();
        rakam.logEvent("test1", null, timestamp1, false);
        looper.runToEndOfTasks();
        // trackSessions is true, start_session event is added
        assertEquals(getUnsentEventCount(), 2);

        // log 2nd event past timeout, verify new session started
        long timestamp2 = timestamp1 + sessionTimeoutMillis;
        rakam.logEvent("test2", null, timestamp2, false);
        looper.runToEndOfTasks();
        // trackSessions is true, end_session and start_session events are added
        assertEquals(getUnsentEventCount(), 5);

        // verify order of synchronous events
        JSONArray events = getUnsentEvents(5);
        JSONObject startSession1 = events.optJSONObject(0);
        JSONObject event1 = events.optJSONObject(1);
        JSONObject endSession = events.optJSONObject(2);
        JSONObject startSession2 = events.optJSONObject(3);
        JSONObject event2 = events.optJSONObject(4);

        assertEquals(startSession1.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));

        assertEquals(event1.optString("collection"), "test1");
        assertEquals(event1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event1.optJSONObject("properties").optString("_time"), String.valueOf(timestamp1));

        assertEquals(endSession.optString("collection"), RakamClient.END_SESSION_EVENT);
        assertEquals(endSession.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));

        assertEquals(startSession2.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp2));

        assertEquals(event2.optString("collection"), "test2");
        assertEquals(event2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp2));
        assertEquals(event2.optJSONObject("properties").optString("_time"), String.valueOf(timestamp2));
    }

    @Test
    public void testDefaultExtendSessionWithTracking() {
        rakam.trackSessionEvents(true);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long sessionTimeoutMillis = 5 * 1000; //5s
        rakam.setSessionTimeoutMillis(sessionTimeoutMillis);

        // log 3 events all just within session expiration window, verify all in same session
        long timestamp1 = System.currentTimeMillis();
        rakam.logEventAsync("test1", null, timestamp1, false);
        looper.runToEndOfTasks();
        // trackSessions is true, start_session event is added
        assertEquals(getUnsentEventCount(), 2);

        long timestamp2 = timestamp1 + sessionTimeoutMillis - 1;
        rakam.logEventAsync("test2", null, timestamp2, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);

        long timestamp3 = timestamp2 + sessionTimeoutMillis - 1;
        rakam.logEventAsync("test3", null, timestamp3, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 4);

        JSONArray events = getUnsentEvents(4);
        JSONObject startSession = events.optJSONObject(0);
        JSONObject event1 = events.optJSONObject(1);
        JSONObject event2 = events.optJSONObject(2);
        JSONObject event3 = events.optJSONObject(3);

        assertEquals(startSession.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));

        assertEquals(event1.optString("collection"), "test1");
        assertEquals(event1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event1.optJSONObject("properties").optString("_time"), String.valueOf(timestamp1));

        assertEquals(event2.optString("collection"), "test2");
        assertEquals(event2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event2.optJSONObject("properties").optString("_time"), String.valueOf(timestamp2));

        assertEquals(event3.optString("collection"), "test3");
        assertEquals(event3.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event3.optJSONObject("properties").optString("_time"), String.valueOf(timestamp3));
    }

    @Test
    public void testDefaultExtendSessionWithTrackingSynchronous() {
        rakam.trackSessionEvents(true);

        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long sessionTimeoutMillis = 5 * 1000; //5s
        rakam.setSessionTimeoutMillis(sessionTimeoutMillis);

        // log 3 events all just within session expiration window, verify all in same session
        long timestamp1 = System.currentTimeMillis();
        rakam.logEvent("test1", null, timestamp1, false);
        looper.runToEndOfTasks();
        // trackSessions is true, start_session event is added
        assertEquals(getUnsentEventCount(), 2);

        long timestamp2 = timestamp1 + sessionTimeoutMillis - 1;
        rakam.logEvent("test2", null,timestamp2, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);

        long timestamp3 = timestamp2 + sessionTimeoutMillis - 1;
        rakam.logEventAsync("test3", null, timestamp3, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 4);

        // verify order of synchronous events
        JSONArray events = getUnsentEvents(4);
        JSONObject startSession = events.optJSONObject(0);
        JSONObject event1 = events.optJSONObject(1);
        JSONObject event2 = events.optJSONObject(2);
        JSONObject event3 = events.optJSONObject(3);

        assertEquals(startSession.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));

        assertEquals(event1.optString("collection"), "test1");
        assertEquals(event1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event1.optJSONObject("properties").optString("_time"), String.valueOf(timestamp1));

        assertEquals(event2.optString("collection"), "test2");
        assertEquals(event2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event2.optJSONObject("properties").optString("_time"), String.valueOf(timestamp2));

        assertEquals(event3.optString("collection"), "test3");
        assertEquals(event3.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(event3.optJSONObject("properties").optString("_time"), String.valueOf(timestamp3));
    }

    @Test
    public void testEnableAccurateTracking() {
        assertFalse(rakam.isUsingForegroundTracking());
        RakamCallbacks callBacks = new RakamCallbacks(rakam);
        assertTrue(rakam.isUsingForegroundTracking());
    }

    @Test
    public void testAccurateOnResumeStartSession() {
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {timestamp};
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertFalse(rakam.isInForeground());

        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertTrue(rakam.isInForeground());
        assertEquals(rakam.getPreviousSessionId(), timestamp);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamp);
    }

    @Test
    public void testAccurateOnResumeStartSessionWithTracking() {
        rakam.trackSessionEvents(true);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {timestamp};
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertFalse(rakam.isInForeground());
        assertEquals(getUnsentEventCount(), 0);

        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertTrue(rakam.isInForeground());
        assertEquals(rakam.getPreviousSessionId(), timestamp);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamp);

        // verify that start session event sent
        assertEquals(getUnsentEventCount(), 1);
        JSONObject startSession = getLastUnsentEvent();
        assertEquals(startSession.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(
                startSession.optJSONObject("properties").optString("_session_id"),
                String.valueOf(timestamp)
        );
        assertEquals(
            startSession.optJSONObject("properties").optString("_time"),
            String.valueOf(timestamp)
        );
    }

    @Test
    public void testAccurateOnPauseRefreshTimestamp() {
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {timestamp, timestamp + minTimeBetweenSessionsMillis};
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);

        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);

        callBacks.onActivityPaused(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[1]);
        assertFalse(rakam.isInForeground());
    }

    @Test
    public void testAccurateOnPauseRefreshTimestampWithTracking() {
        rakam.trackSessionEvents(true);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        rakam.setMinTimeBetweenSessionsMillis(minTimeBetweenSessionsMillis);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {timestamp, timestamp + minTimeBetweenSessionsMillis};
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertEquals(getUnsentEventCount(), 0);

        callBacks.onActivityResumed(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);
        assertEquals(getUnsentEventCount(), 1);

        // only refresh time, no session checking
        callBacks.onActivityPaused(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[1]);
        assertEquals(getUnsentEventCount(), 1);
    }

    @Test
    public void testAccurateOnResumeTriggerNewSession() {
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        rakam.setMinTimeBetweenSessionsMillis(minTimeBetweenSessionsMillis);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {
                timestamp,
                timestamp + 1,
                timestamp + 1 + minTimeBetweenSessionsMillis
        };
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertEquals(getUnsentEventCount(), 0);

        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);
        assertEquals(getUnsentEventCount(), 0);
        assertTrue(rakam.isInForeground());

        // only refresh time, no session checking
        callBacks.onActivityPaused(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[1]);
        assertEquals(getUnsentEventCount(), 0);
        assertFalse(rakam.isInForeground());

        // resume after min session expired window, verify new session started
        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[2]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[2]);
        assertEquals(getUnsentEventCount(), 0);
        assertTrue(rakam.isInForeground());
    }

    @Test
    public void testAccurateOnResumeTriggerNewSessionWithTracking() {
        rakam.trackSessionEvents(true);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        rakam.setMinTimeBetweenSessionsMillis(minTimeBetweenSessionsMillis);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {
                timestamp,
                timestamp + 1,
                timestamp + 1 + minTimeBetweenSessionsMillis
        };
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertEquals(getUnsentEventCount(), 0);

        callBacks.onActivityResumed(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);
        assertEquals(getUnsentEventCount(), 1);
        assertTrue(rakam.isInForeground());

        // only refresh time, no session checking
        callBacks.onActivityPaused(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[1]);
        assertEquals(getUnsentEventCount(), 1);
        assertFalse(rakam.isInForeground());

        // resume after min session expired window, verify new session started
        callBacks.onActivityResumed(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[2]);
        assertEquals(rakam.getLastEventId(), 3);
        assertEquals(rakam.getLastEventTime(), timestamps[2]);
        assertEquals(getUnsentEventCount(), 3);
        assertTrue(rakam.isInForeground());

        JSONArray events = getUnsentEvents(3);
        JSONObject startSession1 = events.optJSONObject(0);
        JSONObject endSession = events.optJSONObject(1);
        JSONObject startSession2 = events.optJSONObject(2);

        assertEquals(startSession1.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamps[0]));
        assertEquals(startSession1.optJSONObject("properties").optString("_time"), String.valueOf(timestamps[0]));

        assertEquals(endSession.optString("collection"), RakamClient.END_SESSION_EVENT);
        assertEquals(endSession.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamps[0]));
        assertEquals(endSession.optJSONObject("properties").optString("_time"), String.valueOf(timestamps[1]));

        assertEquals(startSession2.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamps[2]));
        assertEquals(startSession2.optJSONObject("properties").optString("_time"), String.valueOf(timestamps[2]));
    }

    @Test
    public void testAccurateOnResumeExtendSession() {
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        rakam.setMinTimeBetweenSessionsMillis(minTimeBetweenSessionsMillis);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {
                timestamp,
                timestamp + 1,
                timestamp + 1 + minTimeBetweenSessionsMillis - 1  // just inside session exp window
        };
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);

        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);

        callBacks.onActivityPaused(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[1]);
        assertFalse(rakam.isInForeground());

        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), timestamps[2]);
        assertTrue(rakam.isInForeground());
    }

    @Test
    public void testAccurateOnResumeExtendSessionWithTracking() {
        rakam.trackSessionEvents(true);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        rakam.setMinTimeBetweenSessionsMillis(minTimeBetweenSessionsMillis);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {
                timestamp,
                timestamp + 1,
                timestamp + 1 + minTimeBetweenSessionsMillis - 1  // just inside session exp window
        };
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertEquals(getUnsentEventCount(), 0);

        callBacks.onActivityResumed(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);
        assertEquals(getUnsentEventCount(), 1);

        callBacks.onActivityPaused(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[1]);
        assertFalse(rakam.isInForeground());
        assertEquals(getUnsentEventCount(), 1);

        callBacks.onActivityResumed(null);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[2]);
        assertTrue(rakam.isInForeground());
        assertEquals(getUnsentEventCount(), 1);

        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamps[0]));
        assertEquals(event.optJSONObject("properties").optString("_time"), String.valueOf(timestamps[0]));
    }

    @Test
    public void testAccurateLogAsyncEvent() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        rakam.setMinTimeBetweenSessionsMillis(minTimeBetweenSessionsMillis);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {timestamp + minTimeBetweenSessionsMillis - 1};
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertEquals(getUnsentEventCount(), 0);
        assertFalse(rakam.isInForeground());

        // logging an event before onResume will force a session check
        rakam.logEventAsync("test", null, timestamp, false);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamp);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamp);
        assertEquals(getUnsentEventCount(), 1);

        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamp);
        assertEquals(rakam.getLastEventId(), 1);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);
        assertEquals(getUnsentEventCount(), 1);
        assertTrue(rakam.isInForeground());

        JSONObject event = getLastUnsentEvent();
        assertEquals(event.optString("collection"), "test");
        assertEquals(event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));
        assertEquals(event.optJSONObject("properties").optString("_time"), String.valueOf(timestamp));
    }

    @Test
    public void testAccurateLogAsyncEventWithTracking() {
        rakam.trackSessionEvents(true);
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long minTimeBetweenSessionsMillis = 5*1000; //5s
        rakam.setMinTimeBetweenSessionsMillis(minTimeBetweenSessionsMillis);
        long timestamp = System.currentTimeMillis();
        long [] timestamps = {timestamp + minTimeBetweenSessionsMillis};
        RakamCallbacks callBacks = new RakamCallbacksWithTime(rakam, timestamps);

        assertEquals(rakam.getPreviousSessionId(), -1);
        assertEquals(rakam.getLastEventId(), -1);
        assertEquals(rakam.getLastEventTime(), -1);
        assertEquals(getUnsentEventCount(), 0);
        assertFalse(rakam.isInForeground());

        // logging an event before onResume will force a session check
        rakam.logEventAsync("test", null, timestamp, false);
        looper.runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamp);
        assertEquals(rakam.getLastEventId(), 2);
        assertEquals(rakam.getLastEventTime(), timestamp);
        assertEquals(getUnsentEventCount(), 2);

        // onResume after session expires will start new session
        callBacks.onActivityResumed(null);
        ((ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper())).runToEndOfTasks();
        assertEquals(rakam.getPreviousSessionId(), timestamps[0]);
        assertEquals(rakam.getLastEventId(), 4);
        assertEquals(rakam.getLastEventTime(), timestamps[0]);
        assertEquals(getUnsentEventCount(), 4);
        assertTrue(rakam.isInForeground());

        JSONArray events = getUnsentEvents(4);
        JSONObject startSession1 = events.optJSONObject(0);
        JSONObject event = events.optJSONObject(1);
        JSONObject endSession = events.optJSONObject(2);
        JSONObject startSession2 = events.optJSONObject(3);

        assertEquals(startSession1.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));
        assertEquals(startSession1.optJSONObject("properties").optString("_time"), String.valueOf(timestamp));

        assertEquals(event.optString("collection"), "test");
        assertEquals(event.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));
        assertEquals(event.optJSONObject("properties").optString("_time"), String.valueOf(timestamp));

        assertEquals(endSession.optString("collection"), RakamClient.END_SESSION_EVENT);
        assertEquals(endSession.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp));
        assertEquals(endSession.optJSONObject("properties").optString("_time"), String.valueOf(timestamp));

        assertEquals(startSession2.optString("collection"), RakamClient.START_SESSION_EVENT);
        assertEquals(startSession2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamps[0]));
        assertEquals(startSession2.optJSONObject("properties").optString("_time"), String.valueOf(timestamps[0]));
    }


    @Test
    public void testLogOutOfSessionEvent() {
        ShadowLooper looper = (ShadowLooper) ShadowExtractor.extract(rakam.logThread.getLooper());
        long sessionTimeoutMillis = 5*1000; //1s
        rakam.setSessionTimeoutMillis(sessionTimeoutMillis);

        long timestamp1 = System.currentTimeMillis();
        rakam.logEventAsync("test1", null, timestamp1, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 1);

        // log out of session event just within session expiration window
        long timestamp2 = timestamp1 + sessionTimeoutMillis - 1;
        rakam.logEventAsync("outOfSession", null, timestamp2, true);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 2);

        // out of session events do not extend session, 2nd event will start new session
        long timestamp3 = timestamp1 + sessionTimeoutMillis;
        rakam.logEventAsync("test2", null, timestamp3, false);
        looper.runToEndOfTasks();
        assertEquals(getUnsentEventCount(), 3);

        JSONArray events = getUnsentEvents(3);
        JSONObject event1 = events.optJSONObject(0);
        JSONObject outOfSessionEvent = events.optJSONObject(1);
        JSONObject event2 = events.optJSONObject(2);

        assertEquals(event1.optString("collection"), "test1");
        assertEquals(event1.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp1));
        assertEquals(outOfSessionEvent.optString("collection"), "outOfSession");
        assertEquals(outOfSessionEvent.optJSONObject("properties").optString("_session_id"), String.valueOf(-1));
        assertEquals(event2.optString("collection"), "test2");
        assertEquals(event2.optJSONObject("properties").optString("_session_id"), String.valueOf(timestamp3));
    }
}
