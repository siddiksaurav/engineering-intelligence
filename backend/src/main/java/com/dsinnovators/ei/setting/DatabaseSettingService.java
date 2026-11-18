package com.dsinnovators.ei.setting;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@Transactional(readOnly = true)
class DatabaseSettingService implements SettingService {

    private final SettingRepository settings;

    DatabaseSettingService(SettingRepository settings) {
        this.settings = settings;
    }

    @Override
    public BigDecimal getDecimal(String key, Quarter quarter) {
        JsonNode value = getJson(key, quarter);
        requireKind(value.isNumber(), key, "a number", value);
        return value.decimalValue();
    }

    @Override
    public int getInt(String key, Quarter quarter) {
        JsonNode value = getJson(key, quarter);
        requireKind(value.isIntegralNumber() && value.canConvertToInt(), key, "a whole number that fits an int", value);
        return value.intValue();
    }

    @Override
    public String getText(String key, Quarter quarter) {
        JsonNode value = getJson(key, quarter);
        requireKind(value.isString(), key, "a text", value);
        return value.stringValue();
    }

    @Override
    public List<String> getTextList(String key, Quarter quarter) {
        JsonNode value = getJson(key, quarter);
        requireKind(value.isArray() && value.valueStream().allMatch(JsonNode::isString), key, "a list of texts", value);
        return value.valueStream().map(JsonNode::stringValue).toList();
    }

    @Override
    public JsonNode getJson(String key, Quarter quarter) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(quarter, "quarter");
        return settings.findInForce(key, quarter.toString())
                .map(setting -> CanonicalJson.parse(setting.getSettingValue()))
                .orElseThrow(() -> new SettingNotFoundException(key, quarter));
    }

    private static void requireKind(boolean isOfKind, String key, String expected, JsonNode actual) {
        if (!isOfKind) {
            throw new SettingTypeException(key, expected, actual.toString());
        }
    }
}
