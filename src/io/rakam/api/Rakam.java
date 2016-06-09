package io.rakam.api;

import android.content.Context;

import org.json.JSONObject;


/**
 * <h1>Rakam</h1>
 * This is the main Rakam class that manages SDK instances.
 *
 * @see RakamClient RakamClient
 */
public class Rakam {

    /**
     * Gets the default instance. This is the only method you should be calling on the
     * Rakam class.
     *
     * @return the default instance
     */
    public static RakamClient getInstance() {
        return RakamClient.getInstance();
    }
}
