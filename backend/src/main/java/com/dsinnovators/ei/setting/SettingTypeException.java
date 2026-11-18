package com.dsinnovators.ei.setting;

import java.io.Serial;

/** The value of the setting is of another kind than the reader asked for. */
public class SettingTypeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String key;

    public SettingTypeException(String key, String expected, String actualJson) {
        super("Setting '" + key + "' is not " + expected + ": " + actualJson);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
