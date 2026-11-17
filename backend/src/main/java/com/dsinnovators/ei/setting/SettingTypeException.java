package com.dsinnovators.ei.setting;

/** The value of the setting is of another kind than the reader asked for. */
public class SettingTypeException extends RuntimeException {

    private final String key;

    public SettingTypeException(String key, String expected, String actualJson) {
        super("Setting '" + key + "' is not " + expected + ": " + actualJson);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
