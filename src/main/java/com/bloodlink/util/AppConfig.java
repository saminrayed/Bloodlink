package com.bloodlink.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppConfig {
    private static final Properties PROPERTIES = new Properties();
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([A-Z0-9_]+)(?::([^}]*))?}");

    static {
        try (InputStream input = AppConfig.class.getResourceAsStream("/com/bloodlink/config/application.properties")) {
            if (input == null) throw new IllegalStateException("Missing application.properties");
            PROPERTIES.load(input);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private AppConfig() { }

    public static String get(String key) {
        String raw = PROPERTIES.getProperty(key);
        if (raw == null) throw new IllegalArgumentException("Missing configuration key: " + key);
        Matcher matcher = ENV_PATTERN.matcher(raw);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String environmentValue = System.getenv(matcher.group(1));
            String replacement = environmentValue != null ? environmentValue : (matcher.group(2) == null ? "" : matcher.group(2));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static boolean getBoolean(String key) { return Boolean.parseBoolean(get(key)); }
    public static int getInt(String key) { return Integer.parseInt(get(key)); }
}
