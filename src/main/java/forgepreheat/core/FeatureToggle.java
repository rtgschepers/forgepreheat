package forgepreheat.core;

/** Reads -Dforgepreheat.&lt;key&gt;=true|false JVM arguments so each feature can be switched for A/B runs. */
public final class FeatureToggle {
    private static final String PREFIX = "forgepreheat.";

    private FeatureToggle() {}

    public static boolean isEnabled(String key, boolean byDefault) {
        String value = System.getProperty(property(key));
        return value == null ? byDefault : Boolean.parseBoolean(value.trim());
    }

    public static String property(String key) {
        return PREFIX + key;
    }
}
