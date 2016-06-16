package io.rakam.api;

public class Constants {

    public static final String LIBRARY = "rakam-android";
    public static final String PLATFORM = "Android";
    public static final String VERSION = "2.7.4";

    public static final String DEFAULT_EVENT_LOG_URL = "https://app.rakam.io/";

    public static final String EVENT_BATCH_ENDPOINT = "event/batch";
    public static final String USER_SET_PROPERTIES_ENDPOINT = "user/batch";

    public static final String PACKAGE_NAME = "io.rakam.api";
    public static final int API_VERSION = 2;

    public static final String DATABASE_NAME = PACKAGE_NAME;
    public static final int DATABASE_VERSION = 3;

    public static final int EVENT_UPLOAD_THRESHOLD = 30;
    public static final int EVENT_UPLOAD_MAX_BATCH_SIZE = 100;
    public static final int EVENT_MAX_COUNT = 1000;
    public static final int EVENT_REMOVE_BATCH_SIZE = 20;
    public static final long EVENT_UPLOAD_PERIOD_MILLIS = 30 * 1000; // 30s
    public static final long MIN_TIME_BETWEEN_SESSIONS_MILLIS = 5 * 60 * 1000; // 5m
    public static final long SESSION_TIMEOUT_MILLIS = 30 * 60 * 1000; // 30m
    public static final int MAX_STRING_LENGTH = 1024;

    public static final String PREFKEY_LAST_EVENT_ID = PACKAGE_NAME + ".lastEventId";
    public static final String PREFKEY_LAST_EVENT_TIME = PACKAGE_NAME + ".lastEventTime";
    public static final String PREFKEY_LAST_IDENTIFY_ID = PACKAGE_NAME + ".lastIdentifyId";
    public static final String PREFKEY_PREVIOUS_SESSION_ID = PACKAGE_NAME + ".previousSessionId";
    public static final String PREFKEY_DEVICE_ID = PACKAGE_NAME + ".deviceId";
    public static final String PREFKEY_USER_ID = PACKAGE_NAME + ".userId";
    public static final String PREFKEY_OPT_OUT = PACKAGE_NAME + ".optOut";

    public static final String OP_INCREMENT = "increment_properties";
    public static final String OP_APPEND = "append_item_to_property";
    public static final String OP_CLEAR_ALL = "clear_all_properties";
    public static final String OP_SET = "set_properties";
    public static final String OP_SET_ONCE = "set_properties_once";
    public static final String OP_UNSET = "unset_properties";

    public static final String REVENUE_EVENT = "_revenue";
    public static final String REVENUE_PRODUCT_ID = "_product_id";
    public static final String REVENUE_QUANTITY = "_quantity";
    public static final String REVENUE_PRICE = "_price";
    public static final String REVENUE_REVENUE_TYPE = "_revenue_type";
    public static final String REVENUE_RECEIPT = "_receipt";
    public static final String REVENUE_RECEIPT_SIG = "_receipt_sig";
}
