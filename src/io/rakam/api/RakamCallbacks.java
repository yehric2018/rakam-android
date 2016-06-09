package io.rakam.api;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

class RakamCallbacks implements Application.ActivityLifecycleCallbacks {

    public static final String TAG = "RakamCallbacks";
    private static final String NULL_MESSAGE = "Need to initialize RakamCallbacks with RakamClient instance";

    private RakamClient clientInstance = null;
    private static RakamLog logger = RakamLog.getLogger();

    public RakamCallbacks(RakamClient clientInstance) {
        if (clientInstance == null) {
            logger.e(TAG, NULL_MESSAGE);
            return;
        }

        this.clientInstance = clientInstance;
        clientInstance.useForegroundTracking();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {
        if (clientInstance == null) {
            logger.e(TAG, NULL_MESSAGE);
            return;
        }

        clientInstance.onExitForeground(getCurrentTimeMillis());
    }

    @Override
    public void onActivityResumed(Activity activity) {
        if (clientInstance == null) {
            logger.e(TAG, NULL_MESSAGE);
            return;
        }

        clientInstance.onEnterForeground(getCurrentTimeMillis());
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outstate) {}

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    protected long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }
}
