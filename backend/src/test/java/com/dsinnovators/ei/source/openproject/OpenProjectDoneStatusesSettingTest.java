package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class OpenProjectDoneStatusesSettingTest extends DatabaseTest {

    @Autowired
    private SettingService settings;

    @ParameterizedTest
    @ValueSource(strings = {"2026-Q2", "2026-Q3", "2026-Q4"})
    void doneStatusesAreTheFiveOfDecision67For2026Q3(String quarter) {
        assertEquals(
                List.of("tested", "deployed", "resolved", "released", "closed"),
                settings.getTextList("openproject.done_statuses", Quarter.parse(quarter)));
    }
}
