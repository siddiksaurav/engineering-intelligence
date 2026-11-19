package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubBotLoginsSettingTest extends DatabaseTest {

    @Autowired
    private SettingService settings;

    @ParameterizedTest
    @ValueSource(strings = {"2026-Q2", "2026-Q3", "2026-Q4"})
    void theBotLoginsAreReadableForTheQuartersOfTheWindow(String quarter) {
        assertEquals(
                List.of(
                        "dependabot",
                        "openprojectci",
                        "copilot-pull-request-reviewer",
                        "github-actions",
                        "github-advanced-security"),
                settings.getTextList("github.bot_logins", Quarter.parse(quarter)));
    }
}
