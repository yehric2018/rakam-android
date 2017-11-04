package io.rakam.unity.plugins;

import android.app.Application;
import android.content.Context;

import io.rakam.api.Rakam;
import io.rakam.api.Identify;
import io.rakam.api.Revenue;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;

public class RakamPlugin {

    public static JSONObject ToJSONObject(String jsonString) {
        JSONObject properties = null;
        try {
            properties = new JSONObject(jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return properties;
    }

    public static void init(Context context, String apiKey, String userId) {
        URL apiUrl;
        try {
            apiUrl = new URL("https://app.rakam.io");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        Rakam.getInstance().initialize(context, apiUrl, apiKey, userId);
    }

    public static void enableForegroundTracking(Application app) {
        Rakam.getInstance().enableForegroundTracking(app);
    }

    public static void logEvent(String event) {
        Rakam.getInstance().logEvent(event);
    }

    public static void logEvent(String event, String jsonProperties) {
        Rakam.getInstance().logEvent(event, ToJSONObject(jsonProperties));
    }

    public static void logEvent(String event, String jsonProperties, boolean outOfSession) {
        Rakam.getInstance().logEvent(event, ToJSONObject(jsonProperties), outOfSession);
    }

    public static void setUserId(String userId) {
        Rakam.getInstance().setUserId(userId);
    }

    public static void setOptOut(boolean enabled) {
        Rakam.getInstance().setOptOut(enabled);
    }

    public static void setUserProperties(String jsonProperties) {
        Rakam.getInstance().setUserProperties(ToJSONObject(jsonProperties));
    }

    public static void logRevenue(Revenue revenue) {
        Rakam.getInstance().logRevenue(revenue);
    }

    public static String getDeviceId() {
        return Rakam.getInstance().getDeviceId();
    }

    public static void trackSessionEvents(boolean enabled) {
        Rakam.getInstance().trackSessionEvents(enabled);
    }

    // User Property Operations

    // clear user properties
    public static void clearUserProperties() {
        Rakam.getInstance().clearUserProperties();
    }

    // unset user property
    public static void unsetUserProperty(String property) {
        Rakam.getInstance().identify(new Identify().unset(property));
    }

    // setOnce user property
    public static void setOnceUserProperty(String property, boolean value) {
        Rakam.getInstance().identify(new Identify().setOnce(property, value));
    }

    public static void setOnceUserProperty(String property, double value) {
        Rakam.getInstance().identify(new Identify().setOnce(property, value));
    }

    public static void setOnceUserProperty(String property, float value) {
        Rakam.getInstance().identify(new Identify().setOnce(property, value));
    }

    public static void setOnceUserProperty(String property, int value) {
        Rakam.getInstance().identify(new Identify().setOnce(property, value));
    }

    public static void setOnceUserProperty(String property, long value) {
        Rakam.getInstance().identify(new Identify().setOnce(property, value));
    }

    public static void setOnceUserProperty(String property, String value) {
        Rakam.getInstance().identify(new Identify().setOnce(property, value));
    }

    public static void setOnceUserPropertyDict(String property, String values) {
        Rakam.getInstance().identify(new Identify().setOnce(property, ToJSONObject(values)));
    }

    public static void setOnceUserPropertyList(String property, String values) {
        JSONObject properties = ToJSONObject(values);
        if (properties == null) {
            return;
        }
        Rakam.getInstance().identify(new Identify().setOnce(
            property, properties.optJSONArray("list")
        ));
    }

    public static void setOnceUserProperty(String property, boolean[] values) {
        Rakam.getInstance().identify(new Identify().setOnce(property, values));
    }

    public static void setOnceUserProperty(String property, double[] values) {
        Rakam.getInstance().identify(new Identify().setOnce(property, values));
    }

    public static void setOnceUserProperty(String property, float[] values) {
        Rakam.getInstance().identify(new Identify().setOnce(property, values));
    }

    public static void setOnceUserProperty(String property, int[] values) {
        Rakam.getInstance().identify(new Identify().setOnce(property, values));
    }

    public static void setOnceUserProperty(String property, long[] values) {
        Rakam.getInstance().identify(new Identify().setOnce(property, values));
    }

    public static void setOnceUserProperty(String property, String[] values) {
        Rakam.getInstance().identify(new Identify().setOnce(property, values));
    }

    // set user property
    public static void setUserProperty(String property, boolean value) {
        Rakam.getInstance().identify(new Identify().set(property, value));
    }

    public static void setUserProperty(String property, double value) {
        Rakam.getInstance().identify(new Identify().set(property, value));
    }

    public static void setUserProperty(String property, float value) {
        Rakam.getInstance().identify(new Identify().set(property, value));
    }

    public static void setUserProperty(String property, int value) {
        Rakam.getInstance().identify(new Identify().set(property, value));
    }

    public static void setUserProperty(String property, long value) {
        Rakam.getInstance().identify(new Identify().set(property, value));
    }

    public static void setUserProperty(String property, String value) {
        Rakam.getInstance().identify(new Identify().set(property, value));
    }

    public static void setUserPropertyDict(String property, String values) {
        Rakam.getInstance().identify(new Identify().set(property, ToJSONObject(values)));
    }

    public static void setUserPropertyList(String property, String values) {
        JSONObject properties = ToJSONObject(values);
        if (properties == null) {
            return;
        }
        Rakam.getInstance().identify(new Identify().set(
                property, properties.optJSONArray("list")
        ));
    }

    public static void setUserProperty(String property, boolean[] values) {
        Rakam.getInstance().identify(new Identify().set(property, values));
    }

    public static void setUserProperty(String property, double[] values) {
        Rakam.getInstance().identify(new Identify().set(property, values));
    }

    public static void setUserProperty(String property, float[] values) {
        Rakam.getInstance().identify(new Identify().set(property, values));
    }

    public static void setUserProperty(String property, int[] values) {
        Rakam.getInstance().identify(new Identify().set(property, values));
    }

    public static void setUserProperty(String property, long[] values) {
        Rakam.getInstance().identify(new Identify().set(property, values));
    }

    public static void setUserProperty(String property, String[] values) {
        Rakam.getInstance().identify(new Identify().set(property, values));
    }

    // add
    public static void addUserProperty(String property, double value) {
        Rakam.getInstance().identify(new Identify().add(property, value));
    }

    public static void addUserProperty(String property, float value) {
        Rakam.getInstance().identify(new Identify().add(property, value));
    }

    public static void addUserProperty(String property, int value) {
        Rakam.getInstance().identify(new Identify().add(property, value));
    }

    public static void addUserProperty(String property, long value) {
        Rakam.getInstance().identify(new Identify().add(property, value));
    }

    public static void addUserProperty(String property, String value) {
        Rakam.getInstance().identify(new Identify().add(property, value));
    }

    public static void addUserPropertyDict(String property, String values) {
        Rakam.getInstance().identify(new Identify().add(property, ToJSONObject(values)));
    }

//    // append user property
//    public static void appendUserProperty(String property, boolean value) {
//        Rakam.getInstance().identify(new Identify().append(property, value));
//    }
//
//    public static void appendUserProperty(String property, double value) {
//        Rakam.getInstance().identify(new Identify().append(property, value));
//    }
//
//    public static void appendUserProperty(String property, float value) {
//        Rakam.getInstance().identify(new Identify().append(property, value));
//    }
//
//    public static void appendUserProperty(String property, int value) {
//        Rakam.getInstance().identify(new Identify().append(property, value));
//    }
//
//    public static void appendUserProperty(String property, long value) {
//        Rakam.getInstance().identify(new Identify().append(property, value));
//    }
//
//    public static void appendUserProperty(String property, String value) {
//        Rakam.getInstance().identify(new Identify().append(property, value));
//    }
//
//    public static void appendUserPropertyDict(String property, String values) {
//        Rakam.getInstance().identify(new Identify().append(property, ToJSONObject(values)));
//    }
//
//    public static void appendUserPropertyList(String property, String values) {
//        JSONObject properties = ToJSONObject(values);
//        if (properties == null) {
//            return;
//        }
//        Rakam.getInstance().identify(new Identify().append(
//                property, properties.optJSONArray("list")
//        ));
//    }
//
//    public static void appendUserProperty(String property, boolean[] values) {
//        Rakam.getInstance().identify(new Identify().append(property, values));
//    }
//
//    public static void appendUserProperty(String property, double[] values) {
//        Rakam.getInstance().identify(new Identify().append(property, values));
//    }
//
//    public static void appendUserProperty(String property, float[] values) {
//        Rakam.getInstance().identify(new Identify().append(property, values));
//    }
//
//    public static void appendUserProperty(String property, int[] values) {
//        Rakam.getInstance().identify(new Identify().append(property, values));
//    }
//
//    public static void appendUserProperty(String property, long[] values) {
//        Rakam.getInstance().identify(new Identify().append(property, values));
//    }
//
//    public static void appendUserProperty(String property, String[] values) {
//        Rakam.getInstance().identify(new Identify().append(property, values));
//    }
}
