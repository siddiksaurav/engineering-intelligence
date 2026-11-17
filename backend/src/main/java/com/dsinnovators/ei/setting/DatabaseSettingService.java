package com.dsinnovators.ei.setting;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
        if (!value.isNumber()) {
            throw new SettingTypeException(key, "a number", value.toString());
        }
        return value.decimalValue();
    }

    @Override
    public int getInt(String key, Quarter quarter) {
        JsonNode value = getJson(key, quarter);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new SettingTypeException(key, "a whole number that fits an int", value.toString());
        }
        return value.intValue();
    }

    @Override
    public String getText(String key, Quarter quarter) {
        JsonNode value = getJson(key, quarter);
        if (!value.isString()) {
            throw new SettingTypeException(key, "a text", value.toString());
        }
        return value.stringValue();
    }

    @Override
    public List<String> getTextList(String key, Quarter quarter) {
        JsonNode value = getJson(key, quarter);
        if (!value.isArray()) {
            throw new SettingTypeException(key, "a list of texts", value.toString());
        }
        List<String> texts = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isString()) {
                throw new SettingTypeException(key, "a list of texts", value.toString());
            }
            texts.add(element.stringValue());
        }
        return List.copyOf(texts);
    }

    @Override
    public JsonNode getJson(String key, Quarter quarter) {
        return settings
                .findFirstBySettingKeyAndValidFromQuarterLessThanEqualOrderByValidFromQuarterDesc(
                        key, quarter.toString())
                .map(setting -> CanonicalJson.parse(setting.getSettingValue()))
                .orElseThrow(() -> new SettingNotFoundException(key, quarter));
    }
}
