package com.dsinnovators.ei.setting;

import com.dsinnovators.ei.common.Quarter;
import java.io.Serial;

/** No value of the setting is in force in the quarter. */
public class SettingNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String key;
    private final Quarter quarter;

    public SettingNotFoundException(String key, Quarter quarter) {
        super("No setting '" + key + "' is in force in " + quarter);
        this.key = key;
        this.quarter = quarter;
    }

    public String getKey() {
        return key;
    }

    public Quarter getQuarter() {
        return quarter;
    }
}
