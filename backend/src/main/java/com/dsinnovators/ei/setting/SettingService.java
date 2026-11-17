package com.dsinnovators.ei.setting;

import com.dsinnovators.ei.common.Quarter;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Reads a setting for a quarter. The value in force is the one with the latest
 * {@code valid_from_quarter} that is not after the asked quarter. There is no default in code and
 * no cache.
 *
 * <p>Every reader throws {@link SettingNotFoundException} when no value is in force for the key in
 * that quarter, and {@link SettingTypeException} when the value is of another kind than asked.
 */
public interface SettingService {

    BigDecimal getDecimal(String key, Quarter quarter);

    /** A whole number that fits an {@code int}. */
    int getInt(String key, Quarter quarter);

    String getText(String key, Quarter quarter);

    List<String> getTextList(String key, Quarter quarter);

    /** The value as it is stored: a number, a text, a list or an object. */
    JsonNode getJson(String key, Quarter quarter);
}
