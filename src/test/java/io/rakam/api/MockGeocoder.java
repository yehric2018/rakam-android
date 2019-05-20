package io.rakam.api;

import android.location.Geocoder;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

// mock for static Geocoder method
@Implements(Geocoder.class)
public class MockGeocoder {

    @Implementation
    public static boolean isPresent() {
        return true;
    }
}
